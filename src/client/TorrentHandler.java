
 package client;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import GivenTools.*;

/**
 * TorrentHandler 
 * Managing the peers,
 * Manages communication to the tracker 
 * Keeps track of torrent data
 */
public class TorrentHandler extends Observable implements TorrentDelegate, PeerDelegate, Runnable {
	protected final TorrentInfo info;
	public Tracker tracker;
	public final String escaped_info_hash;
	protected String local_peer_id;
	private int uploaded;
	private int downloaded;
	public final int size;
	public int listenPort;
	public Writer file_writer;
	protected SessionHandler session_handler;
	public byte[][] full_byte_array;
	private long start_time;
	public boolean finish_time;

	boolean isRunning;
	boolean didStart = false;
	private BlockingDeque<Callable<Void>> runQueue;
	protected Map<String, Peer> connected_peers;
	protected Map<String, Peer> reconnecting_peers;
	protected Bitfield localBitfield;
	private Queue<PieceIndexCount> queue_piece_index;
	private Queue<Integer> queue_number;

	protected SpeedTestDotNet odometer;
	private Timer chokeTimer;
	protected String file_name;

	/*
	 * Makes the synchronized function  of uploads and downloads
	 */
	public synchronized int getUploaded() {
		int newInt = uploaded;
		return newInt;
	}
	private synchronized void incrementUploaded(int value) {
		uploaded += value;
	}
	public synchronized int getDownloaded() {
		int newInt = downloaded;
		return newInt;
	}
	private synchronized void incrementDownloaded(int value) {
		downloaded += value;
		if (downloaded == size) {
			int hours,minutes,seconds;
			long time = System.currentTimeMillis();
			System.out.println("Completed Downloads. Notifying the tracker.");
			tracker.getTrackerResponse(uploaded, downloaded, Tracker.MessageType.COMPLETED);
			finish_time = true;
			
			//Gives the timer for the download
			time -= start_time;
			time /= 1000;
			hours = (int)time/3600;
			time = time%3600;
			minutes = (int)time/60;
			time = time%60;
			seconds = (int)time;
			System.out.println("Time Elapsed: " + hours + " : " + minutes + " : " + seconds);
		}
	}

	/**
	 * create a TorrentHandler from Torrent File and Save File Name
	 * 		If there are errors return NULL
	 * @param  torrentFilename The name of the torrent file
	 * @param  saveFileName    Name of the save file name
	 * @return                 If no errors then successful Torrent Handle,
	 *                         		Otherwise return NULL
	 */
	public static TorrentHandler create(String torrentFilename, String saveFileName) {
		TorrentHandler newTorrent = null;
		try {
			TorrentInfo newInfo = new TorrentInfo(Files.readAllBytes(Paths.get(torrentFilename)));
			String newInfoHash = URLEncoder.encode(new String(newInfo.info_hash.array(), "ISO-8859-1"), "ISO-8859-1");
			newTorrent = new TorrentHandler(newInfo, newInfoHash, saveFileName);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Failed to make Torrent Download Handler");
		}
		return newTorrent;
	}

	/**
	 * TorrentHandler constructor
	 * Will throw IOException if it fails
	 * @param  info              Describes the torrent to be downloaded
	 * @param  escaped_info_hash Used to communicate with the tracker
	 * @param  peer_id           Peer id to be used by the client
	 * @param  saveFileName      Name of the file name to be saved as
	 */
	protected TorrentHandler(TorrentInfo info, String escaped_info_hash, String saveFileName) throws IOException {
		this.info = info;
		this.escaped_info_hash = escaped_info_hash;
		file_name = saveFileName;
		local_peer_id = RUBTClient.peerId;
		listenPort = RUBTClient.getListenPort();
		uploaded = 0;
		downloaded = 0;
		size = info.file_length;
		tracker = new Tracker(escaped_info_hash, info.announce_url.toString(), size);
		file_writer = new Writer(saveFileName, info.piece_length);
		connected_peers = new HashMap<>();
		reconnecting_peers = new HashMap<>();
		queue_piece_index = new ArrayDeque<>(info.piece_hashes.length);
		queue_number = new ArrayDeque<>(info.piece_hashes.length);
		start_time = System.currentTimeMillis();
		finish_time = false;
		session_handler = new SessionHandler(saveFileName, info.piece_length);
		full_byte_array = session_handler.getPrevSessionData();
		if (full_byte_array.length != info.piece_hashes.length)
			full_byte_array = new byte[info.piece_hashes.length][info.piece_length];
		localBitfield = Bitfield.decode(session_handler.loadSession(), info.piece_hashes.length);
		if (localBitfield == null) {
			System.out.println("Bitfield is NULL");
			localBitfield = new Bitfield(info.piece_hashes.length);
		}
		System.out.println("Local Bitfield: " + localBitfield);

		for (int i = 0; i < info.piece_hashes.length; i++) {
			if (localBitfield.get(i) == true)
				downloaded += getPieceSize(i);
			else
				queue_piece_index.add(new PieceIndexCount(i, Integer.MAX_VALUE));
		}

		System.out.println("Need to download piece: " + queue_piece_index);

		if (queue_piece_index.peek() == null)
			finish_time = true;

		isRunning = true;
		runQueue = new LinkedBlockingDeque<>();
		odometer = new SpeedTestDotNet();
		chokeTimer = new Timer();
	}

   /**
	 * Verify the piece against the hash in torrent file
	 * 		True if it matches, false otherwise
	 * @param  pieceMessage Message 
	 */
	
	protected boolean pieceIsCorrect(MessageData pieceMessage) {
		if (pieceMessage.type == Message.PIECE) {
			return pieceIsCorrect(pieceMessage.pieceIndex, pieceMessage.block);
		} else {
			return false;
		}
	}

	protected boolean pieceIsCorrect(int pieceIndex, byte[] block) {
		boolean isCorrect = false;
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(block, 0, block.length);
			byte[] generatedPieceHash = sha1.digest();
			byte[] torrentFilePieceHash = info.piece_hashes[pieceIndex].array();
			if (Arrays.equals(generatedPieceHash, torrentFilePieceHash)) {
				isCorrect = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return isCorrect;
	}

	/**
	 * Will only try to save, once download completed
	 */
	protected void saveTofile() {
		if (getDownloaded() == info.file_length) {
			int hours,minutes,seconds;
			long time = System.currentTimeMillis();
			time -= start_time;
			time /= 1000;
			hours = (int)time/3600;
			time = time%3600;
			minutes = (int)time/60;
			time= time%60;
			seconds = (int)time;
			System.out.println("Everything is download and writing to file.");
			System.out.println("Time Elapsed since start:"+hours+":"+minutes+":"+seconds);
			for (int i = 0; i < info.piece_hashes.length; i++) {
				if (full_byte_array[i] != null)
					file_writer.writeData(i, ByteBuffer.wrap(full_byte_array[i]));
			}
		} else
			System.err.println("Error dealing with all the pieces");
	}

	protected void saveTofile(MessageData piece) {
		file_writer.writeMessage(piece.message);
	}

	/**
	 * Get piece size from index
	 * Last piece is different, half the size
	 * 		PieceSize set to 0 if the index is outside of the count
	 * @param  pieceIndex Index of each piece
	 */
	public int getPieceSize(int pieceIndex) {
		int pieceSize;
		if (pieceIndex == info.piece_hashes.length - 1)
			pieceSize = size % info.piece_length;
		else if (pieceIndex < info.piece_hashes.length)
			pieceSize = info.piece_length;
		else
			pieceSize = 0;
		return pieceSize;
	}

	/*
	 * Disconnects peers from connected peers list
	 * 		Then attempts to reconnect peers
	 */
	protected void disconnectPeers() {
		for (Peer peer : connected_peers.values()) {
			System.out.println("Shutting down peer id: " + peer.ip);
			peer.shutdown();
		}
		connected_peers.clear();
		for (Peer peer : reconnecting_peers.values()) {
			System.out.println("Shutting down peer id: " + peer.ip);
			peer.shutdown();
		}
		reconnecting_peers.clear();
	}

	/*
	 * Request the next piece
	 */
	public synchronized void requestNextPiece(final Peer peer) {
		Set<Integer> usefulPiecesFromPeer = localBitfield.not().and(peer.getBitfield()).getSetBitIndexes();
		//If the piece was never there
		if (usefulPiecesFromPeer.size() > 0) {
			boolean pieceWasRequested = false;
			List<PieceIndexCount> failedPieces = new ArrayList<>();
			for (int i = 0; !pieceWasRequested && i < 10; i++) {
				Integer pieceToRequest = null;
				PieceIndexCount pieceObj = queue_piece_index.poll();
				
				if (pieceObj == null){
					pieceToRequest = queue_number.poll();
				}else{
					pieceToRequest = pieceObj.index;
				}
				//If the piece is requested then send message
				if (pieceObj != null && pieceToRequest != null) {
					int pieceIndex = pieceToRequest.intValue();
					int pieceSize = getPieceSize(pieceIndex);
					if (peer.getBitfield().get(pieceIndex) == true) {
						System.out.println("Sending REQUEST for piece " + pieceIndex + " to " + peer.ip);
						peer.send(new MessageData(Message.REQUEST, pieceIndex, 0, pieceSize));
						queue_number.add(pieceIndex);
						pieceWasRequested = true;
					} else if (pieceObj != null) {
						failedPieces.add(pieceObj);
					}
				}
			}
			for (PieceIndexCount triedPieceObj : failedPieces)
				failedPieces.add(triedPieceObj);
		// If the piece already exists
		} else {
			peer.send(new MessageData(Message.NOTINTERESTED));
		}
	}

	/**
	 * Get number of peers that have the piece
	 * 		Returns value for the priority queue
	 * 			otherwise Integer.MAX_VALUE if it has no peers
	 * 		Java priority heap is min heap
	 * 			Last piece have no info
	 * @param  index The piece index
	 */
	protected int getPeerCountForPiece(int index) {
		int count = 0;
		for (Peer peer : connected_peers.values()) {
			if (peer.getBitfield().get(index) == true)
				count++;
		}
		if (count == 0)
			count = Integer.MAX_VALUE;
		return count;
	}

	/**
	 * Updates the queue_piece_index priority queue when new info
	 * when given piece comes in.
	 * 		Update Priority value form getPeerCountForPiece()
	 * @param index index of the piece that needs updating
	 */
	protected synchronized void updateRarestPiece(int index) {
		PieceIndexCount piece = new PieceIndexCount(index, getPeerCountForPiece(index));
		if (queue_piece_index.contains(piece)) {
			queue_piece_index.remove(piece);
			queue_piece_index.add(piece);
		}
	}


	/*
	 * Shuts down the TorrentHandler from user input
	 */
	public void shutdown() {
		try {
			runQueue.putFirst(new Callable<Void>() {
				@Override
				public Void call() {
					chokeTimer.cancel();
					disconnectPeers();
					tracker.getTrackerResponse(getUploaded(), getDownloaded(), Tracker.MessageType.STOPPED);
					isRunning = false;
					System.out.println("Shutting Down TorrentHandler");
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Gets the status from user input
	 */
	public void status(){
		System.out.format("Downloaded: %d bytes, Unploaded: %d bytes\n", getDownloaded(), getUploaded());
	}

	//Get the info_hash
	public ByteBuffer getHash() {
		return info.info_hash;
	}
	
	//Make an incoming peer to uploading
	public void createIncomingPeer(Handshake peer_hs, Socket sock){
		Peer incPeer = Peer.peerFromHandshake(peer_hs, sock, this);
		incPeer.addObserver(odometer);

		if (incPeer != null && !incPeer.sock.isClosed() && incPeer.sock.isConnected()){
			if (!reconnecting_peers.containsKey(incPeer.peer_id)) {
				reconnecting_peers.put(incPeer.peer_id, incPeer);
				PeerRunnable.HS_StartAndReadRunnable runnable = new PeerRunnable.HS_StartAndReadRunnable(incPeer, peer_hs);
				(new Thread(runnable)).start();     
			}
		} else {
			System.err.println("Error: Socket is closed on incoming peer");
		}
	}

	//Get the File name
	public String getFilename() {
		return file_name;
	}

	//Get the download percentage
	public synchronized double getDownloadPercentage() {
		return 100.0 * (double)getDownloaded() / info.file_length;
	}


	/**
	 * "PeerDelegate" interface methods
	 */
	public void peerDidReceiveChoke(final Peer peer) { }

	public void peerDidReceiveUnChoke(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					if (didStart == false) {
						if (getDownloaded() != size)
							tracker.getTrackerResponse(getUploaded(), getDownloaded(), Tracker.MessageType.STARTED);
						else
							tracker.getTrackerResponse(getUploaded(), getDownloaded(), Tracker.MessageType.COMPLETED);
						didStart = true;
					}
					requestNextPiece(peer);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidReceiveInterested(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					peer.send(new MessageData(Message.UNCHOKE));
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidReceiveNotInterested(final Peer peer) { }

	public void peerDidReceiveHave(final Peer peer, final int pieceIndex) {
		// Updates the rarest piece
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					System.out.println("updating HAVE rarity for " + pieceIndex);
					updateRarestPiece(pieceIndex);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidReceiveBitfield(final Peer peer, final Bitfield bitfield) {
		// update rarest piece too
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					for (int i = 0; i < bitfield.numBits; i++) {
						if (bitfield.get(i) == true) {
							System.out.println("updating RECIEVE rarity for " + i);
							updateRarestPiece(i);
						}
					}
					System.out.println(queue_piece_index);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		peer.send(new MessageData(Message.INTERESTED));
	}

	public void peerDidReceiveRequest(final Peer peer, final int index, final int begin, final int length) {
		if (getLocalBitfield().get(index) == true) {
			//Can then find piece in memory
			byte[] block = new byte[length];
			System.arraycopy(full_byte_array[index], begin, block, 0, length);
			MessageData msg = new MessageData(Message.PIECE, index, begin, block);
			System.out.format("Sending PIECE index: %d, begin: %d, length: %d to peer %s\n",
				index, begin, length, peer.ip);
			peer.send(msg);
		}
	}

	public void peerDidReceivePiece(final Peer peer, final int index, final int begin, final byte[] block) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					if (pieceIsCorrect(index, block)) {
						if (localBitfield.get(index) == false) {
							saveTofile(new MessageData(Message.PIECE, index, begin, block));
							full_byte_array[index] = block;
							localBitfield.set(index);
							try {
								session_handler.writeSession(localBitfield.array);
							} catch (Exception e) {
								e.printStackTrace();
							}
							incrementDownloaded(getPieceSize(index));
							double percentDownloaded = 100.0 * (double)getDownloaded() / info.file_length;
							System.out.format("Downloaded %d out of %d (%.2f %%) (processed piece %d, size %d)\n",
								getDownloaded(), info.file_length, percentDownloaded, index, getPieceSize(index));
							setChanged();
							notifyObservers(percentDownloaded);
						}
					} else {
						queue_piece_index.add(new PieceIndexCount(index, getPeerCountForPiece(index)));
					}
					queue_number.remove(index);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		requestNextPiece(peer);
	}

	public void peerDidReceiveCancel(final Peer peer, final int index, final int begin, final int length) {
		// peer.cancel(index, begin, length);
	}

	/**
	 * Called by peer after handshake exchange checked
	 * Let TorrentHandler know successfully connected peers
	 * @param peer        Peer that checked the handshake
	 * @param peerIsLegit True if the handshake was correct
	 *                    False if handshake was incorrect
	 */
	public void peerDidHandshake(final Peer peer, final Boolean peerIsLegit) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					if (peerIsLegit) {
						connected_peers.put(peer.peer_id, peer);
						reconnecting_peers.remove(peer.peer_id);
					} else {
						peer.shutdown();
					}
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidInitiateConnection(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					for (int i = 0; i < localBitfield.numBits; i++) {
						if (localBitfield.get(i) == true) {
							System.out.println("Sending have " + i + " to peer " + peer.ip);
							peer.send(new MessageData(Message.HAVE, i));
						}
					}
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called by peer if it tried to connect but failed
	 * @param peer peer that failed to create a connection
	 */
	public void peerDidFailToConnect(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					System.err.println("Could not connect to peer " + peer.peer_id + " ( IP: " + peer.ip + " )");
					peer.shutdown();
					reconnecting_peers.remove(peer);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void peerDidDisconnect(final Peer peer) {
		try {
			runQueue.putLast(new Callable<Void>() {
				@Override
				public Void call() {
					reconnecting_peers.remove(peer);
					connected_peers.remove(peer);
					return null;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Methods for getting info
	 * the first two are also for the PeerDelegate interface
	 */

	public String getLocalPeerId() {
		return local_peer_id;
	}

	public TorrentInfo getTorrentInfo() {
		return info;
	}

	//get local bitfield
	public synchronized Bitfield getLocalBitfield() {
		return localBitfield.clone();
	}

	protected class OptimisticUnchokeTask extends TimerTask {
		@Override
		public void run() {
			try {
				runQueue.putLast(new Callable<Void>() {
					@Override
					public Void call() {
						System.out.println("OPTIMISTIC UNCHOKING task called");
						boolean slowPeerChoked = false;
						List<Map.Entry<String, Integer>> increasingDownloadPeers = odometer.poll();
						for (Map.Entry<String, Integer> peerEntry : increasingDownloadPeers) {
							if (connected_peers.containsKey(peerEntry.getKey())) {
								Peer slowPeer = connected_peers.get(peerEntry.getKey());
								if (slowPeer.getAmChoking() == false) {
									slowPeer.send(new MessageData(Message.CHOKE));
									slowPeerChoked = true;
									System.out.println("Choked peer at IP: " + slowPeer.ip);
									break;
								}
							}
						}
						if (slowPeerChoked) {
							List<Peer> unchokedAndInterestedPeers = new ArrayList<>();
							for (Peer peer : connected_peers.values()) {
								if (peer.getAmChoking() && peer.getIsInterested()) {
									unchokedAndInterestedPeers.add(peer);
								}
							}
							if (unchokedAndInterestedPeers.size() > 0) {
								int randomIndex = (new Random()).nextInt(unchokedAndInterestedPeers.size());
								Peer randomPeer = unchokedAndInterestedPeers.get(randomIndex);
								randomPeer.send(new MessageData(Message.UNCHOKE));
								System.out.println("Unchoked peer at IP: " + randomPeer.ip);
							}
						}
						return null;
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	protected OptimisticUnchokeTask optimisticUnchokeTask = new OptimisticUnchokeTask();

	/**
	 * Start torrent handler to communicate with tracker
	 * Parse through the tracker response
	 * create a connection to peers that begin with "-RU"
	 */
	@SuppressWarnings("unchecked")
	public void run() {
		Tracker.MessageType event;
		if (size == getDownloaded())
			event = Tracker.MessageType.STARTED;
		else
			event = Tracker.MessageType.UNDEFINED;
		Map<ByteBuffer, Object> decodedData = tracker.getTrackerResponse(uploaded, downloaded, event);
		ToolKit.print(decodedData);
		if (decodedData != null && !finish_time) {
			Object value = decodedData.get(Tracker.KEY_PEERS);
			ArrayList<Map<ByteBuffer, Object>> peers = (ArrayList<Map<ByteBuffer, Object>>)value;
			// ToolKit.print(peers);
			if (peers != null) {
				for (Map<ByteBuffer, Object> map_peer : peers) {
					ByteBuffer ip = (ByteBuffer)map_peer.get(Tracker.KEY_IP);
					if (ip != null) {
						Peer client = Peer.peerFromMap(map_peer, this);
						client.addObserver(odometer);
						reconnecting_peers.put(client.peer_id, client);
						client.startThreads();
					}
				}
			} else {
				System.err.println("Could not find key PEERS in decoded tracker response");
			}
		} else if (finish_time) {
			System.out.println("Seeding...");
		} else {
			System.err.println("Tracker response came back empty, please try again.");
		}

		chokeTimer.scheduleAtFixedRate(optimisticUnchokeTask, 30000, 30000);

		// Now start consuming the queue, calling each object/
		// block of code put in the queue
		Callable<Void> block = null;
		try {
			while (isRunning && (block = runQueue.takeFirst()) != null) {
				block.call();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("Closing TorrentHandler");
	}
}

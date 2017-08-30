package client;
import java.util.*;
import java.nio.*;
import java.util.concurrent.*;
import javax.swing.*;


public class RUBTClient {

	public static final String peerId = generatePeerId();
	public static final int MAX_PEERS = 10;
   
   private static int port;
	/**
	 * Generates random peer to communicate 
	 * with peers/tracker
	 */
	public static String generatePeerId() {
		char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
		Random rando = new Random();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			sb.append(chars[rando.nextInt(chars.length)]);
		}
		return sb.toString();	
	}

	public static int getListenPort(){
		return port;
	}

	final static JFrame downloadFrame = new JFrame();

	/**
	 * Main for BitTorrent client.
	 * Creates TorrentHandler object to handle download
	 * @param args command line arguments
	 *             Torrent File, Save File Name 
	 */
	public static void main(String[] args) {
		final Scanner sc = new Scanner(System.in);
		Boolean isReceivingInput = true;

		System.out.println("Peer ID: " + peerId);
		ListenServer server;
		ConcurrentMap<ByteBuffer, TorrentHandler> torrentMap;

		torrentMap = new ConcurrentHashMap<ByteBuffer, TorrentHandler>();
		server = ListenServer.create(torrentMap);
		port = server.getListenPort();
		System.out.println("Listening on port: " + port);

		Thread listener =  new Thread(server);
		listener.start();

		TorrentHandler myTorrent = null;

		if (args.length == 2) {
			myTorrent = TorrentHandler.create(args[0], args[1]);
			if (myTorrent != null) {
				torrentMap.put(myTorrent.info.info_hash, myTorrent);
				new Thread(myTorrent).start();
			} else {
				System.err.println("Can't start a torrent with" + args[0]);
				isReceivingInput = false;
				server.shutdown();
			}
		} else {
			System.err.println("Needs exactly 2 arguments: Torrent File, Save File Name");
			isReceivingInput = false;
			server.shutdown();
		}


		
		if (isReceivingInput == true) {
			downloadFrame.setSize(550, 300);
			downloadFrame.setLayout(null);
			downloadFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			downloadFrame.setTitle("Downloading File: " + args[0]);
			final JLabel fileLabel = new JLabel("Download File Name: " + args[1]);
			fileLabel.setBounds(175, 20, 300, 40);
			downloadFrame.add(fileLabel);
			final JProgressBar progressbar = new JProgressBar(0,100);
			progressbar.setVisible(true);
			progressbar.setStringPainted(false);
			progressbar.setBounds(120,75,300,40);
			final JLabel percentDownload = new JLabel(String.format("%.2f %% Completed", myTorrent.getDownloadPercentage()));
			final JLabel size = new JLabel("Size of file: " + myTorrent.size);
			final JLabel bytesDownloaded = new JLabel("Bytes Downloaded: 0");
			size.setBounds(200, 150, 300, 40);
			bytesDownloaded.setBounds(200, 120, 300, 40);
			percentDownload.setBounds(200, 75, 300, 40);
			final JLabel instructions = new JLabel("(To exit type 'quit' in command line)");
			instructions.setBounds(150, 200, 300, 40);
			downloadFrame.add(instructions);
			downloadFrame.add(size);
			downloadFrame.add(bytesDownloaded);
			downloadFrame.add(percentDownload);
			downloadFrame.add(progressbar);
			final int tsize = myTorrent.size;
			myTorrent.addObserver(new Observer() {
				@Override
				public void update(Observable o, Object arg) {
					String text = String.format("%.2f %% Completed", ((Double)arg).doubleValue());
					percentDownload.setText(text);
					progressbar.setValue(((Double)arg).intValue());
					bytesDownloaded.setText("Bytes Downloaded: "+ (int)(tsize*((Double)arg/100)));
				}
			});

			downloadFrame.setVisible(true);
		}

		//Allows user to input got status
		while (isReceivingInput && sc.hasNextLine()) {
			String input = sc.nextLine();
			if (input.equalsIgnoreCase("quit"))
				isReceivingInput = false;
			else if (input.equalsIgnoreCase("status"))
				myTorrent.status();
		}
		sc.close();
		server.shutdown();
		myTorrent.shutdown();
		System.out.println("Closing RUBTClient");
	}
}

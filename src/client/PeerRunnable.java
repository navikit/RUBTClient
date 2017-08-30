package client;

import java.util.*;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

public abstract class PeerRunnable implements Runnable {
	protected Peer peer;

	public static class StartAndReadRunnable extends PeerRunnable {
		public StartAndReadRunnable(Peer peerToManage) {
			peer = peerToManage;
			peer.readThread = this;
		}

		public void run() {
			peer.start();
			System.out.format("startReader thread %s closing\n", peer.ip);
		}

		public void peerDidHandshake(Boolean peerIsLegit) {
			if (peerIsLegit == true) {
				WriteRunnable newWriteRunnable = new WriteRunnable(peer);
				peer.writeThread = newWriteRunnable;
				(new Thread(newWriteRunnable)).start();
				peer.startReading();
			}
		}
	}

	public static class HS_StartAndReadRunnable extends StartAndReadRunnable {
		protected Handshake hs;

		public HS_StartAndReadRunnable(Peer peerToManage, Handshake peer_hs) {
			super(peerToManage);
			hs = peer_hs;
		}

		@Override
		public void run(){
			peer.start(hs);
			System.out.format("HSReader thread %s closing\n", peer.ip);
		}
	}

	public static class WriteRunnable extends PeerRunnable {
		private Boolean running;
		private Queue<MessageData> writeQueue;

		protected WriteRunnable(Peer peerToManage) {
			peer = peerToManage;
			running = false;
			writeQueue = new ArrayDeque<>();
		}

		public void run() {
			System.out.println("write thread running");
			running = true;
			try {
				while (running == true) {
					PeerEvent<? extends EventPayload> event = peer.eventQueue.poll(90, TimeUnit.SECONDS);
					if (event != null) {
						if (event.type == PeerEvent.Type.MESSAGE_TO_SEND && event.payload instanceof MessageData) {
							MessageData message = (MessageData)event.payload;
							if (message.type == Message.REQUEST)
								writeQueue.add(message);
							else
								peer.writeToSocket(message);
						}
						else if (event.type == PeerEvent.Type.SHUTDOWN)
							running = false;
					} else {
						peer.writeToSocket(new MessageData(Message.KEEPALIVE));
					}

					if (peer.getIsChoking() == false) {
						for (MessageData msg = writeQueue.poll(); msg != null; msg = writeQueue.poll()) {
							peer.writeToSocket(msg);
						}
					}
				}
			} catch (Exception e) {
				if (!(e instanceof SocketException))
					e.printStackTrace();
			}
			peer.disconnect();

			System.out.println("write thread for " + peer.ip + " closing");
		}
	}
}

package client;

public class PeerEvent<T extends EventPayload> {
	public final Type type;
	public final Peer sender;
	public T payload;

	public PeerEvent(Type type) {
		this(type, null, null);
	}

	public PeerEvent(Type type, T payload) {
		this(type, null, payload);
	}

	public PeerEvent(Type type, Peer sender) {
		this(type, sender, null);
	}

	public PeerEvent(Type type, Peer sender, T payload) {
		this.type = type;
		this.sender = sender;
		this.payload = payload;
	}

	// public T getPayload() {
	// 	return payload;
	// }

	public enum Type {
		CONNECTION_FAILED,
		HANDSHAKE_SUCCESSFUL,
		HANDSHAKE_FAILED,
	 	MESSAGE_RECEIVED,
		MESSAGE_TO_SEND,
		SHUTDOWN,
		UNCHOKED,
		PEER_INITIATED_CONNECTION,
	}
}
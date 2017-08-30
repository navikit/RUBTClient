package client;
import GivenTools.*;
/**
 * An interface for a Peer to pass events to a handler.
 */
public interface PeerDelegate {
	/**
	 * Called by peer after a handshake exchange has been completed
	 * and the peer has checked if the received handshake is correct
	 * @param peer        peer that handled this particular handshake
	 * @param peerIsLegit true if the handshake that came back is correct, false otherwise
	 */
	public void peerDidHandshake(Peer peer, Boolean peerIsLegit);

	/**
	 * Called by a peer once a message has been processed and
	 * packaged into a MessageData object for ease of use.
	 * PeerDelegate will decide what to do with the resources.
	 * @param  peer    peer that received the message
	 * @param  message message that came, packaged for encapsulation
	 * @return         true if peer should continue listening,
	 *                      false if peer should stop listening.
	 */
	// public void peerDidReceiveMessage(Peer peer, MessageData message);

	public void peerDidReceiveChoke(final Peer peer);

	public void peerDidReceiveUnChoke(final Peer peer);

	public void peerDidReceiveInterested(final Peer peer);

	public void peerDidReceiveNotInterested(final Peer peer);

	public void peerDidReceiveHave(final Peer peer, final int pieceIndex);

	public void peerDidReceiveBitfield(final Peer peer, final Bitfield bitfield);

	public void peerDidReceiveRequest(final Peer peer, final int index, final int begin, final int length);

	public void peerDidReceivePiece(final Peer peer, final int index, final int begin, final byte[] block);

	public void peerDidReceiveCancel(final Peer peer, final int index, final int begin, final int length);

	/**
	 * Called by a Peer if a socket could not be created to the
	 * peer's ip/port. PeerDelegate decides what to do in this case
	 * @param peer peer that failed to connect
	 */
	public void peerDidFailToConnect(Peer peer);

	public String getLocalPeerId();

	public TorrentInfo getTorrentInfo();

	public void peerDidInitiateConnection(Peer peer);

	public void peerDidDisconnect(final Peer peer);
}

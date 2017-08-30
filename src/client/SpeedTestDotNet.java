package client;
import java.util.*;

public class SpeedTestDotNet implements Observer {
	
	public Map<String, Integer> deltaDownloaded;
	// public HashMap<String, integer> deltaUploaded;
	// public Timer timer;

	@Override
	public void update(Observable o, Object arg) {
		if (o instanceof Peer && arg instanceof Integer)
			incrementDownload((Peer)o, ((Integer)arg).intValue());
	}

	public SpeedTestDotNet() {
		deltaDownloaded = new HashMap<>();
	}


	public synchronized List<Map.Entry<String, Integer>> poll() {
		List<Map.Entry<String, Integer>> orderedPeers = new ArrayList<>(deltaDownloaded.entrySet());
		Collections.sort(orderedPeers, new Comparator<Map.Entry<String, Integer>>() {
			@Override
			public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
				return o2.getValue() - o1.getValue();
			}
		});
		deltaDownloaded.clear();
		return orderedPeers;
	}

	public synchronized void incrementDownload(Peer peer, int bytesDownloaded) {
		if (deltaDownloaded.containsKey(peer.peer_id))
			bytesDownloaded += deltaDownloaded.get(peer.peer_id);
		deltaDownloaded.put(peer.peer_id, bytesDownloaded);
	}

	// public synchronized int increment(String peerID, int quantity, HashMap<String, int> deltaMap){
	//    return deltaMap.put(peerID, deltaMap.get(peerID) + quantity);
	// }



}

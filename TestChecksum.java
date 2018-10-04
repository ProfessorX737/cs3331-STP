import java.util.*;

public class TestChecksum {
	Map<Integer,Packet> packetMap;

	public TestChecksum() {
		packetMap = new HashMap<>();
		Packet packet = new Packet();
		packetMap.put(2, packet);
		
		Packet p = packetMap.get(2);
		//p.setAck(true);
		System.out.println(packetMap.get(2).getAck());
		
	}
	public static void main(String[] args) {
		new TestChecksum();
	}
}

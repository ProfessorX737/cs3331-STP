import java.io.*;
import java.net.*;
import java.util.*;

public class PLD {
	private DatagramSocket socket;
	private InetAddress dstAddr;
	private int dstPort;
	private Random random;

	private final float pDrop = 0.1f;
	private final float pDuplicate = 0.1f;
	private final float pCorrupt = 0.1f;
	private final float pOrder = 0.1f;
	private final int maxOrder = 4;
	private final float pDelay = 0;
	private final int maxDelay = 0;
	private final int seed = 100; 
	
	private Packet reorderedPacket;
	private int orderCount;

	public PLD(DatagramSocket socket, InetAddress dstAddr, int dstPort) {
		this.socket = socket;
		this.dstAddr = dstAddr;
		this.dstPort = dstPort;
		random = new Random(100);
		reorderedPacket = null;
		orderCount = 0;
	}
	
	// beware that this function may modify the packet input
	public void send(Packet packet) {
		
		// decide whether to drop the packet
		if(random.nextFloat() < pDrop) {
			System.out.println("dropping packet "+packet.getSeqNum());
			return;
		}
		
		// decide whether to duplicate packet
		if(random.nextFloat() < pDuplicate) {
			_send(packet);
			orderCount++;
			checkReorderSend();
			_send(packet);
			orderCount++;
			checkReorderSend();
			System.out.println("duplicating packet "+packet.getSeqNum());
			return;
		}
		
		// decide whether to corrupt packet
		if(random.nextFloat() < pCorrupt) {
			byte[] data = packet.getData();
			data[0] += 1;
			System.out.println("corrupting packet "+packet.getSeqNum());
			_send(packet);
			orderCount++;
			checkReorderSend();
			return;
		}
		
		// decide whether to send this packet out of order
		if(random.nextFloat() < pOrder) {
			// if there is no segment already waiting for reordering
			if(reorderedPacket == null) {
				System.out.println("packet "+packet.getSeqNum()+" to be reordered");
				reorderedPacket = packet;
				orderCount = 0;
			} else {
				_send(packet);
				orderCount++;
				checkReorderSend();
			}
			return;
		}
		
		_send(packet);
		orderCount++;
		checkReorderSend();
	}
	
	private void checkReorderSend() {
		if(orderCount == maxOrder && reorderedPacket != null) {
			_send(reorderedPacket);
			System.out.println("reordered packet "+reorderedPacket.getSeqNum());
			reorderedPacket = null;
			orderCount = 0;
		}
	}
	
	private void _send(Packet packet) {
		byte[] bytes = Packet.toBytes(packet);
		DatagramPacket dp = new DatagramPacket(bytes,bytes.length,dstAddr,dstPort);
		try {
			socket.send(dp);
		} catch(IOException e) {
			System.err.println("Unable to send packet");
		}
	}
}

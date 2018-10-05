import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public class PLD {
	private DatagramSocket socket;
	private InetAddress dstAddr;
	private int dstPort;
	private Random random;

	private final float pDrop;
	private final float pDuplicate;
	private final float pCorrupt;
	private final float pOrder;
	private final int maxOrder;
	private final float pDelay;
	private final int maxDelay;
	
	private Packet reorderedPacket;
	private int orderCount;
	private PacketStopWatch stopWatch;
	private Semaphore s;
	
	public PLD(DatagramSocket socket, InetAddress dstAddr, int dstPort, 
			   float pDrop, float pDuplicate, float pCorrupt, float pOrder,
			   int maxOrder, float pDelay, int maxDelay, int seed) {
		this.socket = socket;
		this.dstAddr = dstAddr;
		this.dstPort = dstPort;

		this.pDrop = pDrop;
		this.pDuplicate = pDuplicate;
		this.pCorrupt = pCorrupt;
		this.pOrder = pOrder;
		this.maxOrder = maxOrder;
		this.pDelay = pDelay;
		this.maxDelay = maxDelay;

		random = new Random(seed);
		reorderedPacket = null;
		orderCount = 0;
		stopWatch = new PacketStopWatch();
		s = new Semaphore(1);
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
			safe_send(packet);
			safe_send(packet);
			System.out.println("duplicating packet "+packet.getSeqNum());
			return;
		}
		
		// decide whether to corrupt packet
		if(random.nextFloat() < pCorrupt) {
			byte[] data = packet.getData();
			data[0] += 1;
			System.out.println("corrupting packet "+packet.getSeqNum());
			safe_send(packet);
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
				safe_send(packet);
			}
			return;
		}
		
		// decide whether to delay packet
		if(random.nextFloat() < pDelay) {
			Timer timer = new Timer();
			long delay = (long) (random.nextFloat()*maxDelay*1f);
			System.out.println("packet "+packet.getSeqNum()+" to be delayed "+delay+" ms");
			timer.schedule(new DelayedSend(timer,packet), delay);
			if(!stopWatch.isStarted()) {
				stopWatch.start(packet.getSeqNum(), packet.getAckNum());
			}
			return;
		}
		
		// if not dropped, duplicated, corrupted, re-ordered or delayed, forward it
		safe_send(packet);
	}
	
	public class DelayedSend extends TimerTask {
		private Packet packet;
		private Timer timer;
		public DelayedSend(Timer timer, Packet packet) {
			this.packet = packet;
			this.timer = timer;
		}
		public void run() {
			safe_send(packet);
			if(stopWatch.getSeqNum() == packet.getSeqNum()) {
				System.out.println("delayed packet "+packet.getSeqNum()+" is sent after "+stopWatch.getElapsedTime());
				stopWatch.reset();
			} else {
				System.out.println("delayed packet "+packet.getSeqNum()+" is sent");
			}
			timer.cancel();
		}
	}
	
	private void checkReorderSend() {
		if(orderCount == maxOrder && reorderedPacket != null) {
			_send(reorderedPacket);
			System.out.println("reordered packet "+reorderedPacket.getSeqNum());
			reorderedPacket = null;
			orderCount = 0;
		}
	}
	
	private void safe_send(Packet packet) {
		try {
			s.acquire();
			_send(packet);
			orderCount++;
			checkReorderSend();
			s.release();
		} catch(Exception e){
			e.printStackTrace();
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

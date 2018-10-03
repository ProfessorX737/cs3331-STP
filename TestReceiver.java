import java.net.*;
import java.util.*;
import java.io.*;

public class TestReceiver {
	static int myPort = 8080;
	static int dstPort = 3300;
	static int MSS = 150;
	static String host_ip = "127.0.0.1";
	
	DatagramSocket socket;
	List<Packet> packets;
	Queue<Integer> outOfOrder;
	InetAddress dstAddr;

	boolean finished = false;
	int prevSeqNum = 0;
	int nextSeqNum = 0;
	
	public TestReceiver() {
		packets = new ArrayList<>();
		outOfOrder = new PriorityQueue<>();
		
		try {
			socket = new DatagramSocket(myPort);
			dstAddr = InetAddress.getByName(host_ip);
			
			Packet testPk = new Packet();
			byte[] buffer = new byte[MSS];
			testPk.setData(buffer);
			int pkSize = Packet.toBytes(testPk).length;
			buffer = new byte[pkSize];
			DatagramPacket dp = new DatagramPacket(buffer,buffer.length);

			while(true) {
				socket.receive(dp);
				Packet packet = Packet.fromBytes(buffer);
				int seqNum = packet.getSeqNum();

				if(packet.getFin()) {
					break;
				}
				System.out.println("receiver: received " + seqNum + " dataSize: " + packet.getData().length);

				Packet ack = new Packet();
				ack.setAck(true);
				// in order packet
				if(seqNum == nextSeqNum) {
					packets.add(packet);
					nextSeqNum += packet.getData().length;
					ack.setAckNum(nextSeqNum);
					byte[] bytes = Packet.toBytes(ack);
					System.out.println("receiver: in order ack " + nextSeqNum);
					socket.send(new DatagramPacket(bytes,bytes.length,dstAddr,dstPort));
				} else {
					// send duplicate ack
					ack.setAckNum(nextSeqNum);
					byte[] bytes = Packet.toBytes(ack);
					System.out.println("receiver: sending duplicate ack " + nextSeqNum);
					socket.send(new DatagramPacket(bytes,bytes.length,dstAddr,dstPort));
					if(seqNum > nextSeqNum) {
						// gap detected
					}
				}
			}

			File file = new File("out.pdf");
			if(!file.exists()) file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			for(Packet packet : packets) {
				byte[] data = packet.getData();
				fos.write(packet.getData(), 0, data.length);
			}
			
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		new TestReceiver();
	}
	
}

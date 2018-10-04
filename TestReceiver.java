import java.net.*;
import java.util.*;
import java.io.*;

public class TestReceiver {
	static int myPort = 8080;
	static int dstPort;
	static int MSS = 150;
	static String host_ip = "127.0.0.1";

	private enum State {NONE, LISTEN, SYN_RCVD, ESTABLISHED, CLOSE_WAIT, LAST_ACK, CLOSED};
	private State state;
	
	DatagramSocket socket;
	List<Packet> inOrderPackets;
	Queue<Integer> outOfOrder;
	Map<Integer,Packet> packetMap;
	InetAddress dstAddr;

	int mySeqNum = 0;
	boolean finished = false;
	int prevSeqNum = 0;
	int nextSeqNum = 0;
	
	public TestReceiver() {
		inOrderPackets = new ArrayList<>();
		outOfOrder = new PriorityQueue<>();
		state = TestReceiver.State.LISTEN;
		
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
				
				if(state == State.LISTEN) {
					if(packet.getSyn()) {
						// extract destination ip and port
						dstAddr = dp.getAddress();
						dstPort = dp.getPort();
						nextSeqNum = packet.getSeqNum() + 1;
						// send SYNACK
						Packet synack = new Packet();
						synack.setSyn(true);
						synack.setAck(true);
						synack.setSeqNum(mySeqNum);
						synack.setAckNum(nextSeqNum);
						send(synack);
						state = State.SYN_RCVD;
						System.out.println("receiver: SYN_RCVD");
						mySeqNum++;
					}
				} else if(state == State.SYN_RCVD) {
					if(!packet.getSyn() && packet.getSeqNum() == nextSeqNum 
							&& packet.getAckNum() == mySeqNum) {
						System.out.println("receiver: ESTABLISHED");
						state = State.ESTABLISHED;
						nextSeqNum++;
					}
				} else if(state == State.ESTABLISHED) {
					if(packet.getFin() && seqNum == nextSeqNum) {
						Packet finack = new Packet();
						finack.setAck(true);
						nextSeqNum++;
						finack.setAckNum(nextSeqNum);
						finack.setSeqNum(mySeqNum);
						mySeqNum++;
						send(finack);
						state = State.CLOSE_WAIT;
						System.out.println("receiver: ClOSE_WAIT");
						Packet fin = new Packet();
						fin.setFin(true);
						fin.setSeqNum(mySeqNum);
						mySeqNum++;
						send(fin);
						state = State.LAST_ACK;
						System.out.println("receiver: LAST_ACK");
						continue;
					}
					System.out.println("receiver: received " + seqNum + " datasize: " + packet.getData().length);
					Packet ack = new Packet();
					ack.setAck(true);
					// in order packet
					if(seqNum == nextSeqNum) {
						inOrderPackets.add(packet);
						nextSeqNum += packet.getData().length;
						// if incoming packet fills a gap in data
						while(!outOfOrder.isEmpty() && outOfOrder.peek() == nextSeqNum) {
							int poll = outOfOrder.poll();
							Packet p = packetMap.get(poll);
							inOrderPackets.add(p);
							nextSeqNum += p.getData().length;
						}
						ack.setAckNum(nextSeqNum);
						ack.setSeqNum(mySeqNum);
						mySeqNum++;
						System.out.println("receiver: sending ack " + nextSeqNum);
						send(ack);
					} else {
						// send duplicate ack
						ack.setAckNum(nextSeqNum);
						ack.setSeqNum(mySeqNum-1);
						System.out.println("receiver: sending duplicate ack " + nextSeqNum);
						send(ack);
						if(seqNum > nextSeqNum) {
							// gap detected
							outOfOrder.add(seqNum);
							packetMap.put(seqNum, packet);
						}
					}
				} else if(state == State.LAST_ACK) {
					if(packet.getAck() && packet.getAckNum() == mySeqNum) {
						state = State.CLOSED;
						System.out.println("receiver: CLOSED");
						break;
					}
				}
			}

			File file = new File("out.pdf");
			if(!file.exists()) file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			for(Packet packet : inOrderPackets) {
				byte[] data = packet.getData();
				fos.write(packet.getData(), 0, data.length);
			}
			
			fos.close();
			socket.close();
			System.out.println("file created!");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void send(Packet packet) {
		byte[] bytes = Packet.toBytes(packet);
		DatagramPacket dp = new DatagramPacket(bytes,bytes.length,dstAddr,dstPort);
		try {
			socket.send(dp);
		} catch(IOException e) {
			System.err.println("Unable to send packet");
		}
	}
	
	public static void main(String[] args) {
		new TestReceiver();
	}
	
}

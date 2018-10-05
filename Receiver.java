import java.net.*;
import java.util.*;
import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Receiver {
	private int myPort = 8080;
	private int dstPort;

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
	
	public Receiver(int receiver_port, String filename) {
		this.myPort = receiver_port;
		inOrderPackets = new ArrayList<>();
		outOfOrder = new PriorityQueue<>();
		packetMap = new HashMap<>();
		state = Receiver.State.LISTEN;
		
		try {
			socket = new DatagramSocket(myPort);
			
			Packet testPk = new Packet();
			int pkSize = Packet.toBytes(testPk).length;
			byte[] buffer = new byte[pkSize];
			DatagramPacket dp = new DatagramPacket(buffer,buffer.length);
			
			while(true) {
				socket.receive(dp);
				Packet packet = Packet.fromBytes(dp.getData());
				int seqNum = packet.getSeqNum();
				
				if(state == State.LISTEN) {
					if(packet.getSyn()) {
						// extract destination ip and port
						dstAddr = dp.getAddress();
						dstPort = dp.getPort();
						nextSeqNum = packet.getSeqNum() + 1;
						buffer = new byte[packet.getMSS()];
						testPk.setData(buffer);
						buffer = Packet.toBytes(testPk);
						dp = new DatagramPacket(buffer,buffer.length);
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
					boolean corrupt = isCorrupt(packet);
					if(seqNum == nextSeqNum && !corrupt) {
						inOrderPackets.add(packet);
						nextSeqNum += packet.getData().length;
						// if incoming packet fills a gap in data
						while(!outOfOrder.isEmpty() && outOfOrder.peek() == nextSeqNum) {
							int poll = outOfOrder.poll();
							Packet p = packetMap.get(poll);
							inOrderPackets.add(p);
							System.out.println("incrementing seqNum "+nextSeqNum+" to "+(nextSeqNum+p.getData().length));
							nextSeqNum += p.getData().length;
						}
						ack.setAckNum(nextSeqNum);
						ack.setSeqNum(mySeqNum);
						System.out.println("receiver: sending ack " + nextSeqNum);
						send(ack);
					} else {
						if(corrupt) System.out.println("corrupt packet "+packet.getSeqNum());
						// send duplicate ack
						ack.setAckNum(nextSeqNum);
						ack.setSeqNum(mySeqNum);
						System.out.println("receiver: sending duplicate ack " + nextSeqNum);
						send(ack);
						if(seqNum > nextSeqNum && !corrupt && !outOfOrder.contains(seqNum)) {
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

			File file = new File(filename);
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
	
	public boolean isCorrupt(Packet packet) {
		long senderChecksum = packet.getChecksum();
		Checksum checksum = new CRC32();
		packet.setChecksum(0);
		byte[] bytes = Packet.toBytes(packet);
		checksum.update(bytes, 0, bytes.length);
		packet.setChecksum(senderChecksum);
		if(checksum.getValue() != senderChecksum) return true;
		return false;
	}
	
	public static void main(String[] args) {
		if(args.length < 2) {
			System.err.println("java Receiver receiver_port file_r.pdf");
			return;
		}
		new Receiver(Integer.parseInt(args[0]),
					     args[1]);
	}
	
}

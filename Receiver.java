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
	
	String log = "";
	
	FileOutputStream logfos;
	long baseTime;
	
	int numBytesReceived = 0;
	int totalSegsReceived = 0;
	int dataSegsReceived = 0;
	int bitErrors = 0;
	int dupDataReceived = 0;
	int dupAcksSent = 0;
	
	public Receiver(int receiver_port, String filename) {
		this.myPort = receiver_port;
		inOrderPackets = new ArrayList<>();
		outOfOrder = new PriorityQueue<>();
		packetMap = new HashMap<>();
		state = Receiver.State.LISTEN;

		
		try {
			File logfile = new File("Receiver_log.txt");
			if(!logfile.exists()) logfile.createNewFile();
			logfos = new FileOutputStream(logfile);

			socket = new DatagramSocket(myPort);
			
			Packet testPk = new Packet();
			int pkSize = Packet.toBytes(testPk).length;
			byte[] buffer = new byte[pkSize];
			DatagramPacket dp = new DatagramPacket(buffer,buffer.length);
			
			while(true) {
				socket.receive(dp);
				Packet packet = Packet.fromBytes(dp.getData());
				totalSegsReceived++;
				if(packet.getDataSize() != 0) {
					dataSegsReceived++;
					numBytesReceived += packet.getDataSize();
				}
				int seqNum = packet.getSeqNum();
				
				if(state == State.LISTEN) {
					if(packet.getSyn()) {
						baseTime = System.currentTimeMillis();
						log("rcv","S",packet.getSeqNum(),0,packet.getAckNum());
						// extract destination ip and port
						long start = System.currentTimeMillis();
						dstAddr = dp.getAddress();
						dstPort = dp.getPort();
						nextSeqNum = packet.getSeqNum() + 1;
						buffer = new byte[packet.getMaxBufferSize()];
						dp = new DatagramPacket(buffer,buffer.length);
						// send SYNACK
						Packet synack = new Packet();
						synack.setSyn(true);
						synack.setAck(true);
						synack.setSeqNum(mySeqNum);
						synack.setAckNum(nextSeqNum);
						send(synack);
						log("snd","SA",synack.getSeqNum(),0,synack.getAckNum());
						state = State.SYN_RCVD;
						System.out.println("receiver: SYN_RCVD");
						mySeqNum++;
						System.out.println("syn proccessing time " +(System.currentTimeMillis()-start)+"ms");
					}
				} else if(state == State.SYN_RCVD) {
					if(!packet.getSyn() && packet.getSeqNum() == nextSeqNum 
							&& packet.getAckNum() == mySeqNum) {
						System.out.println("receiver: ESTABLISHED");
						state = State.ESTABLISHED;
						log("rcv","A",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
					}
				} else if(state == State.ESTABLISHED) {
					if(packet.getFin() && seqNum == nextSeqNum) {
						log("rcv","F",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
						Packet finack = new Packet();
						finack.setAck(true);
						nextSeqNum++;
						finack.setAckNum(nextSeqNum);
						finack.setSeqNum(mySeqNum);
						send(finack);
						log("snd","A",finack.getSeqNum(),0,finack.getAckNum());
						state = State.CLOSE_WAIT;
						System.out.println("receiver: ClOSE_WAIT");
						Packet fin = new Packet();
						fin.setFin(true);
						fin.setSeqNum(mySeqNum);
						fin.setAckNum(nextSeqNum);
						mySeqNum++;
						send(fin);
						log("snd","F",fin.getSeqNum(),0,fin.getAckNum());
						state = State.LAST_ACK;
						System.out.println("receiver: LAST_ACK");
						continue;
					}
					System.out.println("receiver: received " + seqNum + " datasize: " + packet.getData().length);
					if(isCorrupt(packet)) {
						bitErrors++;
						log("rcv/corr","D",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
						System.out.println("corrupt packet "+packet.getSeqNum());
						continue;
					}
					Packet ack = new Packet();
					ack.setAck(true);
					// in order packet
					if(seqNum == nextSeqNum) {
						log("rcv","D",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
						inOrderPackets.add(packet);
//						packetMap.put(seqNum, packet);
						nextSeqNum += packet.getData().length;
						// if incoming packet fills a gap in data
						while(!outOfOrder.isEmpty() && outOfOrder.peek() == nextSeqNum) {
							int poll = outOfOrder.poll();
							Packet buff = packetMap.remove(poll);
							inOrderPackets.add(buff);
							System.out.println("incrementing seqNum "+nextSeqNum+" to "+(nextSeqNum+buff.getData().length));
							nextSeqNum += buff.getData().length;
						}
//						// if there were buffered packets ahead, then not reliable timestamp
						ack.setUseTimestamp(packet.getUseTimestamp());
						ack.setTimestamp(packet.getTimestamp());
						ack.setAckNum(nextSeqNum);
						ack.setSeqNum(mySeqNum);
						System.out.println("receiver: sending ack " + nextSeqNum);
						send(ack);
						log("snd","A",ack.getSeqNum(),ack.getDataSize(),ack.getAckNum());
					} else {
						log("rcv","D",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
						// send duplicate ack
						ack.setAckNum(nextSeqNum);
						ack.setSeqNum(mySeqNum);
						System.out.println("receiver: sending duplicate ack " + nextSeqNum);
						send(ack);
						dupAcksSent++;
						log("snd/DA","A",ack.getSeqNum(),0,ack.getAckNum());
						if(outOfOrder.contains(seqNum)) {
							dupDataReceived++;
							continue;
						}
						if(seqNum > nextSeqNum) {
							// gap detected
							outOfOrder.add(seqNum);
							packetMap.put(seqNum, packet);
						}
					}
				} else if(state == State.LAST_ACK) {
					if(packet.getAck() && packet.getAckNum() == mySeqNum) {
						log("rcv","A",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
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
			
			addLogSummary();
			logfos.write(log.getBytes());
			fos.close();
			socket.close();
			System.out.println("file created!");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private void addLogSummary() {
		log += String.format("========================================================\n");
		log += String.format("%-40s %8d\n","Amount of data received (bytes)",numBytesReceived);
		log += String.format("%-40s %8d\n","Total segments Received",totalSegsReceived);
		log += String.format("%-40s %8d\n","Data segments received",dataSegsReceived);
		log += String.format("%-40s %8d\n","Data segments with Bit Errors",bitErrors);
		log += String.format("%-40s %8d\n","Duplicate data segments received",dupDataReceived);
		log += String.format("%-40s %8d\n","Duplicate ACKs sent",dupAcksSent);
		log += String.format("========================================================\n");
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

	private void log(String event, String type, int seqNum, int dataSize, int ackNum) {
		LogLine line = new LogLine(event,(System.currentTimeMillis()-baseTime),type,seqNum,dataSize,ackNum);
		log += line.toString();
	}
}

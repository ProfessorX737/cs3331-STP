import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.io.*;

public class TestSender {
	static int myPort = 3300;
	static String destIp = "127.0.0.1";
	static int dstPort = 8080;
	static String file = "test0.pdf";
	static int MWS = 600;
	static int MSS = 150;
	
	InetAddress dstAddr;
	DatagramSocket socket;

	enum State {NONE, SYN_SENT, ESTABLISHED, FIN_WAIT_1, FIN_WAIT_2, TIME_WAIT, CLOSED};
	State state;
	int send_base;
	int nextSeqNum;
	Semaphore s;
	boolean finished = false;
	Timer timer;
	StopWatch stopWatch;
	Map<Integer, Integer> acked; // mapping acks to number of times it was received
	Map<Integer, Packet> packetMap; // maps sequence number to packet
	int EstimatedRTT;

	int timeoutInterval = 500;
	
	public TestSender() {
		try {
			dstAddr = InetAddress.getByName(destIp);
			socket = new DatagramSocket(myPort);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		state = State.NONE;
		send_base = 0;
		nextSeqNum = 0;
		s = new Semaphore(1);
		finished = false;
		timer = new Timer();
		stopWatch = new StopWatch();
		acked = new HashMap<>();
		packetMap = new HashMap<>();
		
		OutThread out = new OutThread();
		InThread in = new InThread();
		out.start();
		in.start();
	}
	
	public void setTimer(boolean newTimer) {
		timer.cancel();
		if(newTimer) {
			timer = new Timer();
			timer.schedule(new Timeout(), timeoutInterval);
		}
	}
	
	public class Timeout extends TimerTask {
		@Override
		public void run() {
			try {
				s.acquire();
				System.out.println("timeout occurred!" + " window: " + send_base+"-"+nextSeqNum);
				send(packetMap.get(send_base));
				setTimer(true);
				s.release();
			} catch (Exception e) {
				e.printStackTrace();
			}
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

	
	public class OutThread extends Thread {
		public OutThread() {
			
		}

		public void run() {
			try {
				FileInputStream fis = new FileInputStream(new File(file));
				while(true) {
					
					if(state == TestSender.State.FIN_WAIT_1) {
						Packet packet = new Packet();
						packet.setFin(true);
						packet.setSeqNum(nextSeqNum);
						send(packet);
						break;
					}
					
					if(!finished) {
						s.acquire();
						// only send data if window is not full
						if(nextSeqNum - send_base <= MWS) {
							Packet packet = new Packet();
							if(packetMap.containsKey(nextSeqNum)) {
								packet = packetMap.get(nextSeqNum);
							} else {
								byte[] buffer = new byte[MSS];
								int numRead = fis.read(buffer, 0, MSS);
								if(numRead == -1) {
									finished = true;
									System.out.println("finished reading file");
									s.release();
									continue;
								}
								packet.setSeqNum(nextSeqNum);
								packet.setData(buffer);
							}
							send(packet);
							System.out.println("sender: sending packet " + nextSeqNum+ " window: " + send_base+"-"+nextSeqNum);
							if(send_base == nextSeqNum) {
								setTimer(true);
								System.out.println("setting timer for " + nextSeqNum+ " window: " + send_base+"-"+nextSeqNum);
								stopWatch.start();
							}
							packetMap.put(nextSeqNum, packet);
							nextSeqNum += packet.getData().length;
						}
						s.release();
					}
				}
				fis.close();
				socket.close();
				setTimer(false);
				System.out.println("all done!");
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	public class InThread extends Thread {
		public InThread() {
			
		}
		
		public void run() {
			int headerSize = Packet.toBytes(new Packet()).length;
			byte[] buffer = new byte[headerSize];

			while(true) {
				try {
					DatagramPacket dp = new DatagramPacket(buffer,buffer.length);
					socket.receive(dp);
					Packet packet = Packet.fromBytes(buffer);
					int ackNum = packet.getAckNum();
					System.out.println("sender: received ack " + ackNum+ " window: " + send_base+"-"+nextSeqNum);
					s.acquire();
					// if normal ack
					if(ackNum > send_base) {
						send_base = ackNum;
						acked.put(ackNum, 1);
						// if there are any unacknowledged segments, restart timer
						if(send_base == nextSeqNum) {
							setTimer(false);
							System.out.println("stopping timer " + nextSeqNum);
						} else {
							setTimer(true);
							System.out.println("setting timer for " + nextSeqNum + " window: " + send_base+"-"+nextSeqNum);
						}
						EstimatedRTT = stopWatch.getElapsedTime();
						stopWatch.reset();
						if(finished && ackNum == nextSeqNum) {
							state = TestSender.State.FIN_WAIT_1;
							s.release();
							break;
						}
					} else {
						// a duplicate ack
						acked.put(ackNum, acked.get(ackNum)+1);
						if(acked.get(ackNum) == 3) {
							// fast retransmit
							send(packetMap.get(ackNum));
							System.out.println("sender: fast retransmit " + ackNum);
						}
					}
					s.release();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		}
	}

	public static void main(String[] args) {
		new TestSender();
	}
}

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
	int rcvrSeqNum;
	int finalSeqNum;

	int timeoutInterval = 500;
	
	public TestSender() {
		state = State.NONE;
		send_base = 0;
		nextSeqNum = 0;
		s = new Semaphore(1);
		finished = false;
		timer = new Timer();
		stopWatch = new StopWatch();
		acked = new HashMap<>();
		packetMap = new HashMap<>();
		rcvrSeqNum = 0;
		finalSeqNum = 0;

		try {
			dstAddr = InetAddress.getByName(destIp);
			socket = new DatagramSocket(myPort);
			FileInputStream fis = new FileInputStream(new File(file));
			
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		
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
				System.out.println("timeout occurred!" + " resending " + send_base);
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

	// modifies nextSeqNum, state and timer
	// uses send_base
	public class OutThread extends Thread {
		public OutThread() {
			
		}

		public void run() {
			try {
				FileInputStream fis = new FileInputStream(new File(file));
				while(true) {
					
					s.acquire();
					if(state == TestSender.State.NONE) {
						// send SYN
						Packet packet = new Packet();
						packet.setSyn(true);
						packet.setSeqNum(nextSeqNum);
						setTimer(true);
						state = TestSender.State.SYN_SENT;
						send(packet);
						packetMap.put(nextSeqNum, packet);
						nextSeqNum++;
						System.out.println("sender: SYN_SENT");
					} else if(state == TestSender.State.ESTABLISHED) {
						if(!finished) {
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
									stopWatch.start();
								}
								packetMap.put(nextSeqNum, packet);
								nextSeqNum += packet.getData().length;
							}
						}
					} else if(state == TestSender.State.FIN_WAIT_1) {
						Packet packet = new Packet();
						packet.setFin(true);
						packet.setSeqNum(nextSeqNum);
						send(packet);
						break;
					}
					s.release();
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
	
	// modifies send_base, 
	public class InThread extends Thread {
		public InThread() {
			
		}
		
		public void run() {
			int headerSize = Packet.toBytes(new Packet()).length;
			byte[] buffer = new byte[headerSize];

			try {
				while(true) {
					DatagramPacket dp = new DatagramPacket(buffer,buffer.length);
					socket.receive(dp);
					Packet packet = Packet.fromBytes(buffer);
					int ackNum = packet.getAckNum();

					s.acquire(); // === enter lock ===
					if(state == TestSender.State.SYN_SENT) {
						// expecting SYNACK
						if(packet.getAck() && ackNum == nextSeqNum && packet.getSyn()) {
							state = TestSender.State.ESTABLISHED;
							// send ACK for SYNACK (handshake 3/3)
							Packet ack = new Packet();
							ack.setAck(true);
							rcvrSeqNum = packet.getSeqNum() + 1;
							ack.setAckNum(rcvrSeqNum);
							ack.setSeqNum(nextSeqNum);
							send(ack);
							nextSeqNum++;
							System.out.println("sender: ESTABLISHED");
						}
					} else if(state == TestSender.State.ESTABLISHED) {
						// if normal ack
						if(ackNum > send_base) {
							System.out.println("sender: received ack " + ackNum);
							send_base = ackNum;
							acked.put(ackNum, 1);
							if(send_base == nextSeqNum) {
								// no unacked segments so stop timer
								setTimer(false);
							} else {
								// remaining unacked segments so start timer
								setTimer(true);
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
							System.out.println("sender: duplicate ack " + ackNum);
							acked.put(ackNum, acked.get(ackNum)+1);
							if(acked.get(ackNum) == 3) {
								// fast retransmit
								send(packetMap.get(ackNum));
								System.out.println("sender: fast retransmit " + ackNum);
							}
						}
					}
					s.release(); // === exit lock ===
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		new TestSender();
	}
}

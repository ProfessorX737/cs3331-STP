import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.io.*;
import java.lang.reflect.Array;

public class Sender {
	private int dstPort = 8080;
	private String file = "test0.pdf";
	private int MWS = 600;
	private int MSS = 150;
	private int gamma = 4;

	private final float pDrop;
	private final float pDuplicate;
	private final float pCorrupt;
	private final float pOrder;
	private final int maxOrder;
	private final float pDelay;
	private final int maxDelay;
	
	InetAddress dstAddr;
	DatagramSocket socket;

	enum State {NONE, SYN_SENT, ESTABLISHED, FIN_WAIT_1, FIN_WAIT_2, TIME_WAIT, CLOSED};
	State state;
	int send_base;
	int nextSeqNum;
	Semaphore s;
	boolean finished = false;
	Timer timer;
	PacketStopWatch stopWatch;
	Map<Integer, Integer> acked; // mapping acks to number of times it was received
	Map<Integer, Packet> packetMap; // maps sequence number to packet
	int rcvrSeqNum;
	int finalSeqNum;
	int EstimatedRTT;
	int DevRTT;
	float alpha;
	float beta;
	int TimeoutInterval;
	
	FileOutputStream logfos;
	long baseTime;

	private Random random;
	private Packet reorderedPacket;
	private int orderCount;
	private Semaphore delayLock;
	
	public Sender(String receiver_host_ip, int receiver_port, 
			String file, int MWS, int MSS, int gamma, float pDrop,
			float pDuplicate, float pCorrupt, float pOrder, int maxOrder,
			float pDelay, int maxDelay, int seed) {
		state = State.NONE;
		send_base = 0;
		nextSeqNum = 0;
		s = new Semaphore(1);
		finished = false;
		timer = new Timer();
		stopWatch = new PacketStopWatch();
		acked = new HashMap<>();
		packetMap = new HashMap<>();
		rcvrSeqNum = 0;
		finalSeqNum = 0;
		EstimatedRTT = 500;
		DevRTT = 250;
		alpha = 0.125f;
		beta = 0.25f;
		TimeoutInterval = EstimatedRTT + gamma*DevRTT;
		
		this.dstPort = receiver_port;
		this.file = file;
		this.MWS = MWS;
		this.MSS = MSS;
		this.gamma = gamma;

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
		delayLock = new Semaphore(1);

		try {
			dstAddr = InetAddress.getByName(receiver_host_ip);
			socket = new DatagramSocket();
			FileInputStream fis = new FileInputStream(new File(file));
			
			byte[] buffer = new byte[MSS];
			int seqNum = 1;
			int read = 0;
			while((read = fis.read(buffer, 0, MSS)) != -1) {
				Packet packet = new Packet();
				if(read < MSS) {
					buffer = Arrays.copyOf(buffer, read);
				}
				packet.setData(buffer);
				packet.setSeqNum(seqNum);
				packetMap.put(seqNum, packet);
				seqNum += read;
				buffer = new byte[MSS];
			}
			finalSeqNum = seqNum;
			fis.close();

			File log = new File("Sender_log.txt");
			if(!log.exists()) log.createNewFile();
			logfos = new FileOutputStream(log);
		
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

//		pld = new PLD(socket, dstAddr, dstPort,
//				      pDrop, pDuplicate, pCorrupt,
//				      pOrder, maxOrder, pDelay,
//				      maxDelay, seed);

		
		OutThread out = new OutThread();
		InThread in = new InThread();
		out.start();
		in.start();
		baseTime = System.currentTimeMillis();
	}
	
	public void setTimer(boolean newTimer) {
		timer.cancel();
		if(newTimer) {
			timer = new Timer();
			timer.schedule(new Timeout(), TimeoutInterval);
		}
	}
	
	public class Timeout extends TimerTask {
		@Override
		public void run() {
			try {
				s.acquire();
				System.out.println("timeout occurred!" + " resending " + send_base);
				Packet p = packetMap.get(send_base);
				pld_send(packetMap.get(send_base),log("snd/RXT","D",p.getSeqNum(),p.getData().length,p.getAckNum()));
				setTimer(true);
				// if stop watch was timing this segment reset it
				if(stopWatch.isStarted() && stopWatch.getSeqNum() == send_base) {
					stopWatch.reset();
				}
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
	
	public long getChecksum(Packet packet) {
		Checksum checksum = new CRC32();
		packet.setChecksum(0);
		byte[] bytes = Packet.toBytes(packet);
		checksum.update(bytes, 0, bytes.length);
		return checksum.getValue();
	}

	public class OutThread extends Thread {
		public OutThread() {}

		public void run() {
			try {
				while(true) {
					s.acquire();
					if(state == Sender.State.NONE) {

						// send SYN
						Packet packet = new Packet();
						packet.setSyn(true);
						packet.setSeqNum(nextSeqNum);
						packet.setMSS(MSS);
						setTimer(true);
						state = Sender.State.SYN_SENT;
						packet.setChecksum(getChecksum(packet));
						send(packet);
						packetMap.put(nextSeqNum, packet);
						log(log("snd","S",nextSeqNum,0,0));
						nextSeqNum++;
						System.out.println("sender: SYN_SENT");
					} else if(state == Sender.State.ESTABLISHED) {

						// only send data if window is not full
						if(nextSeqNum - send_base <= MWS) {
							if(packetMap.containsKey(nextSeqNum)) {
								Packet packet = packetMap.get(nextSeqNum);
								packet.setChecksum(getChecksum(packet));
								LogLine line = log("snd","D",nextSeqNum,packet.getData().length,packet.getAckNum());
								pld_send(new Packet(packet),line);
								System.out.println("sender: sending packet " + packet.getSeqNum());
								if(send_base == nextSeqNum) {
									setTimer(true);
								}
								nextSeqNum += packet.getData().length;
								if(!stopWatch.isStarted()) {
									System.out.println("setting timer for " + packet.getSeqNum());
									stopWatch.start(packet.getSeqNum(),nextSeqNum);
								}
							}
						}
					} else if(state == Sender.State.CLOSED) {
						System.out.println("sender: CLOSED");
						s.release();
						break;
					}
					s.release();
				}
				socket.close();
				setTimer(false);
				System.out.println("all done!");
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public class InThread extends Thread {
		public InThread() {}
		
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
					if(state == Sender.State.SYN_SENT) {
						// expecting SYNACK
						if(packet.getAck() && ackNum == nextSeqNum && packet.getSyn()) {
							log(log("rcv","SA",packet.getSeqNum(),0,ackNum));
							state = Sender.State.ESTABLISHED;
							// send ACK for SYNACK (handshake 3/3)
							Packet ack = new Packet();
							ack.setAck(true);
							rcvrSeqNum = packet.getSeqNum() + 1;
							ack.setAckNum(rcvrSeqNum);
							ack.setSeqNum(nextSeqNum);
							ack.setChecksum(getChecksum(ack));
							send(ack);
							log(log("snd","A",nextSeqNum,0,rcvrSeqNum));
							System.out.println("sender: ESTABLISHED");
						}
					} else if(state == Sender.State.ESTABLISHED) {
						// if normal ack
						if(ackNum > send_base) {
							log(log("rcv","A",packet.getSeqNum(),0,ackNum));
							System.out.println("sender: received ack="+ackNum+" seqNum="+packet.getSeqNum());
							send_base = ackNum;
							acked.put(ackNum, 1);
							if(send_base == nextSeqNum) {
								// no unacked segments so stop timer
								setTimer(false);
							} else {
								// remaining unacked segments so start timer
								setTimer(true);
							}
							// if ack arrives for the segment that is being timed for RTT
							if(stopWatch.isStarted() && stopWatch.getExpectedAck() == ackNum) {
								int SampleRTT = stopWatch.getElapsedTime();
								EstimatedRTT = (int)((1f-alpha) * EstimatedRTT + alpha*SampleRTT);
								DevRTT = (int)((1f-beta)*DevRTT + beta*Math.abs(SampleRTT - EstimatedRTT));
								TimeoutInterval = EstimatedRTT + gamma*DevRTT;
								System.out.println("Packet "+stopWatch.getSeqNum()+" EstimatedRTT="+EstimatedRTT);
								stopWatch.reset();
							} else if(stopWatch.isStarted() && ackNum > stopWatch.getExpectedAck()) {
								// ack for timed packet is skipped because of duplicate, reset stopWatch
								stopWatch.reset();
								System.out.println("cancel RTT timer for " + stopWatch.getSeqNum());
							}
							// if last ack
							if(ackNum == finalSeqNum) {
								// send FIN
								Packet fin = new Packet();
								fin.setFin(true);
								fin.setSeqNum(finalSeqNum);
								fin.setChecksum(getChecksum(fin));
								fin.setAckNum(rcvrSeqNum);
								log(log("snd","F",finalSeqNum,0,rcvrSeqNum));
								send(fin);
								state = Sender.State.FIN_WAIT_1;
								System.out.println("sender: FIN_WAIT_1");
								nextSeqNum++;
							}
						} else {
							log(log("rcv/DA","A",packet.getSeqNum(),0,ackNum));
							// a duplicate ack
							System.out.println("sender: received duplicate ack " + ackNum);
							acked.put(ackNum, acked.get(ackNum)+1);
							if(acked.get(ackNum) == 3) {
								// fast retransmit
								Packet p = packetMap.get(ackNum);
								pld_send(new Packet(p),log("snd/RXT","D",p.getSeqNum(),p.getData().length,p.getAckNum()));
								if(stopWatch.getSeqNum() == ackNum) {
									stopWatch.reset();
								}
								System.out.println("sender: fast retransmit " + ackNum);
							}
						}
					} else if(state == Sender.State.FIN_WAIT_1) {
						if(ackNum == nextSeqNum) {
							log(log("rcv","A",packet.getSeqNum(),0,packet.getAckNum()));
							state = Sender.State.FIN_WAIT_2;
							System.out.println("sender: FIN_WAIT_2");
						}
					} else if(state == Sender.State.FIN_WAIT_2) {
						if(packet.getFin()) {
							log(log("rcv","F",packet.getSeqNum(),0,packet.getAckNum()));
							Packet finack = new Packet();
							finack.setAck(true);
							finack.setAckNum(packet.getSeqNum()+1);
							finack.setChecksum(getChecksum(finack));
							finack.setSeqNum(nextSeqNum);
							send(finack);
							log(log("snd","A",finack.getSeqNum(),0,finack.getAckNum()));
							state = Sender.State.TIME_WAIT;
							System.out.println("sender: TIME_WAIT");
							// don't wait
							state = Sender.State.CLOSED;
							s.release();
							break;
						}
					} 
					s.release(); // === exit lock ===
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private LogLine log(String event, String type, int seqNum, int dataSize, int ackNum) {
		return new LogLine(event,(System.currentTimeMillis()-baseTime),type,seqNum,dataSize,ackNum);
	}
	
	private void log(LogLine line) {
		try {
			logfos.write(line.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if(args.length < 14) {
			System.err.println("USAGE: java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop\r\n" + 
					"pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed");
			return;
		}
		
		new Sender(args[0],
				   Integer.parseInt(args[1]),
				   args[2],
				   Integer.parseInt(args[3]),
				   Integer.parseInt(args[4]),
				   Integer.parseInt(args[5]),
				   Float.parseFloat(args[6]),
				   Float.parseFloat(args[7]),
				   Float.parseFloat(args[8]),
				   Float.parseFloat(args[9]),
				   Integer.parseInt(args[10]),
				   Float.parseFloat(args[11]),
				   Integer.parseInt(args[12]),
				   Integer.parseInt(args[13]));
	}

	// beware that this function may modify the packet input
	public void pld_send(Packet packet, LogLine line) {
		
		// decide whether to drop the packet
		if(random.nextFloat() < pDrop) {
			System.out.println("dropping packet "+packet.getSeqNum());
			line.appendEvent("/drop");
			log(line);
			return;
		}
		
		// decide whether to duplicate packet
		if(random.nextFloat() < pDuplicate) {
			safe_send(packet,line);
			safe_send(packet,line);
			System.out.println("duplicating packet "+packet.getSeqNum());
			line.appendEvent("/dup");
			log(line);
			return;
		}
		
		// decide whether to corrupt packet
		if(random.nextFloat() < pCorrupt) {
			byte[] data = packet.getData();
			data[0] += 1;
			System.out.println("corrupting packet "+packet.getSeqNum());
			safe_send(packet,line);
			line.appendEvent("/corr");
			log(line);
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
				safe_send(packet,line);
			}
			return;
		}
		
		// decide whether to delay packet
		if(random.nextFloat() < pDelay) {
			Timer timer = new Timer();
			long delay = (long) (random.nextFloat()*maxDelay*1f);
			System.out.println("packet "+packet.getSeqNum()+" to be delayed "+delay+" ms");
			timer.schedule(new DelayedSend(timer,packet,line), delay);
			return;
		}
		
		// if not dropped, duplicated, corrupted, re-ordered or delayed, forward it
		safe_send(packet,line);
		log(line);
	}
	
	public class DelayedSend extends TimerTask {
		private Packet packet;
		private Timer timer;
		private LogLine line;
		public DelayedSend(Timer timer, Packet packet, LogLine line) {
			this.packet = packet;
			this.timer = timer;
			this.line = line;
		}
		public void run() {
			safe_send(packet,line);
			line.appendEvent("/dely");
			log(line);
			timer.cancel();
		}
	}
	
	private void checkReorderSend(LogLine line) {
		if(orderCount == maxOrder && reorderedPacket != null) {
			_send(reorderedPacket);
			line.appendEvent("/rord");
			log(line);
			System.out.println("reordered packet "+reorderedPacket.getSeqNum());
			reorderedPacket = null;
			orderCount = 0;
		}
	}
	
	private void safe_send(Packet packet, LogLine line) {
		try {
			delayLock.acquire();
			_send(packet);
			orderCount++;
			checkReorderSend(line);
			delayLock.release();
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

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
	Queue<Packet> retransmits;
	int rcvrSeqNum;
	int finalSeqNum;
	float EstimatedRTT;
	float DevRTT;
	float alpha;
	float beta;
	int TimeoutInterval;
	int maxUnacked;
	int numUnacked;
	
	FileOutputStream logfos;
	long baseTime;

	private Random random;
	private Packet reorderedPacket;
	private int orderCount;
	private Semaphore delayLock;
	
	String log;
	
	private int sizeOfFile;
	private int numTransmitted;
	private int numPLD;
	private int numDropped;
	private int numCorrupted;
	private int numReordered;
	private int numDuplicated;
	private int numDelayed;
	private int numTimeouts;
	private int numFast;
	private int numDupAcks;
	
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
		EstimatedRTT = 500f;
		DevRTT = 250f;
		alpha = 0.125f;
		beta = 0.25f;
		TimeoutInterval = (int)(EstimatedRTT + gamma*DevRTT);
		maxUnacked = MWS/MSS;
		numUnacked = 0;
		
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
		String rordEvent = "";
		orderCount = 0;
		delayLock = new Semaphore(1);
		
		log = "";
		sizeOfFile = 0;
		numTransmitted = 0;
		numPLD = 0;
		numDropped = 0;
		numCorrupted = 0;
		numReordered = 0;
		numDuplicated = 0;
		numDelayed = 0;
		numTimeouts = 0;
		numFast = 0;
		numDupAcks = 0;

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
				sizeOfFile += read;
				buffer = new byte[MSS];
			}
			finalSeqNum = seqNum;
			fis.close();

			File logfile = new File("Sender_log.txt");
			if(!logfile.exists()) logfile.createNewFile();
			logfos = new FileOutputStream(logfile);
		
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
		if(timer != null) {
			timer.cancel();
			timer = null;
		}
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
				Packet p = new Packet(packetMap.get(send_base));
				p.setUseTimestamp(true);
				p.setTimestamp(System.currentTimeMillis());
				p.setChecksum(getChecksum(p));
				pld_send(p,log("snd/RXT","D",p.getSeqNum(),p.getDataSize(),p.getAckNum()));
				numTimeouts++;
				setTimer(true);
				// if stop watch was timing this segment reset it
				if(stopWatch.isStarted() /*&& stopWatch.getSeqNum() == send_base*/) {
					stopWatch.reset();
				}
				s.release();
			} catch (Exception e) {
				e.printStackTrace();
			}
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
		private int maxBufferSize;
		public OutThread() {
			byte[] buffer = new byte[MSS];
			Packet testPk = new Packet();
			testPk.setData(buffer);
			maxBufferSize = Packet.toBytes(testPk).length;
		}

		public void run() {
			try {
				while(true) {
					s.acquire();
					if(state == Sender.State.NONE) {
						// send SYN
						Packet packet = new Packet();
						packet.setSyn(true);
						packet.setSeqNum(nextSeqNum);
						packet.setMaxBufferSize(maxBufferSize);
						setTimer(true);
						state = Sender.State.SYN_SENT;
						packet.setChecksum(getChecksum(packet));
						packetMap.put(nextSeqNum, packet);
						baseTime = System.currentTimeMillis();
						_send(packet);
						log(log("snd","S",nextSeqNum,0,0));
						nextSeqNum++;
						System.out.println("sender: SYN_SENT");
					} else if(state == Sender.State.ESTABLISHED) {
						
						if(packetMap.containsKey(nextSeqNum)) {
							Packet packet = packetMap.get(nextSeqNum);
							if(nextSeqNum - send_base <= MWS - packet.getDataSize()) {
								packet.setAckNum(rcvrSeqNum);
								packet.setUseTimestamp(true);
								packet.setTimestamp(System.currentTimeMillis());
								packet.setChecksum(getChecksum(packet));
								LogLine line = log("snd","D",nextSeqNum,packet.getData().length,packet.getAckNum());
								pld_send(new Packet(packet),line);
								numUnacked++;
								System.out.println("sender: sending packet " + packet.getSeqNum());
								//if(send_base == nextSeqNum) {
								if(timer == null) {
									setTimer(true);
									//stopWatch.start(nextSeqNum, nextSeqNum+packet.getDataSize());
								}
								nextSeqNum += packet.getData().length;
//								if(!stopWatch.isStarted()) {
//									System.out.println("setting timer for " + packet.getSeqNum());
//									stopWatch.start(packet.getSeqNum(),nextSeqNum);
//								}
							}
						}
					} else if(state == Sender.State.CLOSED) {
						System.out.println("sender: CLOSED");
						s.release();
						break;
					}
					s.release();
				}
				addLogSummary();
				logfos.write(log.getBytes());
				socket.close();
				setTimer(false);
				System.out.println("all done!");
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void addLogSummary() {
		log += String.format("========================================================\n");
		log += String.format("%-45s %8d\n","Size of the file (in Bytes)",sizeOfFile);
		log += String.format("%-45s %8d\n","Segments transmitted (including drop & RXT)",numTransmitted);
		log += String.format("%-45s %8d\n","Number of Segments handled by PLD",numPLD);
		log += String.format("%-45s %8d\n","Number of Segments dropped",numDropped);
		log += String.format("%-45s %8d\n","Number of Segments Corrupted",numCorrupted);
		log += String.format("%-45s %8d\n","Number of Segments Re-ordered",numReordered);
		log += String.format("%-45s %8d\n","Number of Segments Duplicated",numDuplicated);
		log += String.format("%-45s %8d\n","Number of Segments Delayed",numDelayed);
		log += String.format("%-45s %8d\n","Number of Retransmissions due to TIMEOUT",numTimeouts);
		log += String.format("%-45s %8d\n","Number of FAST RETRANSMISSON",numFast);
		log += String.format("%-45s %8d\n","Number of DUP ACKS received",numDupAcks);
		log += String.format("========================================================\n");
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
							_send(ack);
							log(log("snd","A",nextSeqNum,0,rcvrSeqNum));
							System.out.println("sender: ESTABLISHED");
						}
					} else if(state == Sender.State.ESTABLISHED) {
						// if normal ack
						if(ackNum > send_base) {
//							for(int key : new HashSet<Integer>(acked.keySet())) {
//								if(key < ackNum) {
//									acked.remove(key);
//								}
//							}
//							for(int key : new HashSet<Integer>(packetMap.keySet())) {
//								if(key < ackNum) {
//									packetMap.remove(key);
//								}
//							}
							log(log("rcv","A",packet.getSeqNum(),0,ackNum));
							System.out.println("sender: received ack="+ackNum+" seqNum="+packet.getSeqNum());
							send_base = ackNum;
							acked.put(ackNum, 0);

							if(packet.getUseTimestamp()) {
								float SampleRTT = System.currentTimeMillis() - packet.getTimestamp();
								EstimatedRTT = ((1f-alpha) * EstimatedRTT + alpha*SampleRTT);
								DevRTT = ((1f-beta)*DevRTT + beta*Math.abs(SampleRTT - EstimatedRTT));
								TimeoutInterval = Math.round(EstimatedRTT + gamma*DevRTT);
								System.out.println("Packet "+stopWatch.getSeqNum()+" Timeout="+TimeoutInterval);
							}
//							if(stopWatch.isStarted() && stopWatch.getExpectedAck() == ackNum) {
//								float SampleRTT = 1f*stopWatch.getElapsedTime();
//								EstimatedRTT = ((1f-alpha) * EstimatedRTT + alpha*SampleRTT);
//								DevRTT = ((1f-beta)*DevRTT + beta*Math.abs(SampleRTT - EstimatedRTT));
//								TimeoutInterval = Math.round(EstimatedRTT + gamma*DevRTT);
//								System.out.println("Packet "+stopWatch.getSeqNum()+" new Timout value="+TimeoutInterval+" ms");
//								stopWatch.reset();
//							} else if(stopWatch.isStarted() && ackNum > stopWatch.getExpectedAck()) {
//								// ack for timed packet is skipped because of duplicate, reset stopWatch
//								stopWatch.reset();
//								System.out.println("cancel RTT timer for " + stopWatch.getSeqNum());
//							}

							if(send_base == nextSeqNum) {
								// no unacked segments so stop timer
								setTimer(false);
							} else {
								// remaining unacked segments so start timer
								setTimer(true);
								//stopWatch.start(send_base, send_base+packetMap.get(send_base).getDataSize());
							}
							// if last ack
							if(ackNum == finalSeqNum) {
								// send FIN
								Packet fin = new Packet();
								fin.setFin(true);
								fin.setSeqNum(finalSeqNum);
								fin.setAckNum(rcvrSeqNum);
								fin.setChecksum(getChecksum(fin));
								log(log("snd","F",finalSeqNum,0,rcvrSeqNum));
								_send(fin);
								state = Sender.State.FIN_WAIT_1;
								System.out.println("sender: FIN_WAIT_1");
								nextSeqNum++;
							}
						} else {
							log(log("rcv/DA","A",packet.getSeqNum(),0,ackNum));
							// a duplicate ack
							System.out.println("sender: received duplicate ack " + ackNum);
							numDupAcks++;
							acked.put(ackNum, acked.get(ackNum)+1);
							if(acked.get(ackNum) == 3) {
								// fast retransmit
								acked.put(ackNum, 0);
								Packet p = new Packet(packetMap.get(ackNum));
								p.setUseTimestamp(true);
								p.setTimestamp(System.currentTimeMillis());
								p.setChecksum(getChecksum(p));
								pld_send(p,log("snd/RXT","D",p.getSeqNum(),p.getDataSize(),p.getAckNum()));
								numFast++;
								//retransmits.add(p);
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
							finack.setSeqNum(nextSeqNum);
							finack.setChecksum(getChecksum(finack));
							_send(finack);
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
		this.log += line.toString();
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
			line.setEvent("drop");
			numDropped++;
			numTransmitted++;
			numPLD++;
			log(line);
			return;
		}
		
		// decide whether to duplicate packet
		if(random.nextFloat() < pDuplicate) {
			log(line);
			LogLine copy = new LogLine(line);
			copy.appendEvent("/dup");
			log(copy);
			safe_send(packet,line);
			safe_send(packet,line);
			System.out.println("duplicating packet "+packet.getSeqNum());
			numDuplicated++;
			numPLD += 2;
			return;
		}
		
		// decide whether to corrupt packet
		if(random.nextFloat() < pCorrupt) {
			byte[] data = packet.getData();
			data[0] += 1;
			System.out.println("corrupting packet "+packet.getSeqNum());
			LogLine copy = new LogLine(line);
			copy.appendEvent("/corr");
			log(copy);
			safe_send(packet,line);
			numCorrupted++;
			numPLD++;
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
				numPLD++;
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
		numPLD++;
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
			LogLine copy = log(line.getEvent(),"D",packet.getSeqNum(),packet.getDataSize(),packet.getAckNum());
			numPLD++;
			copy.appendEvent("/dely");
			numDelayed++;
			log(copy);
			timer.cancel();
		}
	}
	
	private void checkReorderSend(LogLine line) {
		if(orderCount == maxOrder && reorderedPacket != null) {
			_send(reorderedPacket);
			numPLD++;
			LogLine copy = log(line.getEvent(),"D",reorderedPacket.getSeqNum(),reorderedPacket.getDataSize(),reorderedPacket.getAckNum());
			copy.appendEvent("/rord");
			numReordered++;
			log(copy);
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
			numTransmitted++;
		} catch(IOException e) {
			System.err.println("Unable to send packet");
		}
	}
}

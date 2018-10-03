import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
	private final int MY_PORT = 0;
	private InetAddress recvrAddr;
	private DatagramSocket senderSocket;
	private enum State {NONE, SYN_SENT, ESTABLISHED, FIN_WAIT_1, FIN_WAIT_2, TIME_WAIT, CLOSED};
	private State state;
	private int initial_seq_num = 0;
	private int timeOut = 0;
	private Timer timer;

	// command line inputs
	private String receiver_host_ip;
	private int receiver_port;
	private String file;
	private int MWS;
	private int MSS;
	private int gamma;
	private float pDrop;
	private float pDuplicate;
	private float pCorrupt;
	private float pOrder;
	private int maxOrder;
	private float pDelay;
	private int maxDelay;
	private int seed;
	
	public Sender(String receiver_host_ip, int receiver_port, 
			String file, int MWS, int MSS, int gamma, float pDrop,
			float pDuplicate, float pCorrupt, float pOrder, int maxOrder,
			float pDelay, int maxDelay, int seed) {
		this.receiver_host_ip = receiver_host_ip;
		this.receiver_port = receiver_port;
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
		this.seed = seed;
	}
	
	public static void main(String[] args) throws Exception 
	{
		if(args.length < 2) {
			System.err.println("USAGE: java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop\r\n" + 
					"pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed");
			return;
		}
		
		new Sender(args[0],
				   parseInt(args[1]),
				   args[2],
				   parseInt(args[3]),
				   parseInt(args[4]),
				   parseInt(args[4]),
				   parseFloat(args[6]),
				   parseFloat(args[7]),
				   parseFloat(args[8]),
				   parseFloat(args[9]),
				   parseInt(args[10]),
				   parseFloat(args[11]),
				   parseInt(args[12]),
				   parseInt(args[13]));
		
		// three way handshake
		Packet syn = new Packet();
		syn.setSyn(true);
		syn.setSeqNum(initial_seq_num);
		send(syn);

		
		state = State.NONE;
		
		// calculate header size
		int headerSize = Packet.toBytes(new Packet()).length;
		
		int nextSeqNumber = initial_seq_num;
		
		while(true) {
			byte[] buff = new byte[headerSize+MSS];
			DatagramPacket packet = new DatagramPacket(buff,buff.length);
			senderSocket.setSoTimeout(timeOut);
			try {
				senderSocket.receive(packet);
			} catch(SocketTimeoutException e) {
				if(timeOut != 0) {
					System.out.println("Sender: timeout after " + senderSocket.getSoTimeout());
				}
			}
			
			if(state == State.NONE) {
				// Initiate three-way handshake
				Packet syn = new Packet();
				syn.setSyn(true);
				syn.setSeqNum(initial_seq_num);
				send(syn);
				// start timer
				
				state = State.SYN_SENT;
			} else if(state == State.SYN_SENT) {
				Packet ack = Packet.fromBytes(buff);
				if(ack.getAck() != )
			}
			
		}
		
	}
	
	
	public class OutThread extends Thread {

		private DatagramSocket outSk;
		private InetAddress dstAddr;
		private int dstPort;

		public OutThread(DatagramSocket outSk, InetAddress dstAddr, int dstPort) {
			this.outSk = outSk;
			this.dstPort = dstPort;
			this.dstAddr = dstAddr;
		}
		
		public void run() {

		}
		public void send(Packet packet) {
			byte[] bytes = Packet.toBytes(packet);
			DatagramPacket dp = new DatagramPacket(bytes,bytes.length,dstAddr,dstPort);
			try {
				outSk.send(dp);
			} catch(IOException e) {
				System.err.println("Unable to send packet");
			}
		}
	}

	public class InThread extends Thread {

		private DatagramSocket inSk;

		public InThread(DatagramSocket inSk) {
			this.inSk = inSk;
		}
		
		public void run() {
		}
	}
	
	public InetAddress getHostByName(String host) {
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(host);
		} catch(UnknownHostException e) {
			System.err.println("Unkown host: " + host);
		}
		return addr;
	}
	
	public static int parseInt(String arg) {
		return Integer.parseInt(arg);
	}
	public static float parseFloat(String arg) {
		return Float.parseFloat(arg);
	}
	
}

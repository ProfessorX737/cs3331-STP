import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Packet implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean syn;
	private boolean ack;
	private boolean fin;
	private int seqNum;
	private int ackNum;
	private int recvWindow;
	private byte[] data;
	
	public Packet(boolean syn, boolean ack, boolean fin, 
			int seqNum, int ackNum, int recvWindow, byte[] data) {
		this.syn = syn;
		this.ack = ack;
		this.fin = fin;
		this.seqNum = seqNum;
		this.ackNum = ackNum;
		this.data = data;
	}
	
	public Packet() {
		this(false,false,false,0,0,0,null);
	}
	
	public boolean getSyn() {
		return syn;
	}
	
	public boolean getAck() {
		return ack;
	}
	
	public boolean getFin() {
		return fin;
	}
	
	public int getSeqNum() {
		return seqNum;
	}
	
	public int getAckNum() {
		return ackNum;
	}
	
	public int getRecvWindow() {
		return recvWindow;
	}
	
	public byte[] getData() {
		return data;
	}
	
	public void setSyn(boolean syn) {
		this.syn = syn;
	}
	
	public void setAck(boolean ack) {
		this.ack = ack;
	}
	
	public void setFin(boolean fin) {
		this.fin = fin;
	}
	
	public void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
	}
	
	public void setAckNum(int ackNum) {
		this.ackNum = ackNum;
	}
	
	public void setRecvWindow(int recvWindow) {
		this.recvWindow = recvWindow;
	}
	
	public void setData(byte[] data) {
		this.data = data;
	}
	
	public String toString() {
		String result = "";
		result += syn ? "1;" : "0;";
		result += ack ? "1;" : "0;";
		result += fin ? "1;" : "0;";
		result += Integer.toString(seqNum) + ";";
		result += Integer.toString(ackNum) + ";";
		result += Integer.toString(recvWindow) + ";";
		return result;
	}
	
	public static byte[] toBytes(Object object) {
		byte[] bytes = null;
		try{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = null;
			out = new ObjectOutputStream(bos);
			out.writeObject(object);
			out.flush();
			bos.close();
			bytes = bos.toByteArray();
			bos.close();
			out.close();
		} catch(IOException e) {
			System.err.println("Cannot convert object to bytes");
		}
		return bytes;
	}
	
	public static Packet fromBytes(byte[] bytes) {
		Packet packet = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			ObjectInput in = new ObjectInputStream(bis);
			packet = (Packet)in.readObject();
			bis.close();
			in.close();
		} catch(Exception e) {
			System.err.println("Cannot convert bytes to Packet object");
		}
		return packet;
	}

	
}

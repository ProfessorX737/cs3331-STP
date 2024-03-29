import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

public class Packet implements Serializable {
	private static final long serialVersionUID = 1L;
	private int seqNum;
	private int ackNum;
	private boolean syn;
	private boolean ack;
	private boolean fin;
	private boolean useTimestamp;
	private int maxBufferSize;
	private long timestamp;
	private long checksum;
	private byte[] data;
	
	public Packet(boolean syn, boolean ack, boolean fin, 
			int seqNum, int ackNum, int maxBufferSize, boolean useTimestamp, long timestamp, long checksum, byte[] data) {
		this.syn = syn;
		this.ack = ack;
		this.fin = fin;
		this.seqNum = seqNum;
		this.ackNum = ackNum;
		this.maxBufferSize = maxBufferSize;
		this.useTimestamp = useTimestamp;
		this.timestamp = timestamp;
		this.checksum = checksum;
		this.data = data;
	}
	
	public Packet(Packet p) {
		this(p.getSyn(),
			 p.getAck(),
			 p.getFin(),
			 p.getSeqNum(),
			 p.getAckNum(),
			 p.getMaxBufferSize(),
			 p.getUseTimestamp(),
			 p.getTimestamp(),
			 p.getChecksum(),
			 Arrays.copyOf(p.getData(), p.getData().length));
	}
	
	public Packet() {
		this(false,false,false,0,0,0,false,0,0,null);
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
	
	public int getMaxBufferSize() {
		return maxBufferSize;
	}
	
	public boolean getUseTimestamp() {
		return this.useTimestamp;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public long getChecksum() {
		return checksum;
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
	
	public void setMaxBufferSize(int maxBufferSize) {
		this.maxBufferSize = maxBufferSize;
	}
	
	public void setData(byte[] data) {
		this.data = data;
	}
	
	public void setChecksum(long checksum) {
		this.checksum = checksum;
	}
	
	public void setUseTimestamp(boolean useTimestamp) {
		this.useTimestamp = useTimestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String toString() {
		String result = "";
		result += syn ? "1;" : "0;";
		result += ack ? "1;" : "0;";
		result += fin ? "1;" : "0;";
		result += Integer.toString(seqNum) + ";";
		result += Integer.toString(ackNum) + ";";
		result += Integer.toString(maxBufferSize) + ";";
		result += Long.toString(checksum) + ";";
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

	public int getDataSize() {
		if(this.data != null) return this.data.length;
		return 0;
	}
	
}

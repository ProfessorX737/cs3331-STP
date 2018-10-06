
public class LogLine {
	private String event;
	private long time_ms;
	private String type;
	private int seqNum;
	private int dataSize;
	private int ackNum;
	
	LogLine(String event, long time_ms, String type, int seqNum, int dataSize, int ackNum) {
		this.event = event;
		this.time_ms = time_ms;
		this.type = type;
		this.seqNum = seqNum;
		this.dataSize = dataSize;
		this.ackNum = ackNum;
	}
	
	public String toString() {
		return String.format("%-10s\t%8.2f\t%8s\t%8d\t%8d\t%8d\n",event,
				(time_ms*1f/1000f),type,seqNum,dataSize,ackNum);
	}
	
	public void appendEvent(String event) {
		this.event += event;
	}
	
	public void setEvent(String event) {
		this.event = event;
	}
}

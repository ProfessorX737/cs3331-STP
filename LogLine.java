
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
		return String.format("%s\t\t\t%.2f\t%s\t%d\t%d\t%d\n",event,
				(time_ms*1f/1000f),type,seqNum,dataSize,ackNum);
	}
	
	public void appendEvent(String event) {
		this.event += event;
	}
}

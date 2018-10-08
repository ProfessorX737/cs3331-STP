
public class LogLine {
	private String event;
	private long time_ms;
	private String type;
	private int seqNum;
	private int dataSize;
	private int ackNum;
	
	public LogLine(String event, long time_ms, String type, int seqNum, int dataSize, int ackNum) {
		this.event = event;
		this.time_ms = time_ms;
		this.type = type;
		this.seqNum = seqNum;
		this.dataSize = dataSize;
		this.ackNum = ackNum;
	}
	
	public LogLine(LogLine line) {
		this(new String(line.event),line.time_ms,new String(line.type),line.seqNum,line.dataSize,line.ackNum);
	}
	
	public String toString() {
		return String.format("%-15s\t%5.2f\t%5s\t%8d\t%6d\t%6d\n",event,
				(time_ms*1f/1000f),type,seqNum,dataSize,ackNum);
	}
	
	public void appendEvent(String event) {
		this.event += event;
	}
	
	public void setEvent(String event) {
		this.event = event;
	}
	
	public String getEvent() {
		return this.event;
	}
}

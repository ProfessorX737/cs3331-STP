public class PacketStopWatch {
	private int seqNum;
	private int expectedAck;
	boolean started;
	private long startTime;

	public PacketStopWatch() {
		this.seqNum = 0;
		expectedAck = 0;
		started = false;
		startTime = 0;
	}
	public void start(int seqNum, int expectedAck) {
		this.seqNum = seqNum;
		this.expectedAck = expectedAck;
		this.started = true;
		this.startTime = System.currentTimeMillis();
	}
	public int getElapsedTime() {
		return (int)(System.currentTimeMillis() - this.startTime);
	}
	public void reset() {
		this.started = false;
		startTime = 0;
	}
	public boolean isStarted() {
		return this.started;
	}
	public int getSeqNum() {
		return this.seqNum;
	}
	public int getExpectedAck() {
		return this.expectedAck;
	}
}

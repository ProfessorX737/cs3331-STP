public class StopWatch {
	boolean started;
	private long startTime;
	public StopWatch() {
		started = false;
		startTime = 0;
	}
	public void start() {
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
}

import java.util.Timer;
import java.util.TimerTask;

public class TestTimer {
	private static Timer timer = null;
	private static int timeoutVal = 2000;

	public void setTimer(boolean isNewTimer) {
		if(timer != null) timer.cancel();
		if(isNewTimer) {
			timer = new Timer();
			timer.schedule(new Timeout(), timeoutVal);
		}
	}
	public class Timeout extends TimerTask {
		public Timeout() {}
		@Override
		public void run() {
			System.out.println("time out occured!");
			timer.cancel();
		}
	}
	public static void main(String[] args) {
		TestTimer tt = new TestTimer();
		System.out.println("setting timer");
		tt.setTimer(true);
	}
}

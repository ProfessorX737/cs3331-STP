import java.util.*;
import java.util.concurrent.*;

public class Test {
	int send_base;
	Semaphore s;
	
	public Test() {
		send_base = 0;
		s = new Semaphore(1);
		OutThread out = new OutThread();
		InThread in = new InThread();
		out.start();
		in.start();
	}

	public static void main(String[] args) {
		new Test();
	}
	
	public class OutThread extends Thread {
		public OutThread() {
			
		}
		
		public void run() {
			for(int i = 0; i < 1000; i++) {
				acquire();
				int prev = send_base;
				// if send base is even
				if(send_base % 2 == 0) {
					send_base++;
				System.out.println("out: prev=" + prev + " new=" + send_base);
				}
				release();
			}
			System.out.println("out" + send_base);
		}
	}

	public class InThread extends Thread {
		public InThread() {
			
		}
		
		public void run() {
			for(int i = 0; i < 1000; i++) {
				acquire();
				int prev = send_base;
				// if send base is odd
				if(send_base % 2 != 0) {
					send_base++;
				System.out.println("In: prev=" + prev + " new=" + send_base);
				}
				release();
			}
			System.out.println("in " + send_base);
		}
	}
	
	public void acquire() {
		try {
			s.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void release() {
		s.release();
	}
}

import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
	private static DatagramSocket recvrSocket;
	private static enum State {NONE, LISTEN, SYN_RCVD, ESTABLISHED, CLOSE_WAIT, LAST_ACK, CLOSED};
	private static State state;
	private static int receiver_port;

	public static void main(String[] args) {
		if(args.length < 2) {
			System.err.println("java Receiver receiver_port file_r.pdf");
			return;
		}
		
		receiver_port = Integer.parseInt(args[1]);
		state = State.NONE;
		
		while(true) {
			if(state == State.NONE) {
				
			}
		}
	}

}

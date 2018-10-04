import java.util.*;

public class TestQueue {
	public static void main(String[] args) {
		Queue<Integer> queue = new PriorityQueue<>();
		queue.add(4);
		queue.add(2);
		queue.add(5);
		queue.add(1);
		queue.add(3);
		int size = queue.size();
		for(int i = 0; i < size; i++) {
			System.out.println(queue.poll());
		}
	}
}

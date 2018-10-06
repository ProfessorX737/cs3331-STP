import java.util.Random;

public class TestRandom {
	static Random random = new Random(100);
	
	public static void main(String[] args) {
		for(int i = 0; i < 10; i++) {
			if(random.nextFloat() < 0.1f) {
				System.out.println(i+": drop");
				continue;
			}
			if(random.nextFloat() < 0.0f) {
				System.out.println(i+": drop");
				continue;
			}
			if(random.nextFloat() < 0.0f) {
				System.out.println(i+": drop");
				continue;
			}
			if(random.nextFloat() < 0.0f) {
				System.out.println(i+": drop");
				continue;
			}
			if(random.nextFloat() < 0.0f) {
				System.out.println(i+": drop");
				continue;
			}
		}
	}
}

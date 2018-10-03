import java.util.*;
import java.io.*;

public class TestFile {
	public static void main(String[] args) {
		if(args.length < 2) {
			System.err.println("USAGE: filename");
			return;
		}
		List<Packet> packets = new ArrayList<>();
		int MSS = Integer.parseInt(args[1]);
		int send_base = 0;
		try {
			FileInputStream fis = new FileInputStream(new File(args[0]));
			int read;
			byte[] buffer = new byte[MSS];
			while((read = fis.read(buffer, 0, MSS)) != -1) {
				Packet packet = new Packet();
				packet.setData(buffer);
				packets.add(packet);
				buffer = new byte[MSS];
			}

			File file = new File("src/out.pdf");
			if(!file.exists()) file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			for(Packet packet : packets) {
				byte[] data = packet.getData();
				fos.write(packet.getData(), 0, data.length);
			}
			
			fis.close();
			fos.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}

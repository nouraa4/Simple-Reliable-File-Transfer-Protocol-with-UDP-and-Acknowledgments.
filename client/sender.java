import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class sender {

   
    private static final int SENDER_PORT = 6000;
    private static int RECEIVER_PORT;
    private static final int WINDOW_SIZE = 8;
    private static final int PACKET_SIZE = 500;
    private static final int TIMEOUT = 1000;
    private InetAddress receiverAddress;
    private final String filename;
    private long fileSize;
    private long fileSize1;
    private DatagramSocket senderSocket;
    private FileInputStream fileInputStream;
    private List<Packet> window;
    private int base;
    private int nextSeqNum;
    private boolean isTransferComplete;
    private File file;
    private int numPackets ;
    private long start_time ;
    private long end_time ;
    
    

    public sender(InetAddress receiverAddress ,int receiver_port , String filename) throws IOException {
        this.receiverAddress = receiverAddress; //receiver address 
        this.filename = filename;
        this.RECEIVER_PORT = receiver_port; //receiver port number  
        System.out.println(filename);
        this.senderSocket = new DatagramSocket(SENDER_PORT );
        this.file = new File(filename);
        this.fileSize = file.length();
	fileSize1 = fileSize;
        this.numPackets = (int)Math.ceil((double) fileSize/PACKET_SIZE);
        this.fileInputStream = new FileInputStream(file);
        this.window = new ArrayList<>();
        this.base = 0;
        this.nextSeqNum = 0;
        this.isTransferComplete = false;
        
    }

    public void start() throws Exception {
        
        start_time = System.currentTimeMillis();
        // Exchange metadata with the receiver
        exchangeMetadata();
        
        // Start a thread to receive acknowledgments
        Thread ackReceiverThread = new Thread(new AckReceiver());
        ackReceiverThread.start();
        System.out.println(isTransferComplete);
        // Start sending packets
        while (!isTransferComplete) {
            
            while (nextSeqNum < base + WINDOW_SIZE && !isTransferComplete) {
                byte[] data = new byte[PACKET_SIZE];
                int bytesRead = fileInputStream.read(data);

                if (bytesRead == -1) {
                    isTransferComplete = true;
                    break;
                }

                Packet packet = new Packet(nextSeqNum, bytesRead, data);
                System.out.println(nextSeqNum);
                sendPacket(packet);
                window.add(packet);

                nextSeqNum++;
            }

            waitForAck();
        }
        
        end_time = System.currentTimeMillis();
        fileInputStream.close();
        senderSocket.close();

        // Calculate and output statistics
        printStatistics();
    }

    private void exchangeMetadata() throws Exception {
        byte[] filenameBytes = filename.getBytes();
        System.out.println("1");
        // Send filename to the receiver
        DatagramPacket filenamePacket = new DatagramPacket(filenameBytes, filenameBytes.length, receiverAddress,
                RECEIVER_PORT);
        senderSocket.send(filenamePacket);
        System.out.println("2");
        // Receive file size from the receiver
        byte[] fileSizeBytes = new byte[8];
        DatagramPacket fileSizePacket = new DatagramPacket(fileSizeBytes, fileSizeBytes.length);
        senderSocket.receive(fileSizePacket);
        fileSize = ByteBuffer.wrap(fileSizeBytes).getLong();
    }

    private void sendPacket(Packet packet) throws IOException {
        byte[] packetBytes = packet.toBytes();
        DatagramPacket datagramPacket = new DatagramPacket(packetBytes, packetBytes.length, receiverAddress,
                RECEIVER_PORT);
        senderSocket.send(datagramPacket);
    }

    private void waitForAck() throws InterruptedException, IOException {
        Thread.sleep(TIMEOUT);

        for (Packet packet : window) {
            if (!packet.isAckReceived()) {
                sendPacket(packet);
            }
        }
    }

    private void printStatistics() {
        // Calculate and output statistics like throughput and delay
        double transferTime = (end_time - start_time) / 1000.0;
        double throughput = fileSize1 / transferTime;
        double delay = transferTime / numPackets;
            
            System.out.println("Number of Packets: " + numPackets);
            System.out.println("File transfer completed successfully.");
            System.out.println("Transfer time: " + transferTime + " seconds");
            System.out.println("Throughput: " + throughput + " bytes/second");
            System.out.println("Delay: " + delay + " seconds/packet");
        
    }

    private class AckReceiver implements Runnable {
        @Override
        public void run() {
           
                while (!isTransferComplete) {
                    byte[] ackBytes = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length);
                    try {
                        senderSocket.receive(ackPacket);
                    } catch (IOException ex) {
                        Logger.getLogger(sender.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    int ackSeqNum = ByteBuffer.wrap(ackBytes).getInt();
                    System.out.println(ackSeqNum);
                    updateWindow(ackSeqNum);
                }
            
        }

        private void updateWindow(int ackSeqNum) {
            int ackIndex = ackSeqNum - base;

            if (ackIndex >= 0 && ackIndex < WINDOW_SIZE) {
                Packet packet = window.get(ackIndex);
                packet.setAckReceived(true);

                if (ackSeqNum == base) {
                    while (!window.isEmpty() && window.get(0).isAckReceived()) {
                        window.remove(0);
                        base++;
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws  IOException, Exception  {
       
        
         if (args.length != 3) {
             System.out.println(args.length );
         System.out.println("Usage: java Sender <receiver-ip> <Port_receiver> <filename>");
         
         }else{
       InetAddress add = InetAddress.getByName(args[0]);
       int rev_port = Integer.parseInt(args[1]);
        String  file_name = args[2];
        sender s = new sender(add,rev_port, file_name);
       s.start();
         }
    }
    
}

class Packet {
    private static final int HEADER_SIZE = 12;
    private int seqNum;
    private int dataLength;
    private byte[] data;
    private boolean isAckReceived;

    public Packet(int seqNum, int dataLength, byte[] data) {
        this.seqNum = seqNum;
        this.dataLength = dataLength;
        this.data = data;
        this.isAckReceived = false;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public int getDataLength() {
        return dataLength;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isAckReceived() {
        return isAckReceived;
    }

    public void setAckReceived(boolean ackReceived) {
        isAckReceived = ackReceived;
    }

    public boolean isLastPacket() {
        return dataLength < 500;
    }

   public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + dataLength);
        buffer.putInt(seqNum);
        buffer.putInt(dataLength);
        buffer.put(data, 0, dataLength);
        return buffer.array();
    }
   
public static Packet fromBytes(byte[] bytes) {
    System.out.println(bytes.length);
    if (bytes.length < HEADER_SIZE) {
        throw new IllegalArgumentException("Invalid byte array length");
    }

    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    int seqNum = buffer.getInt();
    int dataLength = buffer.getInt();

    if (bytes.length < HEADER_SIZE + dataLength) {
        throw new IllegalArgumentException("Invalid byte array length for data");
    }

    byte[] data = new byte[dataLength];
    buffer.get(data, 0, dataLength);

    return new Packet(seqNum, dataLength, data);
}
}


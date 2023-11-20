import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;


class Receive_UDP {

    private static final int SENDER_PORT = 6000;
    private static final int RECEIVER_PORT = 6500;
    private static final int PACKET_SIZE = 500;
    private DatagramSocket receiverSocket;
    private FileOutputStream fileOutputStream;
    private String filename;
    private long fileSize;
    private boolean isTransferComplete;
    private InetAddress senderAddress;

    public Receive_UDP() throws IOException {
        this.receiverSocket = new DatagramSocket(RECEIVER_PORT);
        this.senderAddress = null;
        this.fileOutputStream = null;
        this.filename = null;
        this.fileSize = 0;
        this.isTransferComplete = false;
    }

    public void start() throws Exception {
        // Receive filename from the sender
        receiveFilename();
        System.out.println("1");

        // Exchange file size with the sender
          exchangeFileSize();
        System.out.println("2");

        // Start receiving packets
        while (!isTransferComplete) {
            System.out.println("3");
            byte[] packetBytes = new byte[PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length);
            System.out.println("4");
            receiverSocket.receive(packet);
            System.out.println("5");
            Packet receivedPacket = Packet.fromBytes(packetBytes);
            processPacket(receivedPacket);
        }

        fileOutputStream.close();
        receiverSocket.close();
    }

    private void receiveFilename() throws IOException {
        byte[] filenameBytes = new byte[PACKET_SIZE];
        DatagramPacket filenamePacket = new DatagramPacket(filenameBytes, filenameBytes.length);
        receiverSocket.receive(filenamePacket);
        senderAddress = filenamePacket.getAddress();
        System.out.println(senderAddress);
        filename = new String(filenameBytes).trim();
        System.out.println(filename);
        fileOutputStream = new FileOutputStream(new File(filename));
    }
    
    
    private void exchangeFileSize() throws IOException {
    File file = new File(filename);
    fileSize = file.length();
    byte[] fileSizeBytes = ByteBuffer.allocate(8).putLong(fileSize).array();
    DatagramPacket fileSizePacket = new DatagramPacket(fileSizeBytes, fileSizeBytes.length);
    fileSizePacket.setAddress(senderAddress); 
    fileSizePacket.setPort(SENDER_PORT); 
    receiverSocket.send(fileSizePacket);
}
     private void processPacket(Packet packet) throws IOException {
        if (packet.isLastPacket()) {
            isTransferComplete = true;
        }

        fileOutputStream.write(packet.getData(), 0, packet.getDataLength());

        sendAcknowledgment(packet.getSeqNum());
    }

    private void sendAcknowledgment(int seqNum) throws IOException {
        byte[] ackBytes = ByteBuffer.allocate(4).putInt(seqNum).array();
        DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length, senderAddress,
                SENDER_PORT);
        receiverSocket.send(ackPacket);
    }
    
    
    public static void main(String[] args) throws IOException, Exception {
        
        Receive_UDP receiver = new Receive_UDP();
            receiver.start();
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

package TFTPServer;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class TFTPClient {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Please key in the IP to download (If your server is local, key in 127.0.0.1): ");
        String g_server_ip = scanner.nextLine();

        System.out.print("Please key in the file's name (ex: Test.txt): ");
        String g_downloadFileName = scanner.nextLine();

        System.out.println(g_server_ip + " " + g_downloadFileName);

        try {
            // Packing request
            byte[] filenameBytes = g_downloadFileName.getBytes("ASCII");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Opcode 1 (Read request) + filename + 0 + mode ("octet") + 0
            outputStream.write(ByteBuffer.allocate(2).putShort((short) 1).array()); // Opcode 1
            outputStream.write(filenameBytes); // Filename
            outputStream.write(0); // Null terminator
            outputStream.write("octet".getBytes("ASCII")); // Mode
            outputStream.write(0); // Null terminator
            byte[] sendDataFirst = outputStream.toByteArray();

            // Create a UDP socket
            DatagramSocket socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName(g_server_ip);

            // Send download file request to server
            DatagramPacket sendPacket = new DatagramPacket(sendDataFirst, sendDataFirst.length, serverAddress, 69);
            socket.send(sendPacket);
            System.out.println("sent"+g_server_ip + " " + g_downloadFileName);

            boolean downloadFlag = true;
            int fileNum = 0;

            // Open file for writing
            FileOutputStream fileOutput = new FileOutputStream(g_downloadFileName);

            while (true) {
                // Receive response
                byte[] recvBuffer = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
                socket.receive(recvPacket);

                byte[] recvData = recvPacket.getData();
                int recvLength = recvPacket.getLength();

                // Unpack opcode and block number
                ByteBuffer recvBufferWrapper = ByteBuffer.wrap(recvData);
                short packetOpt = recvBufferWrapper.getShort(); // Opcode
                short packetNum = recvBufferWrapper.getShort(); // Block number

                if (packetOpt == 3) { // DATA opcode
                    fileNum++;

                    // Check block number
                    if (fileNum == packetNum) {
                        fileOutput.write(recvData, 4, recvLength - 4); // Write data to file
                    }

                    // Send ACK packet
                    ByteBuffer ackBuffer = ByteBuffer.allocate(4);
                    ackBuffer.putShort((short) 4); // ACK opcode
                    ackBuffer.putShort(packetNum); // Block number
                    byte[] ackData = ackBuffer.array();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, recvPacket.getAddress(), recvPacket.getPort());
                    socket.send(ackPacket);
                } else if (packetOpt == 5) { // ERROR opcode
                    System.out.println("Sorry, there is no such file!");
                    downloadFlag = false;
                    break;
                } else {
                    System.out.println("Unknown opcode: " + packetOpt);
                    break;
                }

                // Check if transfer is complete
                if (recvLength < 516) {
                    downloadFlag = true;
                    System.out.printf("%s File download completed!%n", g_downloadFileName);
                    break;
                }
            }

            fileOutput.close();
            if (!downloadFlag) {
                new File(g_downloadFileName).delete();
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
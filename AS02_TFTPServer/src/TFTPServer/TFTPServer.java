/*李芷勻 113356029 資管碩一*/
package TFTPServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class TFTPServer {

	private static final int BUFFERSIZE = 512;

	private static final short RRQ = 1;
	private static final short WRQ = 2;
	private static final short DATA = 3;
	private static final short ACK = 4;
	private static final short ERRO = 5;

	public static String mode;

	public static void main(String[] args) {

		try {
			TFTPServer tftpServer = new TFTPServer();
			tftpServer.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

private void start() throws SocketException {
	    byte[] buffer = new byte[BUFFERSIZE];

	    /* Create socket */
	    DatagramSocket serverSocket = new DatagramSocket(null);

	    /* Bind to local address and port */
	    serverSocket.bind(new InetSocketAddress("127.0.0.1", 6969));

	    System.out.printf("Listening at port %d for new requests\n", 6969);

	    while (true) {
	        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
	        System.out.printf("Waiting for incoming requests...\n");

	        try {
	            // 接收請求封包
	            serverSocket.receive(packet);
	            System.out.printf("Packet received\n");
	        } catch (IOException e) {
	            e.printStackTrace();
	            break;
	        }

	        // 提取封包資訊
	        final InetSocketAddress clientSocketAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
	        final ByteBuffer bufwrap = ByteBuffer.wrap(buffer);
	        final short opcode = bufwrap.getShort(0);
	        final String fileName = get(bufwrap, 2, (byte) 0);
	        final String mode = get(bufwrap, 2 + fileName.length() + 1, (byte) 0);

	        System.out.printf("Received connection from %s:%d\n", packet.getAddress(), packet.getPort());
	        System.out.printf("Opcode=%d, Mode=%s, FileName=%s\n", opcode, mode, fileName);

	        if (mode.compareTo("octet") == 0) {
	            // 啟動新執行緒處理請求
	            new Thread(() -> {
	                try {
	                    DatagramSocket clientSocket = new DatagramSocket();
	                    clientSocket.connect(clientSocketAddress);

	                    switch (opcode) {
	                        case RRQ:
	                            download(clientSocket, clientSocketAddress, fileName, RRQ);
	                            break;
	                        case WRQ:
	                            upload(clientSocket, clientSocketAddress, fileName, WRQ);
	                            break;
	                        default:
	                            System.err.println("Unsupported opcode: " + opcode);
	                            sendError(clientSocket, (short) 4, "Illegal TFTP operation");
	                    }

	                    clientSocket.close();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }).start();
	        } else {
	            System.err.println("Unsupported transfer mode: " + mode);
	            continue;
	        }
	    }

	    serverSocket.close();
	}

	//取得封包內容
	private String get(ByteBuffer buf, int current, byte del) {
		StringBuffer sb = new StringBuffer();
		while (buf.get(current) != del) {
			sb.append((char) buf.get(current));
			current++;
		}
		return sb.toString();
	}

	private boolean isAck(ByteBuffer buf, short blocknum) {
		short op = buf.getShort();
		short block = buf.getShort(2);
		return (op == (short) ACK && block == blocknum);
	}

	private void download(DatagramSocket sendSocket, InetSocketAddress clientAddress, String fileName, int opcode) {
		// RRQ -> data -> ack
		//檔案夾目錄是專案下的 src	
		File file = new File(fileName);
		byte[] buffer = new byte[BUFFERSIZE];
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			System.err.println("File not found.");
			sendError(sendSocket, (short) 1, "File not found.");
			return;
		}

		short blockNum = 1;
		int length;

		while (true) {

			try {
				length = in.read(buffer);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}

			if (length == -1) {
				length = 0;
			}

			DatagramPacket packet = toData(blockNum, buffer, length);
			try {
				packet.setSocketAddress(clientAddress);
				sendSocket.send(packet);

				byte[] recvbuf = new byte[BUFFERSIZE];
				DatagramPacket recv = new DatagramPacket(recvbuf, BUFFERSIZE);
				sendSocket.receive(recv);
				ByteBuffer recvbytebuf = ByteBuffer.wrap(recvbuf);
				if (!isAck(recvbytebuf, blockNum)) {
					System.err.println("Error transferring file.");																				
					break;
				}
	
				blockNum += 1;
				// 讓 blockNum 在 0~65535 間循環
				blockNum  = (short) (65536%blockNum);
				if (length < 512) {
					in.close();
					try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}

	private void upload(DatagramSocket sendSocket, InetSocketAddress clientAddress, String fileName, int opcode) {
		// WRQ -> ack -> data
		
		// 建立 file
		File file = new File(fileName);
		if (file.exists()) {
			System.out.println("File already exists.");
			sendError(sendSocket, (short) 6, "File already exists.");
			return;
		} else {
			FileOutputStream output = null;
			try {
				output = new FileOutputStream(file);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				sendError(sendSocket, (short) 1, "File not found.");
				return;
			}
			//WRQ 是從 ack=0 開始
			short blockNum=0;
			try {
				// 開始接收data
			
					// 空 buffer 
					byte[] recvbuf = new byte[BUFFERSIZE + 4];
					// new 收到的資料 pkt（空）
					DatagramPacket recv = new DatagramPacket(recvbuf, BUFFERSIZE + 4);
					
				while (true) {
					try {
					//回應 WRQ ack block = 0
					DatagramPacket ackPacket = toAck((short) blockNum);
					// 固定 Client端地址
					ackPacket.setSocketAddress(clientAddress); 
					// 發送 ack
					sendSocket.send(ackPacket);
					System.out.println("ACK " +blockNum+" sent\n");
					sendSocket.receive(recv);

					} catch (IOException e) {
						e.printStackTrace();
						System.out.println(e);
						break;
					}
					// 將收到的資料片段存進 buffer
					ByteBuffer recvbytebuffer = ByteBuffer.wrap(recvbuf);
					
					//從 recvbytebuffer 中的起始位置提取前兩個字節
					if (recvbytebuffer.getShort() == (short) 3) {
						
						//取第 3,4 個字節
						blockNum = recvbytebuffer.getShort();
						
						//將檔案下載至目錄
						output.write(recvbuf, 4, recv.getLength() - 4);
						System.out.println("blocknum:"+blockNum);
						
						// 回傳該 block 的 ack
//						sendSocket.send(toAck((short) blockNum));
//						System.out.println("ACK " +blockNum+" sent\n");
						if (recv.getLength() - 4 < 512) {
							System.out.println("End of Data");
							output.close();
							sendSocket.close();
							break;
						}
					}else {
						System.out.println("It is not a data pkt");

					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * sendError
	 * 
	 * Sends an error packet to sendSocket
	 * 
	 * @param sendSocket
	 * @param errorCode
	 * @param errMsg
	 */
	private void sendError(DatagramSocket sendSocket, short errorCode, String errMsg) {

		ByteBuffer wrap = ByteBuffer.allocate(BUFFERSIZE);
		wrap.putShort(ERRO);
		wrap.putShort(errorCode);
		wrap.put(errMsg.getBytes());
		wrap.put((byte) 0);

		DatagramPacket receivePacket = new DatagramPacket(wrap.array(), wrap.array().length);
		try {
			sendSocket.send(receivePacket);
		} catch (IOException e) {
			System.err.println("Problem sending error packet.");
			e.printStackTrace();
		}
	}

	/**
	 * ackPacket
	 * 
	 * Constructs an ACK packet for the given block number.
	 * 
	 * @param block the current block number
	 * @return ackPacket
	 */
	private DatagramPacket toAck(short block) {

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putShort(ACK);
		buffer.putShort(block);
		
		System.out.println("ACK "+block+" prepared\n");
		return new DatagramPacket(buffer.array(), 4);
	}

	/**
	 * dataPacket
	 * 
	 * Constructs an DATA packet
	 * 
	 * @param block  current block number
	 * @param data   data to be sent
	 * @param length length of data
	 * @return DatagramPacket to be sent
	 */
	private DatagramPacket toData(short block, byte[] data, int length) {

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE + 4);
		buffer.putShort(DATA);
		buffer.putShort(block);
		buffer.put(data, 0, length);

		return new DatagramPacket(buffer.array(), 4 + length);
	}
}

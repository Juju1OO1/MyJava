package Echo;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/* create a EchoServer class*/
public class EchoServerPrototype {

	public static void main(String[] args) {
		
		try {
			/* socket parameters */
			String myHostName = "127.0.0.1";
			int myPortNumber = 8888;
		
			System.out.println("Waiting for clients...");
			
			/* Create a serversocket instance. */
			ServerSocket serverSocket = new ServerSocket();
			
			/* Let the OS bind the created ServerSocket by using user-specified parameters. */
			/* The 1st parameter is the binded IP, and the 2nd parameter is the binded port number. */
			/* In java, there is no need to call listen(). */
			serverSocket.bind(new InetSocketAddress(myHostName, myPortNumber));
			
			/* While loop 讓 server 一直保持在 listen 狀態，隨時服務新client */
			while(true) {
				/* Sever waits for accepting a request from a client. */
				Socket clientSocket = serverSocket.accept();
				System.out.println("Connection established!");
			/* 有新 client 加入則啟動 new thread */
				new Thread(()->{
					try {
						String str = "";
						/* 若 client 輸入 close 則結束 */
						while(!str.equals("close")) {
							BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));			
							str = in.readLine();
							if(str != null) {
								System.out.println("Client Sent: " + str);
								PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
								out.println("Server recieved: " + str);
							}
						}
						clientSocket.close();
					}
					catch(IOException e) {
						e.printStackTrace();
					}
				}).start();
			}
		} 
		/* Exception handling. */
		catch (Exception e) {
			e.printStackTrace();
		} 

	}

}
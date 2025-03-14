/*李芷勻 113356029 資管碩一*/
package Echo;

import java.io.*;
import java.net.*;

public class EchoServerHTTP {

    public static void main(String[] args) {
        String myHostName = "127.0.0.1";
        int myPortNumber = 8888;

        System.out.println("Waiting for clients...");

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(myHostName, myPortNumber));

            /* While loop 讓 server 一直保持在 listen 狀態，隨時服務新client */
            while (true) {
                Socket clientSocket = serverSocket.accept();
    			/* 有新 client 加入則啟動 new thread */
                new Thread(() -> handleHttpRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleHttpRequest(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // 讀取 HTTP 請求
            String requestLine = in.readLine();
            if (requestLine == null) return;
            System.out.println("Received request: " + requestLine);

            // HTTP 請求內容分割
            String[] requestParts = requestLine.split(" ");
            String path = requestParts[1];
            
            // 根據 request 回應不同內容
            switch (path) {
                case "/good.html":
                    sendHttpResponse(out, 200, "OK", "<html><head><link href=\"style.css\" rel=\"stylesheet\" type=\"text/css\"></head><body>good</body></html>", "text/html");
                    break;
                case "/style.css":
                    sendHttpResponse(out, 200, "OK", "body {color: red;}", "text/css");
                    break;
                case "/redirect.html":
                    sendRedirectResponse(out, "http://127.0.0.1:8888/good.html");
                    break;
                case "/notfound.html":
                    sendHttpResponse(out, 404, "Not Found", "<html><body><h1>Page Not Found</h1></body></html>", "text/html");
                    break;
                default:
                    sendHttpResponse(out, 404, "Not Found", "<html><body><h1>Page Not Found</h1></body></html>", "text/html");
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    
    /* HTTP 狀態碼與 Header 的 class 方便取用 */
    private static void sendHttpResponse(PrintWriter out, int statusCode, String statusText, String body, String contentType) {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                          "Content-Type: " + contentType + "; charset=UTF-8\r\n" +
                          "Content-Length: " + body.length() + "\r\n" +
                          "Connection: close\r\n" +
                          "\r\n" +
                          body;
        out.print(response);
        out.flush();
    }
    /* HTTP  redirect 狀態碼與 Header 的 class （Location）*/
    private static void sendRedirectResponse(PrintWriter out, String location) {
        String response = "HTTP/1.1 301 Moved Permanently\r\n" +
                          "Location: " + location + "\r\n" +
                          "Content-Length: 0\r\n" +
                          "Connection: close\r\n" +
                          "\r\n";
        out.print(response);
        out.flush();
    }
}

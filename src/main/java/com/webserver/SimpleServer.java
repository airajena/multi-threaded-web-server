// src/main/java/com/webserver/SimpleServer.java
package com.webserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class SimpleServer {
    private final int port;

    public SimpleServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("🚀 Server started on http://localhost:" + port);
            System.out.println("📝 Try: http://localhost:" + port + "/hello");

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("👤 New connection!");

                handleClient(client);
            }
        } catch (Exception e) {
            System.err.println("💥 Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter output = new PrintWriter(client.getOutputStream())) {

            // Read first line only: GET /hello HTTP/1.1
            String requestLine = input.readLine();
            if (requestLine == null) return;

            System.out.println("📨 Request: " + requestLine);

            // Parse request (simple way - no arrays causing problems!)
            String method = "GET";  // Default
            String path = "/";      // Default

            if (requestLine.contains(" ")) {
                int firstSpace = requestLine.indexOf(' ');
                int secondSpace = requestLine.indexOf(' ', firstSpace + 1);

                if (firstSpace > 0) {
                    method = requestLine.substring(0, firstSpace);
                }
                if (secondSpace > firstSpace) {
                    path = requestLine.substring(firstSpace + 1, secondSpace);
                }
            }

            Request request = new Request(method, path);
            Response response = createResponse(request);

            System.out.println("📤 Sending response for: " + path);
            output.print(response.toHttpString());
            output.flush();

        } catch (Exception e) {
            System.err.println("❌ Client error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private Response createResponse(Request request) {
        String path = request.getPath();

        if (path.equals("/") || path.equals("/hello")) {
            String html = "<html><body>" +
                    "<h1>🎉 Welcome to My Web Server!</h1>" +
                    "<p>Your server is working perfectly!</p>" +
                    "<ul>" +
                    "<li><a href='/hello'>Hello Page</a></li>" +
                    "<li><a href='/time'>Current Time</a></li>" +
                    "</ul>" +
                    "</body></html>";
            return new Response(200, html);

        } else if (path.equals("/time")) {
            String html = "<html><body>" +
                    "<h1>⏰ Current Time</h1>" +
                    "<p>Time: " + new java.util.Date() + "</p>" +
                    "<a href='/hello'>← Back</a>" +
                    "</body></html>";
            return new Response(200, html);

        } else {
            String html = "<html><body><h1>404 - Not Found</h1></body></html>";
            return new Response(404, html);
        }
    }
}

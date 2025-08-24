// src/main/java/com/webserver/ConnectionHandler.java
package com.webserver;

import java.io.*;
import java.net.Socket;

/**
 * Handles a single client connection in its own thread
 * Each instance is like one waiter serving one table
 */
public class ConnectionHandler implements Runnable {
    private final Socket clientSocket;
    private final RequestProcessor requestProcessor;
    private final long connectionId;

    public ConnectionHandler(Socket clientSocket, RequestProcessor requestProcessor, long connectionId) {
        this.clientSocket = clientSocket;
        this.requestProcessor = requestProcessor;
        this.connectionId = connectionId;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        System.out.println("🔄 [" + threadName + "] Handling connection #" + connectionId);

        try (
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                );
                PrintWriter output = new PrintWriter(
                        clientSocket.getOutputStream(), true
                )
        ) {
            // Record start time to measure how long this takes
            long startTime = System.currentTimeMillis();

            // Parse the HTTP request
            Request request = parseRequest(input);

            if (request != null) {
                System.out.println("📨 [" + threadName + "] Processing: " + request);

                // Process the request (this is where our business logic lives)
                Response response = requestProcessor.processRequest(request);

                // Send response back to client
                output.print(response.toHttpString());
                output.flush();

                // Log performance metrics
                long processingTime = System.currentTimeMillis() - startTime;
                System.out.println("✅ [" + threadName + "] Completed connection #" + connectionId +
                        " in " + processingTime + "ms");
            } else {
                System.out.println("⚠️  [" + threadName + "] Invalid request on connection #" + connectionId);
            }

        } catch (IOException e) {
            System.err.println("❌ [" + threadName + "] Error handling connection #" + connectionId +
                    ": " + e.getMessage());
        } finally {
            // Always close the client socket
            try {
                clientSocket.close();
                System.out.println("🔌 [" + threadName + "] Closed connection #" + connectionId);
            } catch (IOException e) {
                System.err.println("❌ Error closing socket: " + e.getMessage());
            }
        }
    }

    /**
     * Parse HTTP request from client
     * Same logic as before, but now in its own thread!
     */
    private Request parseRequest(BufferedReader input) throws IOException {
        String requestLine = input.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            return null;
        }

        // Parse: "GET /hello HTTP/1.1"
        String method = "GET";
        String path = "/";

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

        // Skip headers for now (we'll add this back later)
        String line;
        while ((line = input.readLine()) != null && !line.trim().isEmpty()) {
            // Just read and ignore headers for now
        }

        return new Request(method, path);
    }
}

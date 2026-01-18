package com.webserver;

import java.io.*;
import java.net.Socket;

/**
 * Handles a single client connection in a worker thread.
 * 
 * @author Airaj Jena
 */
public class ConnectionHandler implements Runnable {
    
    private final Socket clientSocket;
    private final RequestProcessor requestProcessor;
    private final long connectionId;
    private final Runnable onComplete;
    
    public ConnectionHandler(Socket clientSocket, RequestProcessor requestProcessor, 
                            long connectionId, Runnable onComplete) {
        this.clientSocket = clientSocket;
        this.requestProcessor = requestProcessor;
        this.connectionId = connectionId;
        this.onComplete = onComplete;
    }
    
    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();
        
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
             OutputStream output = clientSocket.getOutputStream()) {
            
            Request request = parseRequest(input);
            
            if (request != null) {
                Response response = requestProcessor.processRequest(request);
                
                // Send response
                output.write(response.toHttpString().getBytes());
                if (response.getBody().length > 0) {
                    output.write(response.getBody());
                }
                output.flush();
                
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("✅ [" + threadName + "] Connection #" + connectionId + 
                    " completed in " + duration + "ms");
            }
            
        } catch (IOException e) {
            System.err.println("❌ [" + threadName + "] Error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
            
            // Notify completion
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
    
    private Request parseRequest(BufferedReader input) throws IOException {
        String requestLine = input.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "GET";
        String path = parts.length > 1 ? parts[1] : "/";
        
        Request request = new Request(method, path);
        
        // Parse headers
        String line;
        while ((line = input.readLine()) != null && !line.trim().isEmpty()) {
            if (line.contains(":")) {
                int colonIndex = line.indexOf(':');
                String headerName = line.substring(0, colonIndex).trim();
                String headerValue = line.substring(colonIndex + 1).trim();
                request.addHeader(headerName, headerValue);
            }
        }
        
        // Read body for POST/PUT requests
        if (("POST".equals(method) || "PUT".equals(method)) && request.getContentLength() > 0) {
            StringBuilder bodyBuilder = new StringBuilder();
            int contentLength = request.getContentLength();
            for (int i = 0; i < contentLength; i++) {
                int ch = input.read();
                if (ch == -1) break;
                bodyBuilder.append((char) ch);
            }
            request.setBody(bodyBuilder.toString());
        }
        
        return request;
    }
}

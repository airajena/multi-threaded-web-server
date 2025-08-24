// src/main/java/com/webserver/MultiThreadedServer.java
package com.webserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded HTTP server that can handle multiple requests simultaneously
 * This is our restaurant with multiple waiters!
 */
public class MultiThreadedServer {
    private final int port;
    private final ThreadPoolManager threadPool;
    private final RequestProcessor requestProcessor;
    private final AtomicLong connectionCounter; // Thread-safe counter
    private volatile boolean isRunning = false; // volatile = visible to all threads

    public MultiThreadedServer(int port, int threadCount) {
        this.port = port;
        this.threadPool = new ThreadPoolManager(threadCount);
        this.requestProcessor = new RequestProcessor();
        this.connectionCounter = new AtomicLong(0);

        System.out.println("🏗️  Multi-threaded server created");
        System.out.println("📍 Port: " + port);
        System.out.println("🧵 Thread pool size: " + threadCount);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            isRunning = true;

            System.out.println("🚀 Multi-threaded server started on http://localhost:" + port);
            System.out.println("📝 Try these URLs:");
            System.out.println("   • http://localhost:" + port + "/hello");
            System.out.println("   • http://localhost:" + port + "/time");
            System.out.println("   • http://localhost:" + port + "/stats");
            System.out.println("   • http://localhost:" + port + "/slow");
            System.out.println("⏹️  Press Ctrl+C to stop");
            System.out.println();

            // Main server loop - accepts connections and delegates to threads
            while (isRunning) {
                try {
                    // Accept a new client connection
                    Socket clientSocket = serverSocket.accept();

                    // Generate unique connection ID
                    long connectionId = connectionCounter.incrementAndGet();

                    System.out.println("🔗 New connection #" + connectionId + " from " +
                            clientSocket.getRemoteSocketAddress());

                    // Create a handler for this connection
                    ConnectionHandler handler = new ConnectionHandler(
                            clientSocket,
                            requestProcessor,
                            connectionId
                    );

                    // Submit to thread pool for processing
                    // This is where the magic happens - no blocking!
                    threadPool.execute(handler);

                    System.out.println("📤 Connection #" + connectionId + " submitted to thread pool");

                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("❌ Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("💥 Failed to start server: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        if (isRunning) {
            System.out.println("\n🛑 Stopping server...");
            isRunning = false;

            // Shutdown thread pool gracefully
            threadPool.shutdown();

            System.out.println("📊 Final stats:");
            System.out.println("   • Total connections handled: " + connectionCounter.get());
            System.out.println("   • Total requests processed: " + requestProcessor.getRequestCount());
            System.out.println("✅ Server stopped successfully");
        }
    }
}

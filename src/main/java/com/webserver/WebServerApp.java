// src/main/java/com/webserver/WebServerApp.java
package com.webserver;

public class WebServerApp {
    public static void main(String[] args) {
        System.out.println("🌟 Starting Multi-Threaded Web Server...");

        // Configuration with defaults
        int port = 8080;
        int threadCount = 10;

        // Parse command line arguments if provided
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]); // ✅ CORRECT: args not args
            } catch (NumberFormatException e) {
                System.err.println("❌ Invalid port: " + args);
                System.err.println("💡 Usage: java WebServerApp [port] [threads]");
                return;
            }
        }

        if (args.length > 1) {
            try {
                threadCount = Integer.parseInt(args[1]); // ✅ CORRECT: args[1] not args
            } catch (NumberFormatException e) {
                System.err.println("❌ Invalid thread count: " + args[1]);
                System.err.println("💡 Usage: java WebServerApp [port] [threads]");
                return;
            }
        }

        System.out.println("📍 Port: " + port);
        System.out.println("🧵 Threads: " + threadCount);

        // Create and start the server
        MultiThreadedServer server = new MultiThreadedServer(port, threadCount);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🚨 Shutdown signal received");
            server.stop();
        }));

        // Start the server (this blocks until server stops)
        server.start();
    }
}

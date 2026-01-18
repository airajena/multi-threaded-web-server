package com.webserver;

import com.webserver.io.NioEventLoop;

/**
 * Entry point for the Multi-Threaded Web Server.
 * 
 * Demonstrates:
 * - Custom Thread Pool (not Executors)
 * - Token Bucket Rate Limiter
 * - LRU Cache with Read-Write Lock
 * - Circuit Breaker
 * - Async Request Processing
 * - NIO Event Loop (optional)
 * - Graceful Shutdown
 * 
 * @author Airaj Jena
 */
public class WebServerApp {
    
    public static void main(String[] args) {
        int port = 8080;
        int threadCount = 10;
        boolean useNio = false;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p", "--port" -> {
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                }
                case "-t", "--threads" -> {
                    if (i + 1 < args.length) {
                        threadCount = Integer.parseInt(args[++i]);
                    }
                }
                case "--nio" -> useNio = true;
                case "-h", "--help" -> {
                    printUsage();
                    return;
                }
            }
        }
        
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════════╗
            ║       Multi-Threaded Web Server - Concurrency Showcase        ║
            ║                                                               ║
            ║   Demonstrating Low-Level Multithreading Skills:              ║
            ║   • Custom Thread Pool (no Executors)                         ║
            ║   • Blocking Queue with wait/notify                           ║
            ║   • Token Bucket Rate Limiter                                 ║
            ║   • LRU Cache with Custom Read-Write Lock                     ║
            ║   • Circuit Breaker Pattern                                   ║
            ║   • Async Request Processing (CompletableFuture)              ║
            ║   • NIO Event Loop with Selector                              ║
            ║   • Graceful Shutdown with Request Draining                   ║
            ╚═══════════════════════════════════════════════════════════════╝
            """);
        
        if (useNio) {
            startNioServer(port);
        } else {
            startThreadPoolServer(port, threadCount);
        }
    }
    
    private static void startThreadPoolServer(int port, int threadCount) {
        MultiThreadedServer server = new MultiThreadedServer(port, threadCount);
        
        // Register shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🚨 Shutdown signal received (Ctrl+C)");
            server.stop();
        }, "ShutdownHook"));
        
        server.start();
    }
    
    private static void startNioServer(int port) {
        try {
            NioEventLoop eventLoop = new NioEventLoop(port, connection -> {
                System.out.println("📥 [NIO] New connection: " + connection);
            });
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🚨 Shutdown signal received (Ctrl+C)");
                eventLoop.stop();
            }, "ShutdownHook"));
            
            // Run event loop on main thread
            eventLoop.run();
            
        } catch (Exception e) {
            System.err.println("💥 Failed to start NIO server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void printUsage() {
        System.out.println("""
            Usage: java -jar webserver.jar [options]
            
            Options:
              -p, --port <port>      Server port (default: 8080)
              -t, --threads <count>  Thread pool size (default: 10)
              --nio                  Use NIO event loop instead of thread pool
              -h, --help             Show this help message
            
            Examples:
              java -jar webserver.jar -p 8080 -t 20
              java -jar webserver.jar --nio
            
            Demo Endpoints:
              /demo/threadpool  - Thread pool statistics
              /demo/ratelimit   - Rate limiter status
              /demo/cache       - LRU cache operations
              /demo/circuit     - Circuit breaker status
              /demo/async       - Async processing demo
              /demo/all         - All statistics
            """);
    }
}

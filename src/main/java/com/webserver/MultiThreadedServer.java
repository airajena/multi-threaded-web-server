package com.webserver;

import com.webserver.async.AsyncRequestProcessor;
import com.webserver.cache.LRUCache;
import com.webserver.concurrency.CustomReadWriteLock;
import com.webserver.concurrency.RequestTimeoutHandler;
import com.webserver.concurrency.TokenBucketRateLimiter;
import com.webserver.core.CustomThreadPool;
import com.webserver.core.RejectionPolicy;
import com.webserver.io.ConnectionPool;
import com.webserver.io.NioEventLoop;
import com.webserver.resilience.CircuitBreaker;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A multi-threaded HTTP server demonstrating advanced concurrency patterns.
 * 
 * This server uses a custom-built thread pool (not Java's Executors) and
 * integrates multiple concurrency primitives for demonstration.
 * 
 * Features demonstrated:
 * - Custom Thread Pool with blocking queue and rejection policies
 * - Token Bucket Rate Limiting
 * - Read-Write Lock protected resources
 * - LRU Cache with concurrent access
 * - Circuit Breaker for fault tolerance
 * - Async request processing with CompletableFuture
 * - Request timeout handling
 * - Graceful shutdown with request draining
 * 
 * @author Airaj Jena
 */
public class MultiThreadedServer {
    
    private final int port;
    private final CustomThreadPool threadPool;
    private final RequestProcessor requestProcessor;
    private final AtomicLong connectionCounter = new AtomicLong(0);
    
    // Concurrency primitives for demo
    private final TokenBucketRateLimiter rateLimiter;
    private final LRUCache<String, String> cache;
    private final CircuitBreaker circuitBreaker;
    private final RequestTimeoutHandler timeoutHandler;
    private final ConnectionPool connectionPool;
    private final AsyncRequestProcessor asyncProcessor;
    
    private volatile boolean isRunning = false;
    private ServerSocket serverSocket;
    
    // Shutdown coordination
    private final AtomicLong activeRequests = new AtomicLong(0);
    private final Object shutdownLock = new Object();
    
    /**
     * Creates a new multi-threaded server with all concurrency features.
     */
    public MultiThreadedServer(int port, int threadCount) {
        this.port = port;
        
        // Initialize custom thread pool (NOT using Executors!)
        this.threadPool = new CustomThreadPool(
            threadCount,           // Core pool size
            threadCount * 2,       // Max pool size
            1000,                  // Queue capacity
            60000,                 // Keep-alive time (ms)
            RejectionPolicy.CALLER_RUNS  // Rejection policy
        );
        
        // Initialize concurrency primitives
        this.rateLimiter = new TokenBucketRateLimiter(100, 50); // 100 burst, 50/sec
        this.cache = new LRUCache<>(1000); // 1000 entry cache
        this.circuitBreaker = new CircuitBreaker("ExternalService", 5, 30000);
        this.timeoutHandler = new RequestTimeoutHandler(30000); // 30 sec timeout
        this.connectionPool = new ConnectionPool(10, 5000, 60000);
        this.asyncProcessor = new AsyncRequestProcessor(
            runnable -> threadPool.execute(runnable)
        );
        
        // Initialize request processor with all primitives
        this.requestProcessor = new RequestProcessor(
            rateLimiter, cache, circuitBreaker, threadPool, asyncProcessor
        );
        
        System.out.println("🏗️ MultiThreadedServer initialized:");
        System.out.println("   Port: " + port);
        System.out.println("   Thread Pool: " + threadCount + " core threads");
        System.out.println("   Rate Limiter: 100 burst, 50/sec");
        System.out.println("   Cache: 1000 entries");
        System.out.println("   Circuit Breaker: 5 failures, 30s reset");
    }
    
    /**
     * Starts the server and begins accepting connections.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            
            printStartupBanner();
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    long connectionId = connectionCounter.incrementAndGet();
                    
                    // Rate limit check
                    if (!rateLimiter.tryAcquire()) {
                        System.out.println("🚫 Connection #" + connectionId + " rate limited");
                        sendRateLimitResponse(clientSocket);
                        clientSocket.close();
                        continue;
                    }
                    
                    System.out.println("🔗 Connection #" + connectionId + 
                        " from " + clientSocket.getRemoteSocketAddress());
                    
                    // Schedule timeout
                    timeoutHandler.scheduleTimeout(connectionId, () -> {
                        System.out.println("⏰ Connection #" + connectionId + " timed out");
                    });
                    
                    // Track active requests
                    activeRequests.incrementAndGet();
                    
                    // Submit to thread pool
                    ConnectionHandler handler = new ConnectionHandler(
                        clientSocket, requestProcessor, connectionId,
                        () -> {
                            timeoutHandler.cancelTimeout(connectionId);
                            if (activeRequests.decrementAndGet() == 0 && !isRunning) {
                                synchronized (shutdownLock) {
                                    shutdownLock.notifyAll();
                                }
                            }
                        }
                    );
                    
                    threadPool.execute(handler);
                    
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
    
    /**
     * Sends a 429 Too Many Requests response.
     */
    private void sendRateLimitResponse(Socket socket) {
        try {
            String response = "HTTP/1.1 429 Too Many Requests\r\n" +
                "Content-Type: application/json\r\n" +
                "Retry-After: 1\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                "{\"error\": \"Rate limit exceeded\", \"retryAfter\": 1}";
            socket.getOutputStream().write(response.getBytes());
            socket.getOutputStream().flush();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    /**
     * Gracefully stops the server, allowing active requests to complete.
     */
    public void stop() {
        if (!isRunning) return;
        
        System.out.println("\n🛑 Stopping server...");
        isRunning = false;
        
        // Close server socket to stop accepting new connections
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("❌ Error closing server socket: " + e.getMessage());
        }
        
        // Wait for active requests to complete (graceful shutdown)
        System.out.println("⏳ Waiting for " + activeRequests.get() + " active requests to complete...");
        synchronized (shutdownLock) {
            long deadline = System.currentTimeMillis() + 30000; // 30 sec grace period
            while (activeRequests.get() > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    System.out.println("⚠️ Grace period expired, forcing shutdown");
                    break;
                }
                try {
                    shutdownLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // Shutdown all components
        threadPool.shutdown();
        timeoutHandler.shutdown();
        connectionPool.shutdown();
        
        // Wait for thread pool termination
        try {
            threadPool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Print final statistics
        printShutdownStats();
    }
    
    private void printStartupBanner() {
        System.out.println("\n" +
            "╔═══════════════════════════════════════════════════════════════╗\n" +
            "║   🚀 Multi-Threaded Web Server - Concurrency Showcase         ║\n" +
            "╠═══════════════════════════════════════════════════════════════╣\n" +
            "║   http://localhost:" + port + "                                      ║\n" +
            "╠═══════════════════════════════════════════════════════════════╣\n" +
            "║   Demo Endpoints:                                             ║\n" +
            "║   • /demo/threadpool  - Thread pool statistics                ║\n" +
            "║   • /demo/ratelimit   - Test rate limiting                    ║\n" +
            "║   • /demo/cache       - LRU cache operations                  ║\n" +
            "║   • /demo/circuit     - Circuit breaker status                ║\n" +
            "║   • /demo/async       - Async request processing              ║\n" +
            "║   • /demo/all         - All concurrency stats                 ║\n" +
            "╠═══════════════════════════════════════════════════════════════╣\n" +
            "║   Press Ctrl+C to stop                                        ║\n" +
            "╚═══════════════════════════════════════════════════════════════╝\n"
        );
    }
    
    private void printShutdownStats() {
        System.out.println("\n📊 Final Statistics:");
        System.out.println("   Total connections: " + connectionCounter.get());
        System.out.println("   Thread pool: " + threadPool.getStats());
        System.out.println("   Rate limiter: " + rateLimiter.getStats());
        System.out.println("   Cache: " + cache.getStats());
        System.out.println("   Circuit breaker: " + circuitBreaker.getStats());
        System.out.println("   Connection pool: " + connectionPool.getStats());
        System.out.println("✅ Server stopped successfully");
    }
    
    // Getters for testing and monitoring
    public CustomThreadPool getThreadPool() { return threadPool; }
    public TokenBucketRateLimiter getRateLimiter() { return rateLimiter; }
    public LRUCache<String, String> getCache() { return cache; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public long getTotalConnections() { return connectionCounter.get(); }
    public boolean isRunning() { return isRunning; }
}

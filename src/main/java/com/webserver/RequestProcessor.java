package com.webserver;

import com.webserver.async.AsyncRequestProcessor;
import com.webserver.cache.LRUCache;
import com.webserver.concurrency.TokenBucketRateLimiter;
import com.webserver.core.CustomThreadPool;
import com.webserver.resilience.CircuitBreaker;
import com.webserver.resilience.CircuitOpenException;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processes HTTP requests and routes to appropriate handlers.
 * Includes demo endpoints to showcase all concurrency primitives.
 * 
 * @author Airaj Jena
 */
public class RequestProcessor {
    
    private final TokenBucketRateLimiter rateLimiter;
    private final LRUCache<String, String> cache;
    private final CircuitBreaker circuitBreaker;
    private final CustomThreadPool threadPool;
    private final AsyncRequestProcessor asyncProcessor;
    
    private final AtomicLong requestCount = new AtomicLong(0);
    
    public RequestProcessor(TokenBucketRateLimiter rateLimiter, LRUCache<String, String> cache,
                           CircuitBreaker circuitBreaker, CustomThreadPool threadPool,
                           AsyncRequestProcessor asyncProcessor) {
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.circuitBreaker = circuitBreaker;
        this.threadPool = threadPool;
        this.asyncProcessor = asyncProcessor;
    }
    
    public Response processRequest(Request request) {
        long reqId = requestCount.incrementAndGet();
        String path = request.getPath();
        
        try {
            // Route to demo endpoints
            if (path.startsWith("/demo/")) {
                return handleDemoEndpoint(path, request);
            }
            
            // Default response
            return createWelcomeResponse();
            
        } catch (Exception e) {
            System.err.println("❌ Error processing request #" + reqId + ": " + e.getMessage());
            return createJsonResponse(500, 
                "{\"error\": \"Internal Server Error\", \"message\": \"" + 
                e.getMessage().replace("\"", "'") + "\"}");
        }
    }
    
    private Response handleDemoEndpoint(String path, Request request) {
        return switch (path) {
            case "/demo/threadpool" -> demoThreadPool();
            case "/demo/ratelimit" -> demoRateLimiter();
            case "/demo/cache" -> demoCache(request);
            case "/demo/circuit" -> demoCircuitBreaker(request);
            case "/demo/async" -> demoAsync(request);
            case "/demo/all" -> demoAllStats();
            case "/demo/stress" -> demoStress();
            default -> createJsonResponse(404, "{\"error\": \"Unknown demo endpoint\"}");
        };
    }
    
    /**
     * Demo: Thread Pool Statistics
     */
    private Response demoThreadPool() {
        var stats = threadPool.getStats();
        String json = String.format("""
            {
                "component": "CustomThreadPool",
                "description": "Hand-built thread pool with custom BlockingQueue, WorkerThreads, and RejectionPolicy",
                "stats": {
                    "poolSize": %d,
                    "activeThreads": %d,
                    "queuedTasks": %d,
                    "submittedTasks": %d,
                    "completedTasks": %d,
                    "rejectedTasks": %d
                },
                "demo": "This pool is built from scratch without using Executors.newFixedThreadPool()"
            }
            """,
            stats.poolSize(), stats.activeThreads(), stats.queuedTasks(),
            stats.submittedTasks(), stats.completedTasks(), stats.rejectedTasks()
        );
        return createJsonResponse(200, json);
    }
    
    /**
     * Demo: Rate Limiter
     */
    private Response demoRateLimiter() {
        var stats = rateLimiter.getStats();
        String json = String.format("""
            {
                "component": "TokenBucketRateLimiter",
                "description": "Token bucket algorithm for rate limiting with burst support",
                "stats": {
                    "availableTokens": %.2f,
                    "maxTokens": %d,
                    "refillRate": %.2f,
                    "utilizationPercent": %.1f
                },
                "demo": "Try rapid requests: for i in {1..20}; do curl localhost:8080/demo/ratelimit; done"
            }
            """,
            stats.availableTokens(), stats.maxTokens(), 
            stats.refillRate(), stats.getUtilizationPercent()
        );
        return createJsonResponse(200, json);
    }
    
    /**
     * Demo: LRU Cache
     */
    private Response demoCache(Request request) {
        String queryString = request.getPath().contains("?") 
            ? request.getPath().substring(request.getPath().indexOf("?") + 1) 
            : "";
        
        String key = null, value = null;
        for (String param : queryString.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2) {
                if ("key".equals(kv[0])) key = kv[1];
                if ("value".equals(kv[0])) value = kv[1];
            }
        }
        
        String operation = "stats";
        String result = null;
        
        if (key != null && value != null) {
            cache.put(key, value);
            operation = "put";
            result = "Stored: " + key + " = " + value;
        } else if (key != null) {
            result = cache.get(key);
            operation = "get";
            if (result == null) result = "(not found)";
        }
        
        var stats = cache.getStats();
        String json = String.format("""
            {
                "component": "LRUCache",
                "description": "Thread-safe LRU cache with O(1) operations using CustomReadWriteLock",
                "operation": "%s",
                "result": "%s",
                "stats": {
                    "size": %d,
                    "capacity": %d,
                    "hits": %d,
                    "misses": %d,
                    "evictions": %d,
                    "hitRate": %.1f
                },
                "demo": "Try: ?key=foo&value=bar to store, ?key=foo to retrieve"
            }
            """,
            operation, result != null ? result : "",
            stats.size(), stats.capacity(), stats.hits(), 
            stats.misses(), stats.evictions(), stats.getHitRate()
        );
        return createJsonResponse(200, json);
    }
    
    /**
     * Demo: Circuit Breaker
     */
    private Response demoCircuitBreaker(Request request) {
        String queryString = request.getPath().contains("?") 
            ? request.getPath().substring(request.getPath().indexOf("?") + 1) 
            : "";
        
        boolean shouldFail = queryString.contains("fail=true");
        boolean shouldReset = queryString.contains("reset=true");
        
        String operationResult = "No operation";
        
        if (shouldReset) {
            circuitBreaker.reset();
            operationResult = "Circuit reset to CLOSED";
        } else if (shouldFail) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                });
            } catch (CircuitOpenException e) {
                operationResult = "Circuit is OPEN: " + e.getMessage();
            } catch (Exception e) {
                operationResult = "Failure recorded: " + e.getMessage();
            }
        } else {
            try {
                String result = circuitBreaker.execute(() -> "Success!");
                operationResult = "Operation succeeded: " + result;
            } catch (CircuitOpenException e) {
                operationResult = "Circuit is OPEN: " + e.getMessage();
            } catch (Exception e) {
                operationResult = "Operation failed: " + e.getMessage();
            }
        }
        
        var stats = circuitBreaker.getStats();
        String json = String.format("""
            {
                "component": "CircuitBreaker",
                "description": "Fault tolerance pattern with CLOSED/OPEN/HALF_OPEN states",
                "operation": "%s",
                "stats": {
                    "name": "%s",
                    "state": "%s",
                    "failures": %d,
                    "totalRequests": %d,
                    "rejectedRequests": %d,
                    "timeSinceStateChange": "%dms"
                },
                "demo": "Try: ?fail=true to simulate failure, ?reset=true to reset"
            }
            """,
            operationResult, stats.name(), stats.state(), stats.failures(),
            stats.totalRequests(), stats.rejectedRequests(), stats.getTimeSinceStateChangeMs()
        );
        return createJsonResponse(200, json);
    }
    
    /**
     * Demo: Async Processing
     */
    private Response demoAsync(Request request) {
        long startTime = System.currentTimeMillis();
        
        // Process asynchronously and wait for result
        try {
            Response asyncResponse = asyncProcessor.processAsync(request).get();
            long duration = System.currentTimeMillis() - startTime;
            
            String json = String.format("""
                {
                    "component": "AsyncRequestProcessor",
                    "description": "CompletableFuture-based async pipeline with timeout and retry support",
                    "stats": {
                        "processingTimeMs": %d,
                        "thread": "%s",
                        "mode": "async-pipeline"
                    },
                    "demo": "Request processed through async Parse->Validate->Execute pipeline"
                }
                """,
                duration, Thread.currentThread().getName()
            );
            return createJsonResponse(200, json);
            
        } catch (Exception e) {
            return createJsonResponse(500, 
                "{\"error\": \"Async processing failed\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Demo: All Statistics
     */
    private Response demoAllStats() {
        var poolStats = threadPool.getStats();
        var rateLimiterStats = rateLimiter.getStats();
        var cacheStats = cache.getStats();
        var circuitStats = circuitBreaker.getStats();
        
        String json = String.format("""
            {
                "server": "Multi-Threaded Web Server - Concurrency Showcase",
                "timestamp": "%s",
                "totalRequests": %d,
                "components": {
                    "threadPool": {
                        "poolSize": %d,
                        "activeThreads": %d,
                        "queuedTasks": %d,
                        "completedTasks": %d
                    },
                    "rateLimiter": {
                        "availableTokens": %.2f,
                        "maxTokens": %d,
                        "refillRate": %.2f
                    },
                    "cache": {
                        "size": %d,
                        "capacity": %d,
                        "hitRate": %.1f
                    },
                    "circuitBreaker": {
                        "state": "%s",
                        "failures": %d
                    }
                }
            }
            """,
            LocalDateTime.now(), requestCount.get(),
            poolStats.poolSize(), poolStats.activeThreads(), 
            poolStats.queuedTasks(), poolStats.completedTasks(),
            rateLimiterStats.availableTokens(), rateLimiterStats.maxTokens(), 
            rateLimiterStats.refillRate(),
            cacheStats.size(), cacheStats.capacity(), cacheStats.getHitRate(),
            circuitStats.state(), circuitStats.failures()
        );
        return createJsonResponse(200, json);
    }
    
    /**
     * Demo: Stress test endpoint
     */
    private Response demoStress() {
        // Simulate some work
        long result = 0;
        for (int i = 0; i < 100000; i++) {
            result += i;
        }
        
        String json = String.format("""
            {
                "endpoint": "/demo/stress",
                "description": "High-load endpoint for stress testing",
                "thread": "%s",
                "computation": %d,
                "tip": "Use wrk or ab for load testing: wrk -t4 -c100 -d30s http://localhost:8080/demo/stress"
            }
            """,
            Thread.currentThread().getName(), result
        );
        return createJsonResponse(200, json);
    }
    
    /**
     * Creates the welcome page response.
     */
    private Response createWelcomeResponse() {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Multi-Threaded Web Server</title>
                <style>
                    body { font-family: system-ui; max-width: 800px; margin: 50px auto; padding: 20px; }
                    h1 { color: #2563eb; }
                    .endpoint { background: #f1f5f9; padding: 15px; margin: 10px 0; border-radius: 8px; }
                    code { background: #1e293b; color: #22c55e; padding: 2px 6px; border-radius: 4px; }
                    a { color: #2563eb; }
                </style>
            </head>
            <body>
                <h1>🚀 Multi-Threaded Web Server</h1>
                <p>A concurrency showcase demonstrating low-level multithreading skills.</p>
                
                <h2>🔧 Concurrency Primitives</h2>
                <ul>
                    <li><strong>Custom Thread Pool</strong> - Built from scratch with BlockingQueue</li>
                    <li><strong>Token Bucket Rate Limiter</strong> - Thread-safe with burst support</li>
                    <li><strong>LRU Cache</strong> - With custom Read-Write Lock</li>
                    <li><strong>Circuit Breaker</strong> - Fault tolerance pattern</li>
                    <li><strong>Async Request Processor</strong> - CompletableFuture pipeline</li>
                    <li><strong>NIO Event Loop</strong> - Non-blocking Selector pattern</li>
                </ul>
                
                <h2>🎯 Demo Endpoints</h2>
                <div class="endpoint">
                    <a href="/demo/threadpool"><code>GET /demo/threadpool</code></a> - Thread pool statistics
                </div>
                <div class="endpoint">
                    <a href="/demo/ratelimit"><code>GET /demo/ratelimit</code></a> - Rate limiter status
                </div>
                <div class="endpoint">
                    <a href="/demo/cache?key=test&value=hello"><code>GET /demo/cache</code></a> - LRU cache operations
                </div>
                <div class="endpoint">
                    <a href="/demo/circuit"><code>GET /demo/circuit</code></a> - Circuit breaker status
                </div>
                <div class="endpoint">
                    <a href="/demo/async"><code>GET /demo/async</code></a> - Async processing demo
                </div>
                <div class="endpoint">
                    <a href="/demo/all"><code>GET /demo/all</code></a> - All statistics
                </div>
                
                <h2>📊 Current Thread</h2>
                <p>This request was handled by: <code>%s</code></p>
            </body>
            </html>
            """.formatted(Thread.currentThread().getName());
        
        return new Response(200, html);
    }
    
    private Response createJsonResponse(int status, String json) {
        Response response = new Response(status, json);
        response.addHeader("Content-Type", "application/json");
        return response;
    }
    
    public long getRequestCount() {
        return requestCount.get();
    }
}

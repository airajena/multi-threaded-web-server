package com.webserver.concurrency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe request timeout handler that manages timeouts without blocking threads.
 * 
 * Uses a scheduled executor to trigger timeout callbacks asynchronously.
 * This allows the main request processing threads to continue without being
 * blocked waiting for responses.
 * 
 * Key concepts demonstrated:
 * - Non-blocking timeout management
 * - Scheduled task cancellation
 * - Thread-safe timeout registry using ConcurrentHashMap
 * 
 * @author Airaj Jena
 */
public class RequestTimeoutHandler {
    
    private final ScheduledExecutorService scheduler;
    private final Map<Long, ScheduledFuture<?>> pendingTimeouts;
    private final long defaultTimeoutMs;
    
    /**
     * Creates a new timeout handler with default timeout.
     * 
     * @param defaultTimeoutMs Default timeout in milliseconds
     */
    public RequestTimeoutHandler(long defaultTimeoutMs) {
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TimeoutHandler");
            t.setDaemon(true);
            return t;
        });
        this.pendingTimeouts = new ConcurrentHashMap<>();
        this.defaultTimeoutMs = defaultTimeoutMs;
        
        System.out.println("⏱️ RequestTimeoutHandler initialized (default: " + defaultTimeoutMs + "ms)");
    }
    
    /**
     * Schedules a timeout for a request.
     * 
     * @param requestId Unique identifier for the request
     * @param timeoutMs Timeout duration in milliseconds
     * @param onTimeout Callback to execute when timeout occurs
     */
    public void scheduleTimeout(long requestId, long timeoutMs, Runnable onTimeout) {
        // Cancel any existing timeout for this request
        cancelTimeout(requestId);
        
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            // Remove from map before executing callback
            pendingTimeouts.remove(requestId);
            
            System.out.println("⏰ Request #" + requestId + " timed out after " + timeoutMs + "ms");
            
            try {
                onTimeout.run();
            } catch (Exception e) {
                System.err.println("❌ Timeout callback failed for request #" + requestId + ": " + e.getMessage());
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        
        pendingTimeouts.put(requestId, future);
    }
    
    /**
     * Schedules a timeout using the default timeout duration.
     * 
     * @param requestId Unique identifier for the request
     * @param onTimeout Callback to execute when timeout occurs
     */
    public void scheduleTimeout(long requestId, Runnable onTimeout) {
        scheduleTimeout(requestId, defaultTimeoutMs, onTimeout);
    }
    
    /**
     * Cancels a pending timeout for a request.
     * Should be called when a request completes successfully.
     * 
     * @param requestId The request ID to cancel timeout for
     * @return true if a timeout was cancelled, false if no timeout was pending
     */
    public boolean cancelTimeout(long requestId) {
        ScheduledFuture<?> future = pendingTimeouts.remove(requestId);
        
        if (future != null) {
            boolean cancelled = future.cancel(false);
            return cancelled;
        }
        
        return false;
    }
    
    /**
     * Returns the number of pending timeouts.
     */
    public int getPendingCount() {
        return pendingTimeouts.size();
    }
    
    /**
     * Returns true if a timeout is pending for the given request.
     */
    public boolean hasPendingTimeout(long requestId) {
        return pendingTimeouts.containsKey(requestId);
    }
    
    /**
     * Shuts down the timeout handler.
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down RequestTimeoutHandler");
        
        // Cancel all pending timeouts
        for (ScheduledFuture<?> future : pendingTimeouts.values()) {
            future.cancel(false);
        }
        pendingTimeouts.clear();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Returns the default timeout in milliseconds.
     */
    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }
}

package com.webserver.concurrency;

/**
 * A thread-safe rate limiter using the Token Bucket algorithm.
 * 
 * The token bucket algorithm allows for controlled bursting while
 * maintaining an average rate limit. Tokens are added to the bucket
 * at a fixed rate, and each request consumes one token.
 * 
 * Key concepts demonstrated:
 * - Thread-safe state management with synchronized
 * - Time-based refill calculations
 * - Non-blocking and blocking acquire methods
 * 
 * @author Airaj Jena
 */
public class TokenBucketRateLimiter {
    
    private final long maxTokens;           // Maximum bucket capacity
    private final double refillRate;        // Tokens added per second
    private double currentTokens;           // Current tokens in bucket
    private long lastRefillTimeNanos;       // Last refill timestamp
    
    /**
     * Creates a new rate limiter.
     * 
     * @param maxTokens Maximum tokens (bucket capacity) - controls burst size
     * @param refillRatePerSecond Tokens added per second - controls sustained rate
     */
    public TokenBucketRateLimiter(long maxTokens, double refillRatePerSecond) {
        if (maxTokens < 1) throw new IllegalArgumentException("Max tokens must be >= 1");
        if (refillRatePerSecond <= 0) throw new IllegalArgumentException("Refill rate must be > 0");
        
        this.maxTokens = maxTokens;
        this.refillRate = refillRatePerSecond;
        this.currentTokens = maxTokens; // Start with full bucket
        this.lastRefillTimeNanos = System.nanoTime();
        
        System.out.println("🚦 TokenBucketRateLimiter initialized:");
        System.out.println("   Max tokens (burst): " + maxTokens);
        System.out.println("   Refill rate: " + refillRatePerSecond + " tokens/sec");
    }
    
    /**
     * Attempts to acquire a token without blocking.
     * 
     * @return true if a token was acquired, false if bucket is empty
     */
    public synchronized boolean tryAcquire() {
        refill();
        
        if (currentTokens >= 1.0) {
            currentTokens -= 1.0;
            return true;
        }
        
        return false;
    }
    
    /**
     * Attempts to acquire multiple tokens without blocking.
     * 
     * @param tokens Number of tokens to acquire
     * @return true if tokens were acquired, false if insufficient tokens
     */
    public synchronized boolean tryAcquire(int tokens) {
        if (tokens <= 0) throw new IllegalArgumentException("Tokens must be > 0");
        
        refill();
        
        if (currentTokens >= tokens) {
            currentTokens -= tokens;
            return true;
        }
        
        return false;
    }
    
    /**
     * Acquires a token, blocking if necessary until one is available.
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    public synchronized void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            // Calculate wait time until next token is available
            long waitTimeNanos = calculateWaitTimeNanos(1);
            long waitTimeMs = waitTimeNanos / 1_000_000;
            int waitTimeNanoRemainder = (int) (waitTimeNanos % 1_000_000);
            
            if (waitTimeMs > 0 || waitTimeNanoRemainder > 0) {
                wait(waitTimeMs, waitTimeNanoRemainder);
            }
        }
    }
    
    /**
     * Attempts to acquire a token with a timeout.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if a token was acquired, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public synchronized boolean tryAcquire(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        while (!tryAcquire()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            
            // Wait for the minimum of remaining time or next token time
            long waitTimeNanos = calculateWaitTimeNanos(1);
            long waitTimeMs = Math.min(remaining, waitTimeNanos / 1_000_000 + 1);
            
            wait(waitTimeMs);
        }
        
        return true;
    }
    
    /**
     * Refills the bucket based on elapsed time.
     * This is called before each acquire attempt.
     */
    private void refill() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillTimeNanos;
        
        if (elapsedNanos > 0) {
            // Calculate tokens to add based on elapsed time
            double tokensToAdd = (elapsedNanos / 1_000_000_000.0) * refillRate;
            currentTokens = Math.min(maxTokens, currentTokens + tokensToAdd);
            lastRefillTimeNanos = nowNanos;
        }
    }
    
    /**
     * Calculates how long to wait for the specified number of tokens.
     * 
     * @param tokensNeeded Number of tokens needed
     * @return Wait time in nanoseconds
     */
    private long calculateWaitTimeNanos(double tokensNeeded) {
        double tokensDeficit = tokensNeeded - currentTokens;
        if (tokensDeficit <= 0) return 0;
        
        // Time = tokensNeeded / rate
        double waitTimeSeconds = tokensDeficit / refillRate;
        return (long) (waitTimeSeconds * 1_000_000_000);
    }
    
    // ============== Getters for monitoring ==============
    
    /**
     * Returns the current number of available tokens.
     */
    public synchronized double getAvailableTokens() {
        refill();
        return currentTokens;
    }
    
    /**
     * Returns the maximum bucket capacity.
     */
    public long getMaxTokens() {
        return maxTokens;
    }
    
    /**
     * Returns the refill rate in tokens per second.
     */
    public double getRefillRate() {
        return refillRate;
    }
    
    /**
     * Returns rate limiter statistics.
     */
    public synchronized RateLimiterStats getStats() {
        refill();
        return new RateLimiterStats(currentTokens, maxTokens, refillRate);
    }
    
    /**
     * Immutable snapshot of rate limiter state.
     */
    public record RateLimiterStats(
        double availableTokens,
        long maxTokens,
        double refillRate
    ) {
        public double getUtilizationPercent() {
            return ((maxTokens - availableTokens) / maxTokens) * 100;
        }
        
        @Override
        public String toString() {
            return String.format(
                "RateLimiter[tokens=%.2f/%d, rate=%.2f/sec, utilization=%.1f%%]",
                availableTokens, maxTokens, refillRate, getUtilizationPercent()
            );
        }
    }
}

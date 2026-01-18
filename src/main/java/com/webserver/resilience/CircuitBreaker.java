package com.webserver.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A Circuit Breaker implementation for fault tolerance.
 * 
 * The circuit breaker pattern prevents cascading failures by temporarily
 * blocking requests to a failing service. It has three states:
 * 
 * - CLOSED: Normal operation. Requests pass through. Failures are counted.
 * - OPEN: Circuit is tripped. Requests fail immediately without executing.
 * - HALF_OPEN: Testing mode. Limited requests are let through to test recovery.
 * 
 * State transitions:
 * - CLOSED -> OPEN: When failure count reaches threshold
 * - OPEN -> HALF_OPEN: After reset timeout expires
 * - HALF_OPEN -> CLOSED: On successful request
 * - HALF_OPEN -> OPEN: On failed request
 * 
 * Key concepts demonstrated:
 * - State machine pattern
 * - Atomic state transitions
 * - Failure threshold and timeout configuration
 * - Thread-safe statistics
 * 
 * @author Airaj Jena
 */
public class CircuitBreaker {
    
    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Failing fast
        HALF_OPEN  // Testing recovery
    }
    
    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final int halfOpenMaxRequests;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenRequests = new AtomicInteger(0);
    
    private volatile long lastFailureTime = 0;
    private volatile long lastStateChangeTime = System.currentTimeMillis();
    
    // Statistics
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger rejectedRequests = new AtomicInteger(0);
    
    /**
     * Creates a new circuit breaker.
     * 
     * @param name Identifier for this circuit breaker
     * @param failureThreshold Number of failures before opening circuit
     * @param resetTimeoutMs Time in milliseconds before attempting recovery
     * @param halfOpenMaxRequests Maximum requests to allow in HALF_OPEN state
     */
    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs, 
                          int halfOpenMaxRequests) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
        
        System.out.println("🔌 CircuitBreaker '" + name + "' initialized:");
        System.out.println("   Failure threshold: " + failureThreshold);
        System.out.println("   Reset timeout: " + resetTimeoutMs + "ms");
    }
    
    /**
     * Convenience constructor with default half-open max requests.
     */
    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
        this(name, failureThreshold, resetTimeoutMs, 3);
    }
    
    /**
     * Executes an action through the circuit breaker.
     * 
     * @param action The action to execute
     * @return The result of the action
     * @throws CircuitOpenException if the circuit is open
     * @throws Exception if the action throws
     */
    public <T> T execute(Supplier<T> action) throws Exception {
        totalRequests.incrementAndGet();
        
        State currentState = getState();
        
        switch (currentState) {
            case OPEN:
                rejectedRequests.incrementAndGet();
                throw new CircuitOpenException(
                    "Circuit '" + name + "' is OPEN (failures: " + failureCount.get() + ")"
                );
                
            case HALF_OPEN:
                // Allow limited requests in half-open state
                if (halfOpenRequests.incrementAndGet() > halfOpenMaxRequests) {
                    halfOpenRequests.decrementAndGet();
                    rejectedRequests.incrementAndGet();
                    throw new CircuitOpenException(
                        "Circuit '" + name + "' is HALF_OPEN (max requests reached)"
                    );
                }
                break;
                
            case CLOSED:
            default:
                // Normal operation
                break;
        }
        
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
    
    /**
     * Executes an action and returns a default value if circuit is open.
     * 
     * @param action The action to execute
     * @param fallback The fallback value if circuit is open
     * @return The result or fallback
     */
    public <T> T executeWithFallback(Supplier<T> action, T fallback) {
        try {
            return execute(action);
        } catch (CircuitOpenException e) {
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
    
    /**
     * Called on successful execution.
     */
    private void onSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            successCount.incrementAndGet();
            
            // If we've had enough successes, close the circuit
            if (successCount.get() >= halfOpenMaxRequests) {
                transitionTo(State.CLOSED);
                System.out.println("✅ Circuit '" + name + "' CLOSED (recovered)");
            }
        } else if (currentState == State.CLOSED) {
            // Reset failure count on success in closed state
            failureCount.set(0);
            successCount.incrementAndGet();
        }
    }
    
    /**
     * Called on failed execution.
     */
    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        int failures = failureCount.incrementAndGet();
        
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            // Any failure in half-open state re-opens the circuit
            transitionTo(State.OPEN);
            System.out.println("❌ Circuit '" + name + "' OPEN (half-open test failed)");
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            transitionTo(State.OPEN);
            System.out.println("❌ Circuit '" + name + "' OPEN (threshold reached: " + failures + ")");
        }
    }
    
    /**
     * Gets the current state, checking for timeout transitions.
     */
    public State getState() {
        State currentState = state.get();
        
        // Check if we should transition from OPEN to HALF_OPEN
        if (currentState == State.OPEN) {
            long timeSinceFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceFailure >= resetTimeoutMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenRequests.set(0);
                    successCount.set(0);
                    lastStateChangeTime = System.currentTimeMillis();
                    System.out.println("⚡ Circuit '" + name + "' HALF_OPEN (testing recovery)");
                    return State.HALF_OPEN;
                }
            }
        }
        
        return state.get();
    }
    
    /**
     * Transitions to a new state.
     */
    private void transitionTo(State newState) {
        state.set(newState);
        lastStateChangeTime = System.currentTimeMillis();
        
        if (newState == State.CLOSED) {
            failureCount.set(0);
            successCount.set(0);
        } else if (newState == State.HALF_OPEN) {
            halfOpenRequests.set(0);
            successCount.set(0);
        }
    }
    
    /**
     * Manually resets the circuit breaker to CLOSED state.
     */
    public void reset() {
        transitionTo(State.CLOSED);
        System.out.println("🔄 Circuit '" + name + "' manually reset to CLOSED");
    }
    
    /**
     * Manually trips the circuit to OPEN state.
     */
    public void trip() {
        transitionTo(State.OPEN);
        lastFailureTime = System.currentTimeMillis();
        System.out.println("🔌 Circuit '" + name + "' manually tripped to OPEN");
    }
    
    // ============== Getters ==============
    
    public String getName() { return name; }
    public int getFailureThreshold() { return failureThreshold; }
    public long getResetTimeoutMs() { return resetTimeoutMs; }
    public int getFailureCount() { return failureCount.get(); }
    public int getSuccessCount() { return successCount.get(); }
    
    /**
     * Returns circuit breaker statistics.
     */
    public CircuitStats getStats() {
        return new CircuitStats(
            name,
            getState(),
            failureCount.get(),
            totalRequests.get(),
            rejectedRequests.get(),
            lastStateChangeTime
        );
    }
    
    /**
     * Immutable circuit breaker statistics.
     */
    public record CircuitStats(
        String name,
        State state,
        int failures,
        int totalRequests,
        int rejectedRequests,
        long lastStateChangeTime
    ) {
        public long getTimeSinceStateChangeMs() {
            return System.currentTimeMillis() - lastStateChangeTime;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Circuit[%s, state=%s, failures=%d, requests=%d, rejected=%d]",
                name, state, failures, totalRequests, rejectedRequests
            );
        }
    }
}

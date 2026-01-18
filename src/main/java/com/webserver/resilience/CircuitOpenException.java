package com.webserver.resilience;

/**
 * Exception thrown when a circuit breaker is open and rejecting requests.
 * 
 * @author Airaj Jena
 */
public class CircuitOpenException extends RuntimeException {
    
    public CircuitOpenException(String message) {
        super(message);
    }
    
    public CircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}

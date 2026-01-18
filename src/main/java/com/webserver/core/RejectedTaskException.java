package com.webserver.core;

/**
 * Exception thrown when a task is rejected by the thread pool.
 * 
 * @author Airaj Jena
 */
public class RejectedTaskException extends RuntimeException {
    
    public RejectedTaskException(String message) {
        super(message);
    }
    
    public RejectedTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}

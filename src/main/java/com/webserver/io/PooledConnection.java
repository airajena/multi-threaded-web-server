package com.webserver.io;

import java.io.*;
import java.net.Socket;

/**
 * A pooled connection wrapper that tracks usage and validity.
 * 
 * @author Airaj Jena
 */
public class PooledConnection {
    
    private final Socket socket;
    private final String host;
    private final int port;
    private final long createdAt;
    
    private long lastUsedTime;
    private boolean acquired = false;
    
    public PooledConnection(Socket socket, String host, int port) {
        this.socket = socket;
        this.host = host;
        this.port = port;
        this.createdAt = System.currentTimeMillis();
        this.lastUsedTime = createdAt;
    }
    
    /**
     * Returns the underlying socket.
     */
    public Socket getSocket() {
        return socket;
    }
    
    /**
     * Returns the input stream for reading.
     */
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }
    
    /**
     * Returns the output stream for writing.
     */
    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }
    
    /**
     * Checks if the connection is still valid.
     */
    public boolean isValid() {
        return socket != null && 
               !socket.isClosed() && 
               socket.isConnected() && 
               !socket.isInputShutdown() && 
               !socket.isOutputShutdown();
    }
    
    /**
     * Marks the connection as acquired from the pool.
     */
    public void markAcquired() {
        this.acquired = true;
        this.lastUsedTime = System.currentTimeMillis();
    }
    
    /**
     * Marks the connection as released back to the pool.
     */
    public void markReleased() {
        this.acquired = false;
        this.lastUsedTime = System.currentTimeMillis();
    }
    
    /**
     * Returns whether the connection is currently acquired.
     */
    public boolean isAcquired() {
        return acquired;
    }
    
    /**
     * Returns the time this connection was last used.
     */
    public long getLastUsedTime() {
        return lastUsedTime;
    }
    
    /**
     * Returns the age of this connection in milliseconds.
     */
    public long getAge() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * Returns the pool key for this connection.
     */
    public String getKey() {
        return host + ":" + port;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    /**
     * Closes the underlying socket.
     */
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "PooledConnection[%s:%d, valid=%s, acquired=%s, age=%dms]",
            host, port, isValid(), acquired, getAge()
        );
    }
}

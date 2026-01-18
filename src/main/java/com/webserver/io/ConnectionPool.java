package com.webserver.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.*;

/**
 * A thread-safe connection pool for reusing HTTP connections (Keep-Alive).
 * 
 * Connection pooling reduces the overhead of creating new TCP connections
 * for each request, significantly improving throughput for repeated requests
 * to the same host.
 * 
 * Key concepts demonstrated:
 * - Connection reuse with Keep-Alive
 * - Idle timeout and cleanup
 * - Maximum connections per host
 * - Thread-safe connection management
 * 
 * @author Airaj Jena
 */
public class ConnectionPool {
    
    private final int maxConnectionsPerHost;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    
    private final Map<String, Deque<PooledConnection>> pool;
    private final ScheduledExecutorService cleanupExecutor;
    
    // Statistics
    private long totalCreated = 0;
    private long totalReused = 0;
    private long totalClosed = 0;
    
    /**
     * Creates a new connection pool.
     * 
     * @param maxConnectionsPerHost Maximum connections per host
     * @param connectionTimeoutMs Timeout for creating new connections
     * @param idleTimeoutMs Time before idle connections are closed
     */
    public ConnectionPool(int maxConnectionsPerHost, long connectionTimeoutMs, long idleTimeoutMs) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        
        this.pool = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionPool-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupIdleConnections,
            idleTimeoutMs,
            idleTimeoutMs / 2,
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("🔗 ConnectionPool initialized:");
        System.out.println("   Max connections per host: " + maxConnectionsPerHost);
        System.out.println("   Connection timeout: " + connectionTimeoutMs + "ms");
        System.out.println("   Idle timeout: " + idleTimeoutMs + "ms");
    }
    
    /**
     * Convenience constructor with defaults.
     */
    public ConnectionPool() {
        this(10, 5000, 60000);
    }
    
    /**
     * Acquires a connection to the specified host.
     * Returns a pooled connection if available, otherwise creates a new one.
     * 
     * @param host Target host
     * @param port Target port
     * @return A connection to the host
     * @throws IOException if unable to connect
     */
    public PooledConnection acquire(String host, int port) throws IOException {
        String key = createKey(host, port);
        
        // Try to get an existing connection
        Deque<PooledConnection> connections = pool.get(key);
        if (connections != null) {
            PooledConnection conn;
            while ((conn = connections.pollFirst()) != null) {
                if (conn.isValid()) {
                    conn.markAcquired();
                    totalReused++;
                    System.out.println("♻️ Reused connection to " + key);
                    return conn;
                } else {
                    // Connection is stale, close it
                    conn.close();
                    totalClosed++;
                }
            }
        }
        
        // Create new connection
        return createConnection(host, port);
    }
    
    /**
     * Releases a connection back to the pool.
     * The connection will be available for reuse.
     * 
     * @param connection The connection to release
     */
    public void release(PooledConnection connection) {
        if (connection == null) return;
        
        String key = connection.getKey();
        Deque<PooledConnection> connections = pool.computeIfAbsent(
            key, k -> new ConcurrentLinkedDeque<>()
        );
        
        // Check if we can add to pool
        if (connections.size() < maxConnectionsPerHost && connection.isValid()) {
            connection.markReleased();
            connections.addLast(connection);
            System.out.println("📥 Released connection to pool: " + key);
        } else {
            // Pool is full or connection is invalid
            connection.close();
            totalClosed++;
        }
    }
    
    /**
     * Creates a new connection to the host.
     */
    private PooledConnection createConnection(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) connectionTimeoutMs);
        socket.setSoTimeout((int) connectionTimeoutMs);
        socket.setKeepAlive(true);
        
        totalCreated++;
        System.out.println("🆕 Created new connection to " + host + ":" + port);
        
        return new PooledConnection(socket, host, port);
    }
    
    /**
     * Cleans up idle connections that have exceeded the idle timeout.
     */
    private void cleanupIdleConnections() {
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, Deque<PooledConnection>> entry : pool.entrySet()) {
            Deque<PooledConnection> connections = entry.getValue();
            
            connections.removeIf(conn -> {
                if (!conn.isValid() || (now - conn.getLastUsedTime()) > idleTimeoutMs) {
                    conn.close();
                    totalClosed++;
                    System.out.println("🧹 Cleaned up idle connection: " + entry.getKey());
                    return true;
                }
                return false;
            });
        }
    }
    
    /**
     * Creates a pool key from host and port.
     */
    private String createKey(String host, int port) {
        return host + ":" + port;
    }
    
    /**
     * Returns the number of pooled connections for a host.
     */
    public int getPooledConnectionCount(String host, int port) {
        String key = createKey(host, port);
        Deque<PooledConnection> connections = pool.get(key);
        return connections != null ? connections.size() : 0;
    }
    
    /**
     * Returns total pooled connections across all hosts.
     */
    public int getTotalPooledConnections() {
        return pool.values().stream()
            .mapToInt(Deque::size)
            .sum();
    }
    
    /**
     * Closes all pooled connections and shuts down the pool.
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down ConnectionPool");
        
        cleanupExecutor.shutdown();
        
        for (Deque<PooledConnection> connections : pool.values()) {
            for (PooledConnection conn : connections) {
                conn.close();
                totalClosed++;
            }
        }
        pool.clear();
        
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Returns pool statistics.
     */
    public PoolStats getStats() {
        return new PoolStats(
            getTotalPooledConnections(),
            totalCreated,
            totalReused,
            totalClosed
        );
    }
    
    /**
     * Immutable pool statistics.
     */
    public record PoolStats(
        int currentPooled,
        long totalCreated,
        long totalReused,
        long totalClosed
    ) {
        public double getReuseRate() {
            long total = totalCreated + totalReused;
            return total == 0 ? 0.0 : (double) totalReused / total * 100;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ConnectionPool[pooled=%d, created=%d, reused=%d, closed=%d, reuseRate=%.1f%%]",
                currentPooled, totalCreated, totalReused, totalClosed, getReuseRate()
            );
        }
    }
}

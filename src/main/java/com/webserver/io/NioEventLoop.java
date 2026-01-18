package com.webserver.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A non-blocking I/O event loop using Java NIO Selector.
 * 
 * This implementation demonstrates the Reactor pattern commonly used in
 * high-performance servers like Netty, Node.js, and Nginx. A single thread
 * can handle thousands of connections by using non-blocking I/O.
 * 
 * Key concepts demonstrated:
 * - Selector pattern for multiplexed I/O
 * - Non-blocking socket channels
 * - Event-driven architecture
 * - Single-threaded event loop (like Netty's EventLoop)
 * 
 * @author Airaj Jena
 */
public class NioEventLoop implements Runnable {
    
    private final int port;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Consumer<NioConnection> connectionHandler;
    
    // Statistics
    private long totalConnections = 0;
    private long totalBytesRead = 0;
    private long totalBytesWritten = 0;
    
    /**
     * Creates a new NIO event loop.
     * 
     * @param port Port to listen on
     * @param connectionHandler Handler for new connections
     * @throws IOException if unable to bind to port
     */
    public NioEventLoop(int port, Consumer<NioConnection> connectionHandler) throws IOException {
        this.port = port;
        this.connectionHandler = connectionHandler;
        
        // Create selector
        this.selector = Selector.open();
        
        // Create non-blocking server socket channel
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(port));
        
        // Register for accept events
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("🔌 NioEventLoop initialized on port " + port);
    }
    
    @Override
    public void run() {
        running.set(true);
        System.out.println("🚀 NioEventLoop started - single-threaded non-blocking I/O");
        System.out.println("   Listening on http://localhost:" + port);
        
        while (running.get()) {
            try {
                // Block until at least one channel is ready (or timeout)
                int readyChannels = selector.select(1000);
                
                if (readyChannels == 0) {
                    continue; // Timeout, check if still running
                }
                
                // Get the keys for ready channels
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove(); // Must remove to avoid re-processing
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        System.err.println("❌ Error handling key: " + e.getMessage());
                        closeConnection(key);
                    }
                }
                
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("❌ Selector error: " + e.getMessage());
                }
            }
        }
        
        // Cleanup
        cleanup();
        System.out.println("🛑 NioEventLoop stopped");
    }
    
    /**
     * Handles a new connection accept event.
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        
        if (client != null) {
            client.configureBlocking(false);
            totalConnections++;
            
            // Create connection context
            NioConnection connection = new NioConnection(client, totalConnections);
            
            // Register for read events
            SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
            clientKey.attach(connection);
            
            System.out.println("🔗 [NIO] Connection #" + totalConnections + 
                " from " + client.getRemoteAddress());
            
            // Notify handler
            if (connectionHandler != null) {
                connectionHandler.accept(connection);
            }
        }
    }
    
    /**
     * Handles a read-ready event.
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        NioConnection connection = (NioConnection) key.attachment();
        
        ByteBuffer buffer = connection.getReadBuffer();
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            // Client disconnected
            closeConnection(key);
            return;
        }
        
        if (bytesRead > 0) {
            totalBytesRead += bytesRead;
            connection.addBytesRead(bytesRead);
            
            // Flip buffer for reading
            buffer.flip();
            
            // Simple HTTP response for demo
            String request = new String(buffer.array(), 0, buffer.limit());
            
            if (request.contains("\r\n\r\n")) {
                // Complete HTTP request received
                String response = createHttpResponse(request, connection);
                connection.setWriteData(response.getBytes());
                
                // Switch to write mode
                key.interestOps(SelectionKey.OP_WRITE);
            }
            
            buffer.clear();
        }
    }
    
    /**
     * Handles a write-ready event.
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        NioConnection connection = (NioConnection) key.attachment();
        
        ByteBuffer writeBuffer = connection.getWriteBuffer();
        
        if (writeBuffer.hasRemaining()) {
            int bytesWritten = channel.write(writeBuffer);
            totalBytesWritten += bytesWritten;
            connection.addBytesWritten(bytesWritten);
        }
        
        if (!writeBuffer.hasRemaining()) {
            // All data written, close connection (HTTP 1.0 style for simplicity)
            closeConnection(key);
        }
    }
    
    /**
     * Creates a simple HTTP response for demo purposes.
     */
    private String createHttpResponse(String request, NioConnection connection) {
        String path = "/";
        String[] lines = request.split("\r\n");
        if (lines.length > 0) {
            String[] parts = lines[0].split(" ");
            if (parts.length > 1) {
                path = parts[1];
            }
        }
        
        String body;
        if (path.equals("/nio/stats")) {
            body = String.format(
                "{\"eventLoop\": \"NIO\", \"totalConnections\": %d, \"totalBytesRead\": %d, " +
                "\"totalBytesWritten\": %d, \"thread\": \"%s\"}",
                totalConnections, totalBytesRead, totalBytesWritten,
                Thread.currentThread().getName()
            );
        } else {
            body = String.format(
                "{\"message\": \"Hello from NIO EventLoop!\", \"connectionId\": %d, " +
                "\"path\": \"%s\", \"thread\": \"%s\", \"mode\": \"non-blocking\"}",
                connection.getConnectionId(), path, Thread.currentThread().getName()
            );
        }
        
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: application/json\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "Connection: close\r\n" +
               "Server: NioEventLoop/1.0\r\n" +
               "\r\n" +
               body;
    }
    
    /**
     * Closes a connection and releases resources.
     */
    private void closeConnection(SelectionKey key) {
        try {
            NioConnection connection = (NioConnection) key.attachment();
            if (connection != null) {
                System.out.println("🔌 [NIO] Connection #" + connection.getConnectionId() + " closed");
            }
            key.cancel();
            key.channel().close();
        } catch (IOException e) {
            System.err.println("❌ Error closing connection: " + e.getMessage());
        }
    }
    
    /**
     * Stops the event loop.
     */
    public void stop() {
        running.set(false);
        selector.wakeup(); // Wake up selector if blocked
    }
    
    /**
     * Cleans up resources.
     */
    private void cleanup() {
        try {
            for (SelectionKey key : selector.keys()) {
                try {
                    key.channel().close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            selector.close();
            serverChannel.close();
        } catch (IOException e) {
            System.err.println("❌ Error during cleanup: " + e.getMessage());
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPort() {
        return port;
    }
    
    /**
     * Returns event loop statistics.
     */
    public NioStats getStats() {
        return new NioStats(totalConnections, totalBytesRead, totalBytesWritten);
    }
    
    /**
     * Immutable NIO statistics.
     */
    public record NioStats(
        long totalConnections,
        long totalBytesRead,
        long totalBytesWritten
    ) {
        @Override
        public String toString() {
            return String.format(
                "NioEventLoop[connections=%d, bytesRead=%d, bytesWritten=%d]",
                totalConnections, totalBytesRead, totalBytesWritten
            );
        }
    }
}

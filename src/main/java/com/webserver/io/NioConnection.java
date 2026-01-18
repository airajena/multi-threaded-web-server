package com.webserver.io;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Represents a single connection in the NIO event loop.
 * Holds connection state, buffers, and statistics.
 * 
 * @author Airaj Jena
 */
public class NioConnection {
    
    private static final int BUFFER_SIZE = 4096;
    
    private final SocketChannel channel;
    private final long connectionId;
    private final long createdAt;
    
    private final ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;
    
    private long bytesRead = 0;
    private long bytesWritten = 0;
    
    public NioConnection(SocketChannel channel, long connectionId) {
        this.channel = channel;
        this.connectionId = connectionId;
        this.createdAt = System.currentTimeMillis();
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.writeBuffer = ByteBuffer.allocate(0);
    }
    
    public SocketChannel getChannel() {
        return channel;
    }
    
    public long getConnectionId() {
        return connectionId;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }
    
    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }
    
    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }
    
    public void setWriteData(byte[] data) {
        this.writeBuffer = ByteBuffer.wrap(data);
    }
    
    public void addBytesRead(long bytes) {
        this.bytesRead += bytes;
    }
    
    public void addBytesWritten(long bytes) {
        this.bytesWritten += bytes;
    }
    
    public long getBytesRead() {
        return bytesRead;
    }
    
    public long getBytesWritten() {
        return bytesWritten;
    }
    
    @Override
    public String toString() {
        return String.format(
            "NioConnection[id=%d, age=%dms, read=%d, written=%d]",
            connectionId, getAgeMs(), bytesRead, bytesWritten
        );
    }
}

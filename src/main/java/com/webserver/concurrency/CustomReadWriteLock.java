package com.webserver.concurrency;

/**
 * A custom Read-Write Lock implementation using only synchronized, wait, and notify.
 * 
 * This implementation allows multiple concurrent readers OR a single exclusive writer.
 * It provides writer preference to prevent writer starvation.
 * 
 * Key concepts demonstrated:
 * - Reader-writer concurrency problem
 * - Writer preference (prevents writer starvation)
 * - Condition variables using wait/notify
 * - Reentrant read locking
 * 
 * @author Airaj Jena
 */
public class CustomReadWriteLock {
    
    private int readers = 0;           // Number of active readers
    private int writers = 0;          // Number of active writers (0 or 1)
    private int writeRequests = 0;    // Pending write requests (for writer preference)
    
    // Track which thread holds the write lock for reentrancy
    private Thread writeLockOwner = null;
    private int writeHoldCount = 0;
    
    /**
     * Acquires the read lock. Multiple threads can hold the read lock
     * simultaneously, as long as no thread holds the write lock.
     * 
     * If a writer is waiting, new readers will block to prevent writer starvation.
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    public synchronized void lockRead() throws InterruptedException {
        // Wait while:
        // 1. A writer is active, OR
        // 2. A writer is waiting (writer preference)
        while (writers > 0 || writeRequests > 0) {
            wait();
        }
        
        readers++;
    }
    
    /**
     * Releases the read lock.
     * 
     * @throws IllegalMonitorStateException if current thread doesn't hold read lock
     */
    public synchronized void unlockRead() {
        if (readers <= 0) {
            throw new IllegalMonitorStateException("No read lock held");
        }
        
        readers--;
        
        // If this was the last reader and writers are waiting, notify them
        if (readers == 0) {
            notifyAll();
        }
    }
    
    /**
     * Acquires the write lock. Only one thread can hold the write lock,
     * and it's exclusive (no readers or other writers).
     * 
     * This method is reentrant - the same thread can acquire it multiple times.
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    public synchronized void lockWrite() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        
        // Check for reentrancy
        if (writeLockOwner == currentThread) {
            writeHoldCount++;
            return;
        }
        
        // Register as a waiting writer (for writer preference)
        writeRequests++;
        
        try {
            // Wait while readers or another writer is active
            while (readers > 0 || writers > 0) {
                wait();
            }
            
            writers++;
            writeLockOwner = currentThread;
            writeHoldCount = 1;
        } finally {
            writeRequests--;
        }
    }
    
    /**
     * Releases the write lock.
     * 
     * @throws IllegalMonitorStateException if current thread doesn't hold write lock
     */
    public synchronized void unlockWrite() {
        if (writeLockOwner != Thread.currentThread()) {
            throw new IllegalMonitorStateException("Current thread doesn't hold write lock");
        }
        
        writeHoldCount--;
        
        if (writeHoldCount == 0) {
            writers--;
            writeLockOwner = null;
            notifyAll(); // Notify waiting readers and writers
        }
    }
    
    /**
     * Attempts to acquire the read lock without blocking.
     * 
     * @return true if the lock was acquired, false otherwise
     */
    public synchronized boolean tryLockRead() {
        if (writers > 0 || writeRequests > 0) {
            return false;
        }
        readers++;
        return true;
    }
    
    /**
     * Attempts to acquire the write lock without blocking.
     * 
     * @return true if the lock was acquired, false otherwise
     */
    public synchronized boolean tryLockWrite() {
        Thread currentThread = Thread.currentThread();
        
        // Check for reentrancy
        if (writeLockOwner == currentThread) {
            writeHoldCount++;
            return true;
        }
        
        if (readers > 0 || writers > 0) {
            return false;
        }
        
        writers++;
        writeLockOwner = currentThread;
        writeHoldCount = 1;
        return true;
    }
    
    /**
     * Downgrades a write lock to a read lock atomically.
     * The calling thread must hold the write lock.
     * 
     * @throws IllegalMonitorStateException if current thread doesn't hold write lock
     */
    public synchronized void downgrade() {
        if (writeLockOwner != Thread.currentThread()) {
            throw new IllegalMonitorStateException("Current thread doesn't hold write lock");
        }
        
        // Acquire read lock first
        readers++;
        
        // Release write lock
        writeHoldCount = 0;
        writers--;
        writeLockOwner = null;
        
        // Notify other waiting readers
        notifyAll();
    }
    
    // ============== Getters for monitoring ==============
    
    public synchronized int getReadLockCount() {
        return readers;
    }
    
    public synchronized boolean isWriteLocked() {
        return writers > 0;
    }
    
    public synchronized int getQueuedWriters() {
        return writeRequests;
    }
    
    public synchronized boolean isWriteLockedByCurrentThread() {
        return writeLockOwner == Thread.currentThread();
    }
    
    /**
     * Returns lock statistics.
     */
    public synchronized LockStats getStats() {
        return new LockStats(readers, writers > 0, writeRequests);
    }
    
    /**
     * Immutable snapshot of lock state.
     */
    public record LockStats(
        int activeReaders,
        boolean writeActive,
        int queuedWriters
    ) {
        @Override
        public String toString() {
            return String.format(
                "RWLock[readers=%d, writer=%s, queued=%d]",
                activeReaders, writeActive ? "active" : "none", queuedWriters
            );
        }
    }
}

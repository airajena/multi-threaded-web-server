package com.webserver.core;

/**
 * A custom blocking task queue implementation using only synchronized/wait/notify.
 * This demonstrates understanding of low-level thread coordination without using
 * Java's built-in concurrent collections like BlockingQueue.
 * 
 * Features:
 * - Bounded buffer with configurable capacity
 * - Blocking put() when queue is full
 * - Blocking take() when queue is empty
 * - Timed offer() with timeout support
 * - Interrupt-aware operations
 * 
 * @author Airaj Jena
 */
public class BlockingTaskQueue {
    private final Runnable[] buffer;
    private final int capacity;
    private int head = 0;      // Index for next dequeue
    private int tail = 0;      // Index for next enqueue
    private int size = 0;      // Current number of elements
    
    /**
     * Creates a new blocking task queue with the specified capacity.
     * 
     * @param capacity Maximum number of tasks the queue can hold
     * @throws IllegalArgumentException if capacity is less than 1
     */
    public BlockingTaskQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.buffer = new Runnable[capacity];
    }
    
    /**
     * Adds a task to the queue, blocking if the queue is full.
     * This method will wait indefinitely until space becomes available.
     * 
     * @param task The task to add
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws NullPointerException if task is null
     */
    public synchronized void put(Runnable task) throws InterruptedException {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        
        // Wait while queue is full
        while (size == capacity) {
            wait();
        }
        
        // Add task to the circular buffer
        buffer[tail] = task;
        tail = (tail + 1) % capacity;
        size++;
        
        // Notify waiting consumers that a task is available
        notifyAll();
    }
    
    /**
     * Attempts to add a task to the queue with a timeout.
     * Returns immediately if space is available, otherwise waits up to
     * the specified timeout for space to become available.
     * 
     * @param task The task to add
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if the task was added, false if timeout elapsed
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws NullPointerException if task is null
     */
    public synchronized boolean offer(Runnable task, long timeoutMs) throws InterruptedException {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        while (size == capacity) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false; // Timeout elapsed
            }
            wait(remaining);
        }
        
        buffer[tail] = task;
        tail = (tail + 1) % capacity;
        size++;
        
        notifyAll();
        return true;
    }
    
    /**
     * Attempts to add a task without blocking.
     * 
     * @param task The task to add
     * @return true if the task was added, false if queue is full
     * @throws NullPointerException if task is null
     */
    public synchronized boolean tryOffer(Runnable task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        
        if (size == capacity) {
            return false;
        }
        
        buffer[tail] = task;
        tail = (tail + 1) % capacity;
        size++;
        
        notifyAll();
        return true;
    }
    
    /**
     * Retrieves and removes a task from the queue, blocking if empty.
     * This method will wait indefinitely until a task becomes available.
     * 
     * @return The next task in the queue
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public synchronized Runnable take() throws InterruptedException {
        // Wait while queue is empty
        while (size == 0) {
            wait();
        }
        
        // Remove task from the circular buffer
        Runnable task = buffer[head];
        buffer[head] = null; // Help GC
        head = (head + 1) % capacity;
        size--;
        
        // Notify waiting producers that space is available
        notifyAll();
        
        return task;
    }
    
    /**
     * Attempts to retrieve a task with a timeout.
     * Returns immediately if a task is available, otherwise waits up to
     * the specified timeout for a task to become available.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The next task, or null if timeout elapsed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public synchronized Runnable poll(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        while (size == 0) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return null; // Timeout elapsed
            }
            wait(remaining);
        }
        
        Runnable task = buffer[head];
        buffer[head] = null;
        head = (head + 1) % capacity;
        size--;
        
        notifyAll();
        return task;
    }
    
    /**
     * Returns the current number of tasks in the queue.
     */
    public synchronized int size() {
        return size;
    }
    
    /**
     * Returns the maximum capacity of the queue.
     */
    public int capacity() {
        return capacity;
    }
    
    /**
     * Returns true if the queue is empty.
     */
    public synchronized boolean isEmpty() {
        return size == 0;
    }
    
    /**
     * Returns true if the queue is full.
     */
    public synchronized boolean isFull() {
        return size == capacity;
    }
    
    /**
     * Clears all tasks from the queue.
     */
    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
        head = 0;
        tail = 0;
        size = 0;
        notifyAll();
    }
}

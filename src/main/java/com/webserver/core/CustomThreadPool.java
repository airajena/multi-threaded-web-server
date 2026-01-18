package com.webserver.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A custom thread pool implementation built from scratch using only
 * low-level Java concurrency primitives (synchronized, wait/notify, volatile).
 * 
 * This implementation demonstrates understanding of:
 * - Thread lifecycle management
 * - Work stealing and task distribution
 * - Rejection policies when queue is full
 * - Graceful vs forced shutdown
 * - Thread pool statistics and monitoring
 * 
 * Unlike Java's ExecutorService, this is built entirely from scratch
 * to showcase low-level concurrency skills.
 * 
 * @author Airaj Jena
 */
public class CustomThreadPool {
    
    // Configuration
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final long keepAliveTimeMs;
    
    // Core components
    private final BlockingTaskQueue taskQueue;
    private final List<WorkerThread> workers;
    private final RejectionPolicy rejectionPolicy;
    
    // State
    private volatile boolean isShutdown = false;
    private volatile boolean isTerminated = false;
    
    // Statistics
    private final AtomicLong submittedTasks = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong rejectedTasks = new AtomicLong(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    
    // Lock for worker list modifications
    private final Object workerLock = new Object();
    
    /**
     * Creates a thread pool with the specified configuration.
     * 
     * @param corePoolSize Minimum number of worker threads to keep alive
     * @param maxPoolSize Maximum number of worker threads
     * @param queueCapacity Maximum number of tasks in the queue
     * @param keepAliveTimeMs Time in ms to keep excess threads alive
     * @param rejectionPolicy Policy for handling rejected tasks
     */
    public CustomThreadPool(int corePoolSize, int maxPoolSize, int queueCapacity,
                            long keepAliveTimeMs, RejectionPolicy rejectionPolicy) {
        
        if (corePoolSize < 1) throw new IllegalArgumentException("Core pool size must be >= 1");
        if (maxPoolSize < corePoolSize) throw new IllegalArgumentException("Max pool size must be >= core pool size");
        if (queueCapacity < 1) throw new IllegalArgumentException("Queue capacity must be >= 1");
        
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveTimeMs = keepAliveTimeMs;
        this.rejectionPolicy = rejectionPolicy != null ? rejectionPolicy : RejectionPolicy.ABORT;
        
        this.taskQueue = new BlockingTaskQueue(queueCapacity);
        this.workers = new ArrayList<>(corePoolSize);
        
        // Initialize core workers
        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
        
        System.out.println("🏗️ CustomThreadPool initialized:");
        System.out.println("   Core/Max threads: " + corePoolSize + "/" + maxPoolSize);
        System.out.println("   Queue capacity: " + queueCapacity);
        System.out.println("   Keep-alive: " + keepAliveTimeMs + "ms");
    }
    
    /**
     * Convenience constructor with default rejection policy.
     */
    public CustomThreadPool(int corePoolSize, int maxPoolSize, int queueCapacity) {
        this(corePoolSize, maxPoolSize, queueCapacity, 60000, RejectionPolicy.ABORT);
    }
    
    /**
     * Convenience constructor for fixed-size pool.
     */
    public CustomThreadPool(int poolSize, int queueCapacity) {
        this(poolSize, poolSize, queueCapacity, 60000, RejectionPolicy.ABORT);
    }
    
    /**
     * Submits a task for execution.
     * 
     * @param task The task to execute
     * @throws RejectedTaskException if the pool is shutdown or queue is full
     */
    public void execute(Runnable task) {
        if (task == null) throw new NullPointerException("Task cannot be null");
        
        if (isShutdown) {
            rejectedTasks.incrementAndGet();
            throw new RejectedTaskException("Thread pool is shutdown");
        }
        
        submittedTasks.incrementAndGet();
        
        // Try to add to queue
        if (taskQueue.tryOffer(task)) {
            // Task queued successfully
            return;
        }
        
        // Queue is full - try to add a new worker thread
        synchronized (workerLock) {
            if (workers.size() < maxPoolSize) {
                addWorker();
                
                // Try to queue again after adding worker
                if (taskQueue.tryOffer(task)) {
                    return;
                }
            }
        }
        
        // Still can't queue - apply rejection policy
        rejectedTasks.incrementAndGet();
        rejectionPolicy.reject(task, this);
    }
    
    /**
     * Submits a task with a timeout for queuing.
     * 
     * @param task The task to execute
     * @param timeout Maximum time to wait for queue space
     * @param unit Time unit for timeout
     * @return true if task was accepted, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean executeWithTimeout(Runnable task, long timeout, TimeUnit unit) 
            throws InterruptedException {
        if (task == null) throw new NullPointerException("Task cannot be null");
        if (isShutdown) throw new RejectedTaskException("Thread pool is shutdown");
        
        submittedTasks.incrementAndGet();
        
        boolean accepted = taskQueue.offer(task, unit.toMillis(timeout));
        if (!accepted) {
            rejectedTasks.incrementAndGet();
        }
        return accepted;
    }
    
    /**
     * Adds a new worker thread to the pool.
     */
    private void addWorker() {
        synchronized (workerLock) {
            int workerId = workers.size();
            WorkerThread worker = new WorkerThread(taskQueue, this, workerId);
            workers.add(worker);
            activeWorkers.incrementAndGet();
            worker.start();
        }
    }
    
    /**
     * Called by WorkerThread when a task completes.
     */
    void onTaskCompleted() {
        completedTasks.incrementAndGet();
    }
    
    /**
     * Used by DISCARD_OLDEST policy to discard the oldest task and retry.
     */
    void discardOldestAndExecute(Runnable newTask) {
        try {
            // Poll oldest task (non-blocking)
            Runnable oldest = taskQueue.poll(0);
            if (oldest != null) {
                System.out.println("⚠️ Discarded oldest task to make room");
            }
            // Try to add new task
            taskQueue.tryOffer(newTask);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Initiates graceful shutdown. No new tasks will be accepted,
     * but queued tasks will be processed.
     */
    public void shutdown() {
        isShutdown = true;
        System.out.println("🛑 CustomThreadPool shutdown initiated");
    }
    
    /**
     * Attempts to stop all workers immediately.
     * Queued tasks may not be processed.
     */
    public void shutdownNow() {
        isShutdown = true;
        System.out.println("🛑 CustomThreadPool forced shutdown");
        
        synchronized (workerLock) {
            for (WorkerThread worker : workers) {
                worker.shutdown();
            }
        }
    }
    
    /**
     * Waits for all tasks to complete after shutdown.
     * 
     * @param timeout Maximum time to wait
     * @param unit Time unit
     * @return true if all tasks completed, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        
        synchronized (workerLock) {
            for (WorkerThread worker : workers) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return false;
                
                worker.join(remaining);
                if (worker.isAlive()) return false;
            }
        }
        
        isTerminated = true;
        System.out.println("✅ CustomThreadPool terminated");
        return true;
    }
    
    // ============== Getters for statistics ==============
    
    public int getQueueCapacity() { return queueCapacity; }
    public int getQueueSize() { return taskQueue.size(); }
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getCurrentPoolSize() { 
        synchronized (workerLock) { return workers.size(); }
    }
    
    public int getActiveCount() {
        synchronized (workerLock) {
            int count = 0;
            for (WorkerThread worker : workers) {
                if (!worker.isIdle()) count++;
            }
            return count;
        }
    }
    
    public long getSubmittedTaskCount() { return submittedTasks.get(); }
    public long getCompletedTaskCount() { return completedTasks.get(); }
    public long getRejectedTaskCount() { return rejectedTasks.get(); }
    public boolean isShutdown() { return isShutdown; }
    public boolean isTerminated() { return isTerminated; }
    
    /**
     * Returns a snapshot of the current pool state for monitoring.
     */
    public PoolStats getStats() {
        return new PoolStats(
            getCurrentPoolSize(),
            getActiveCount(),
            getQueueSize(),
            submittedTasks.get(),
            completedTasks.get(),
            rejectedTasks.get()
        );
    }
    
    /**
     * Immutable snapshot of thread pool statistics.
     */
    public record PoolStats(
        int poolSize,
        int activeThreads,
        int queuedTasks,
        long submittedTasks,
        long completedTasks,
        long rejectedTasks
    ) {
        @Override
        public String toString() {
            return String.format(
                "PoolStats[threads=%d/%d active, queue=%d, submitted=%d, completed=%d, rejected=%d]",
                activeThreads, poolSize, queuedTasks, submittedTasks, completedTasks, rejectedTasks
            );
        }
    }
}

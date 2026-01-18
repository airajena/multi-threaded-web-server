package com.webserver.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A worker thread that continuously polls tasks from the task queue.
 * Designed to be used within CustomThreadPool for processing submitted tasks.
 * 
 * Features:
 * - Lifecycle management (start, interrupt, graceful shutdown)
 * - Idle time tracking for dynamic pool sizing
 * - Task execution statistics
 * - Interrupt-aware task processing
 * 
 * @author Airaj Jena
 */
public class WorkerThread extends Thread {
    private final BlockingTaskQueue taskQueue;
    private final CustomThreadPool pool;
    private final int workerId;
    
    private volatile boolean running = true;
    private volatile boolean idle = true;
    private volatile long lastActiveTime = System.currentTimeMillis();
    
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong totalExecutionTimeNanos = new AtomicLong(0);
    
    /**
     * Creates a new worker thread.
     * 
     * @param taskQueue The queue to poll tasks from
     * @param pool The parent thread pool
     * @param workerId Unique identifier for this worker
     */
    public WorkerThread(BlockingTaskQueue taskQueue, CustomThreadPool pool, int workerId) {
        this.taskQueue = taskQueue;
        this.pool = pool;
        this.workerId = workerId;
        
        // Set a descriptive name for debugging
        setName("Worker-" + workerId);
        
        // Set as daemon so JVM can exit if only worker threads remain
        setDaemon(true);
    }
    
    @Override
    public void run() {
        System.out.println("🔄 [" + getName() + "] Started");
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                idle = true;
                
                // Block waiting for a task
                Runnable task = taskQueue.take();
                
                if (task != null) {
                    idle = false;
                    lastActiveTime = System.currentTimeMillis();
                    
                    // Execute the task with timing
                    long startTime = System.nanoTime();
                    try {
                        task.run();
                    } catch (Exception e) {
                        System.err.println("❌ [" + getName() + "] Task execution failed: " + e.getMessage());
                    }
                    long endTime = System.nanoTime();
                    
                    // Update statistics
                    tasksCompleted.incrementAndGet();
                    totalExecutionTimeNanos.addAndGet(endTime - startTime);
                    
                    // Notify pool that task completed
                    pool.onTaskCompleted();
                }
                
            } catch (InterruptedException e) {
                // Thread was interrupted, check if we should continue
                if (!running) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        System.out.println("🛑 [" + getName() + "] Stopped (completed " + tasksCompleted.get() + " tasks)");
    }
    
    /**
     * Signals this worker to stop processing tasks.
     * The worker will finish its current task before stopping.
     */
    public void shutdown() {
        running = false;
        this.interrupt();
    }
    
    /**
     * Returns the number of tasks completed by this worker.
     */
    public long getTasksCompleted() {
        return tasksCompleted.get();
    }
    
    /**
     * Returns the average execution time per task in milliseconds.
     */
    public double getAverageExecutionTimeMs() {
        long completed = tasksCompleted.get();
        if (completed == 0) return 0.0;
        return (totalExecutionTimeNanos.get() / completed) / 1_000_000.0;
    }
    
    /**
     * Returns true if this worker is currently idle (waiting for tasks).
     */
    public boolean isIdle() {
        return idle;
    }
    
    /**
     * Returns the time in milliseconds since this worker last processed a task.
     */
    public long getIdleTimeMs() {
        return System.currentTimeMillis() - lastActiveTime;
    }
    
    /**
     * Returns the worker ID.
     */
    public int getWorkerId() {
        return workerId;
    }
    
    /**
     * Returns true if this worker is still running.
     */
    public boolean isRunning() {
        return running;
    }
}

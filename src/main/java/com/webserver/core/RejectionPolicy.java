package com.webserver.core;

/**
 * Rejection policy interface for thread pool task rejection.
 * Defines what action to take when the task queue is full and
 * a new task cannot be accepted.
 * 
 * This follows the Strategy Design Pattern, allowing different
 * rejection behaviors to be plugged into the thread pool.
 * 
 * @author Airaj Jena
 */
@FunctionalInterface
public interface RejectionPolicy {
    
    /**
     * Called when a task cannot be accepted by the thread pool.
     * 
     * @param task The task that was rejected
     * @param pool The thread pool that rejected the task
     * @throws RejectedTaskException if the policy decides to throw
     */
    void reject(Runnable task, CustomThreadPool pool);
    
    // ============== Built-in Policies ==============
    
    /**
     * Throws RejectedTaskException when a task is rejected.
     * Use when task rejection should be treated as an error.
     */
    RejectionPolicy ABORT = (task, pool) -> {
        throw new RejectedTaskException(
            "Task rejected: thread pool queue is full (capacity: " + 
            pool.getQueueCapacity() + ", active: " + pool.getActiveCount() + ")"
        );
    };
    
    /**
     * Runs the rejected task in the caller's thread.
     * This provides a simple feedback mechanism that slows down
     * the producer when the pool is overloaded.
     */
    RejectionPolicy CALLER_RUNS = (task, pool) -> {
        if (!pool.isShutdown()) {
            System.out.println("⚠️ [CALLER_RUNS] Executing task in caller thread: " + 
                Thread.currentThread().getName());
            task.run();
        }
    };
    
    /**
     * Silently discards the rejected task.
     * Use when losing tasks is acceptable.
     */
    RejectionPolicy DISCARD = (task, pool) -> {
        System.out.println("⚠️ [DISCARD] Task silently discarded");
    };
    
    /**
     * Discards the oldest task in the queue and retries.
     * This gives priority to newer tasks over older ones.
     */
    RejectionPolicy DISCARD_OLDEST = (task, pool) -> {
        if (!pool.isShutdown()) {
            // The pool will handle discarding the oldest and retrying
            System.out.println("⚠️ [DISCARD_OLDEST] Discarding oldest task and retrying");
            pool.discardOldestAndExecute(task);
        }
    };
}

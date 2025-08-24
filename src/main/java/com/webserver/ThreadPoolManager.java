// src/main/java/com/webserver/ThreadPoolManager.java
package com.webserver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages a pool of threads - like a general commanding an army of waiters!
 * This ensures we don't create too many threads (which would slow everything down)
 */
public class ThreadPoolManager {
    private final ExecutorService threadPool;
    private final int threadCount;

    public ThreadPoolManager(int threadCount) {
        this.threadCount = threadCount;
        // Create a fixed pool of threads - like hiring exactly 10 waiters
        this.threadPool = Executors.newFixedThreadPool(threadCount);

        System.out.println("🧵 Thread pool created with " + threadCount + " threads");
    }

    /**
     * Submit a task to be executed by one of our threads
     * Like telling a waiter: "Go serve table 5!"
     */
    public void execute(Runnable task) {
        threadPool.execute(task);
    }

    /**
     * Gracefully shutdown the thread pool
     * Like telling all waiters: "Finish your current customers, then go home"
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down thread pool...");

        threadPool.shutdown(); // No new tasks accepted

        try {
            // Wait up to 10 seconds for existing tasks to complete
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("⚠️  Force shutting down thread pool");
                threadPool.shutdownNow(); // Force shutdown
            } else {
                System.out.println("✅ Thread pool shut down gracefully");
            }
        } catch (InterruptedException e) {
            System.err.println("❌ Thread pool shutdown interrupted");
            threadPool.shutdownNow();
        }
    }

    public int getThreadCount() {
        return threadCount;
    }
}

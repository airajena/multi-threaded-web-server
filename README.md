# Multi-Threaded Web Server

A high-performance HTTP server built from the ground up in Java, exploring low-level concurrency primitives and non-blocking I/O patterns. This project implements core threading concepts without relying on high-level abstractions like `Executors.newFixedThreadPool()`.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Build](https://img.shields.io/badge/Build-Maven-blue.svg)](https://maven.apache.org/)

## Why I Built This

I wanted to deeply understand how concurrent systems work under the hood. Instead of using Java's built-in `ExecutorService`, I implemented:

- A **thread pool from scratch** using `synchronized`, `wait()`, and `notify()`
- A **blocking queue** that handles producer-consumer coordination
- A **read-write lock** with writer preference to prevent starvation
- A **rate limiter** using the token bucket algorithm
- An **LRU cache** with O(1) operations and thread-safe access
- A **circuit breaker** for fault tolerance
- An **NIO event loop** similar to how Netty handles connections

## Architecture

```
src/main/java/com/webserver/
├── core/                          # Thread Pool
│   ├── CustomThreadPool.java      # Main pool implementation
│   ├── BlockingTaskQueue.java     # Bounded blocking queue
│   ├── WorkerThread.java          # Worker lifecycle
│   └── RejectionPolicy.java       # Overflow handling strategies
│
├── concurrency/                   # Synchronization Primitives
│   ├── TokenBucketRateLimiter.java
│   ├── CustomReadWriteLock.java
│   └── RequestTimeoutHandler.java
│
├── cache/
│   └── LRUCache.java              # Thread-safe LRU cache
│
├── resilience/
│   └── CircuitBreaker.java        # Fault tolerance
│
├── io/                            # I/O Layer
│   ├── NioEventLoop.java          # Non-blocking event loop
│   ├── NioConnection.java
│   ├── ConnectionPool.java        # Keep-Alive connection pooling
│   └── PooledConnection.java
│
├── async/
│   └── AsyncRequestProcessor.java # CompletableFuture pipeline
│
└── [Server]
    ├── WebServerApp.java
    ├── MultiThreadedServer.java
    ├── ConnectionHandler.java
    └── RequestProcessor.java
```

## Getting Started

```bash
# Build
mvn clean compile

# Run with thread pool mode (default)
mvn exec:java

# Run with NIO event loop
mvn exec:java -Dexec.args="--nio"

# Custom configuration
mvn exec:java -Dexec.args="-p 8080 -t 20"
```

## Demo Endpoints

Once the server is running, you can explore each component:

| Endpoint | What it shows |
|----------|---------------|
| `http://localhost:8080/` | Welcome page |
| `http://localhost:8080/demo/threadpool` | Pool size, active threads, completed tasks |
| `http://localhost:8080/demo/ratelimit` | Available tokens, refill rate |
| `http://localhost:8080/demo/cache?key=foo&value=bar` | Cache operations and hit rate |
| `http://localhost:8080/demo/circuit` | Circuit breaker state |
| `http://localhost:8080/demo/async` | Async processing pipeline |
| `http://localhost:8080/demo/all` | All stats combined |

## How It Works

### Custom Thread Pool

The thread pool doesn't use `Executors`. Instead, it manages worker threads directly:

```java
public class CustomThreadPool {
    private final BlockingTaskQueue taskQueue;
    private final WorkerThread[] workers;
    private final RejectionPolicy rejectionPolicy;
    
    public void execute(Runnable task) {
        if (!taskQueue.tryOffer(task)) {
            rejectionPolicy.reject(task, this);
        }
    }
}
```

The `BlockingTaskQueue` uses `wait()` and `notify()` for thread coordination:

```java
public synchronized void put(Runnable task) throws InterruptedException {
    while (size == capacity) {
        wait();  // Block until space available
    }
    buffer[tail] = task;
    tail = (tail + 1) % capacity;
    size++;
    notifyAll();  // Wake waiting consumers
}
```

### Token Bucket Rate Limiter

Allows burst traffic while maintaining an average rate:

```java
public synchronized boolean tryAcquire() {
    refill();  // Add tokens based on elapsed time
    if (currentTokens >= 1.0) {
        currentTokens -= 1.0;
        return true;
    }
    return false;
}
```

### Read-Write Lock

Allows multiple readers but exclusive writers, with writer preference:

```java
public synchronized void lockRead() throws InterruptedException {
    while (writers > 0 || writeRequests > 0) {
        wait();  // Writers have priority
    }
    readers++;
}
```

### Circuit Breaker

Prevents cascading failures with automatic recovery:

```java
public <T> T execute(Supplier<T> action) throws Exception {
    if (state == OPEN && resetTimeoutExpired()) {
        state = HALF_OPEN;  // Test if service recovered
    }
    if (state == OPEN) {
        throw new CircuitOpenException("Circuit is open");
    }
    // Execute and track success/failure
}
```

### NIO Event Loop

Single-threaded, non-blocking I/O using Java's Selector:

```java
public void run() {
    while (running) {
        selector.select();  // Block until events
        for (SelectionKey key : selector.selectedKeys()) {
            if (key.isAcceptable()) handleAccept(key);
            if (key.isReadable()) handleRead(key);
            if (key.isWritable()) handleWrite(key);
        }
    }
}
```

## Load Testing

```bash
# Using wrk
wrk -t4 -c100 -d30s http://localhost:8080/demo/stress

# Using Apache Bench
ab -n 10000 -c 100 http://localhost:8080/demo/stress
```

## Running Tests

```bash
mvn test
```

## Tech Stack

- **Java 17** - Records, text blocks, pattern matching switch
- **Maven** - Build tool
- **JUnit 5** - Testing
- **JMH** - Microbenchmarking

## What I Learned

Building this project taught me:

1. **Thread coordination is tricky** - Getting `wait()`/`notify()` right requires careful thought about spurious wakeups and lost signals
2. **Writer preference matters** - Without it, writers can starve in high-read scenarios
3. **NIO is powerful but complex** - The Selector pattern enables handling thousands of connections with one thread
4. **Graceful shutdown is harder than it sounds** - Draining in-flight requests while accepting no new ones requires coordination

## License

MIT

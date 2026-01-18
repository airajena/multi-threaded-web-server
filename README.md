# Multi-Threaded Web Server

A **pure concurrency showcase** demonstrating low-level multithreading and systems programming skills in Java. Built from scratch without relying on high-level abstractions like `Executors.newFixedThreadPool()`.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Build](https://img.shields.io/badge/Build-Maven-blue.svg)](https://maven.apache.org/)

## 🎯 Purpose

This project demonstrates **interview-ready** concurrency concepts commonly asked at MAANG and top product-based companies:

- **Custom Thread Pool** - Hand-built without using Java's Executor framework
- **Blocking Queue** - Implemented with `synchronized`, `wait()`, `notify()`
- **Read-Write Lock** - Custom implementation with writer preference
- **Rate Limiter** - Token bucket algorithm
- **LRU Cache** - Thread-safe with O(1) operations
- **Circuit Breaker** - Fault tolerance pattern
- **NIO Event Loop** - Non-blocking I/O with Selector (like Netty)

## 🏗️ Architecture

```
src/main/java/com/webserver/
├── core/                          # Thread Pool Implementation
│   ├── CustomThreadPool.java      # Main thread pool (no Executors!)
│   ├── BlockingTaskQueue.java     # Blocking queue with wait/notify
│   ├── WorkerThread.java          # Worker lifecycle management
│   └── RejectionPolicy.java       # ABORT, CALLER_RUNS, DISCARD policies
│
├── concurrency/                   # Concurrency Primitives
│   ├── TokenBucketRateLimiter.java    # Rate limiting
│   ├── CustomReadWriteLock.java       # RW lock from scratch
│   └── RequestTimeoutHandler.java     # Non-blocking timeouts
│
├── cache/                         # Caching Layer
│   └── LRUCache.java              # Thread-safe LRU with eviction
│
├── resilience/                    # Fault Tolerance
│   └── CircuitBreaker.java        # CLOSED/OPEN/HALF_OPEN states
│
├── io/                            # I/O Layer
│   ├── NioEventLoop.java          # Selector-based event loop
│   ├── NioConnection.java         # Connection wrapper
│   ├── ConnectionPool.java        # HTTP Keep-Alive pooling
│   └── PooledConnection.java      # Pooled connection
│
├── async/                         # Async Processing
│   └── AsyncRequestProcessor.java # CompletableFuture pipeline
│
└── [Server classes]               # HTTP Server
    ├── WebServerApp.java          # Entry point
    ├── MultiThreadedServer.java   # Main server
    ├── ConnectionHandler.java     # Request handler
    └── RequestProcessor.java      # Demo endpoints
```

## 🚀 Quick Start

```bash
# Clone and build
git clone https://github.com/airajena/multi-threaded-web-server.git
cd multi-threaded-web-server
mvn clean compile

# Run with thread pool (default)
mvn exec:java

# Or with NIO event loop
mvn exec:java -Dexec.args="--nio"

# Custom port and threads
mvn exec:java -Dexec.args="-p 8080 -t 20"
```

## 🎮 Demo Endpoints

| Endpoint | Description |
|----------|-------------|
| `/` | Welcome page with links |
| `/demo/threadpool` | Custom thread pool statistics |
| `/demo/ratelimit` | Token bucket rate limiter status |
| `/demo/cache` | LRU cache operations (`?key=foo&value=bar`) |
| `/demo/circuit` | Circuit breaker state (`?fail=true` / `?reset=true`) |
| `/demo/async` | Async request processing demo |
| `/demo/all` | All concurrency stats |
| `/demo/stress` | High-load endpoint for testing |

## 🔧 Key Implementations

### 1. Custom Thread Pool

Built from scratch without `Executors`:

```java
// Uses custom BlockingTaskQueue with wait/notify
public class CustomThreadPool {
    private final BlockingTaskQueue taskQueue;  // Not java.util.concurrent!
    private final WorkerThread[] workers;
    private final RejectionPolicy rejectionPolicy;
    
    public void execute(Runnable task) {
        if (!taskQueue.tryOffer(task)) {
            rejectionPolicy.reject(task, this);
        }
    }
}
```

### 2. Token Bucket Rate Limiter

```java
public class TokenBucketRateLimiter {
    public synchronized boolean tryAcquire() {
        refill();  // Time-based token refill
        if (currentTokens >= 1.0) {
            currentTokens -= 1.0;
            return true;
        }
        return false;
    }
}
```

### 3. Custom Read-Write Lock

With writer preference to prevent starvation:

```java
public class CustomReadWriteLock {
    private int readers = 0;
    private int writers = 0;
    private int writeRequests = 0;  // Writer preference
    
    public synchronized void lockRead() throws InterruptedException {
        while (writers > 0 || writeRequests > 0) wait();
        readers++;
    }
}
```

### 4. LRU Cache with Eviction

```java
public class LRUCache<K, V> {
    private final Map<K, Node<K, V>> cache;      // O(1) lookup
    private final DoublyLinkedList<K, V> list;   // O(1) eviction
    private final CustomReadWriteLock lock;       // Thread-safe
}
```

### 5. Circuit Breaker

```java
public class CircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }
    
    public <T> T execute(Supplier<T> action) {
        if (state == OPEN && resetTimeoutExpired()) {
            state = HALF_OPEN;  // Test recovery
        }
        // Execute or throw CircuitOpenException
    }
}
```

### 6. NIO Event Loop

Single-threaded, non-blocking I/O (like Netty):

```java
public class NioEventLoop implements Runnable {
    private final Selector selector;
    
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
}
```

## 📊 Load Testing

```bash
# Using wrk
wrk -t4 -c100 -d30s http://localhost:8080/demo/stress

# Using Apache Bench
ab -n 10000 -c 100 http://localhost:8080/demo/stress
```

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=CustomThreadPoolTest

# Run benchmarks
mvn clean install
java -jar target/benchmarks.jar
```

## 📚 Interview Topics Covered

| Topic | Implementation |
|-------|---------------|
| Thread Pool Internals | `CustomThreadPool`, `BlockingTaskQueue` |
| Producer-Consumer | `BlockingTaskQueue` with `wait()`/`notify()` |
| Read-Write Locks | `CustomReadWriteLock` with writer preference |
| Rate Limiting | `TokenBucketRateLimiter` |
| Caching | `LRUCache` with O(1) eviction |
| Fault Tolerance | `CircuitBreaker` state machine |
| Non-blocking I/O | `NioEventLoop` with `Selector` |
| Async Programming | `AsyncRequestProcessor` with `CompletableFuture` |
| Graceful Shutdown | Request draining in `MultiThreadedServer` |

## 🛠️ Technologies

- **Java 17** - Records, text blocks, pattern matching
- **Maven** - Build and dependency management
- **JUnit 5** - Testing
- **JMH** - Benchmarking

## 👤 Author

**Airaj Jena**

- GitHub: [@airajena](https://github.com/airajena)

## 📄 License

MIT License

package com.webserver.cache;

import com.webserver.concurrency.CustomReadWriteLock;
import java.util.HashMap;
import java.util.Map;

/**
 * A thread-safe Least Recently Used (LRU) cache implementation.
 * 
 * Uses a combination of HashMap and doubly-linked list to achieve O(1)
 * time complexity for get, put, and eviction operations.
 * 
 * Thread safety is ensured using a custom Read-Write Lock:
 * - Multiple concurrent readers allowed
 * - Exclusive access for writers
 * 
 * Key concepts demonstrated:
 * - LRU eviction algorithm (common interview question)
 * - Doubly-linked list for O(1) removal
 * - Read-write lock for concurrent access
 * - Cache hit/miss statistics
 * 
 * @author Airaj Jena
 */
public class LRUCache<K, V> {
    
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final CustomReadWriteLock lock;
    
    // Doubly-linked list for LRU ordering
    // head -> most recently used
    // tail -> least recently used (eviction candidate)
    private Node<K, V> head;
    private Node<K, V> tail;
    
    // Statistics
    private long hits = 0;
    private long misses = 0;
    private long evictions = 0;
    
    /**
     * Doubly-linked list node.
     */
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        long accessTime;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.accessTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Creates a new LRU cache with the specified capacity.
     * 
     * @param capacity Maximum number of entries
     * @throws IllegalArgumentException if capacity < 1
     */
    public LRUCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>(capacity);
        this.lock = new CustomReadWriteLock();
        
        System.out.println("🗃️ LRUCache initialized with capacity: " + capacity);
    }
    
    /**
     * Gets a value from the cache.
     * Moves the accessed entry to the front (most recently used).
     * 
     * @param key The key to look up
     * @return The value, or null if not found
     */
    public V get(K key) {
        try {
            lock.lockRead();
            
            Node<K, V> node = cache.get(key);
            if (node == null) {
                misses++;
                return null;
            }
            
            hits++;
            node.accessTime = System.currentTimeMillis();
            
            // Upgrade to write lock to move node to front
            lock.unlockRead();
            
            try {
                lock.lockWrite();
                moveToHead(node);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlockWrite();
            }
            
            return node.value;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Puts a value into the cache.
     * If the key exists, updates the value and moves to front.
     * If the cache is full, evicts the least recently used entry.
     * 
     * @param key The key
     * @param value The value
     */
    public void put(K key, V value) {
        try {
            lock.lockWrite();
            
            Node<K, V> existing = cache.get(key);
            
            if (existing != null) {
                // Update existing entry
                existing.value = value;
                existing.accessTime = System.currentTimeMillis();
                moveToHead(existing);
            } else {
                // Add new entry
                Node<K, V> newNode = new Node<>(key, value);
                
                // Check if eviction is needed
                if (cache.size() >= capacity) {
                    evictLRU();
                }
                
                cache.put(key, newNode);
                addToHead(newNode);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlockWrite();
        }
    }
    
    /**
     * Removes a value from the cache.
     * 
     * @param key The key to remove
     * @return The removed value, or null if not found
     */
    public V remove(K key) {
        try {
            lock.lockWrite();
            
            Node<K, V> node = cache.remove(key);
            if (node != null) {
                removeNode(node);
                return node.value;
            }
            return null;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlockWrite();
        }
    }
    
    /**
     * Checks if the cache contains the key.
     */
    public boolean containsKey(K key) {
        try {
            lock.lockRead();
            return cache.containsKey(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlockRead();
        }
    }
    
    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        try {
            lock.lockWrite();
            cache.clear();
            head = null;
            tail = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlockWrite();
        }
    }
    
    // ============== Internal list operations ==============
    
    private void addToHead(Node<K, V> node) {
        node.prev = null;
        node.next = head;
        
        if (head != null) {
            head.prev = node;
        }
        head = node;
        
        if (tail == null) {
            tail = node;
        }
    }
    
    private void removeNode(Node<K, V> node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }
        
        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }
    }
    
    private void moveToHead(Node<K, V> node) {
        if (node == head) {
            return; // Already at head
        }
        removeNode(node);
        addToHead(node);
    }
    
    private void evictLRU() {
        if (tail == null) return;
        
        cache.remove(tail.key);
        removeNode(tail);
        evictions++;
    }
    
    // ============== Getters for monitoring ==============
    
    public int size() {
        try {
            lock.lockRead();
            return cache.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            lock.unlockRead();
        }
    }
    
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Returns cache statistics.
     */
    public CacheStats getStats() {
        try {
            lock.lockRead();
            return new CacheStats(cache.size(), capacity, hits, misses, evictions);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CacheStats(0, capacity, 0, 0, 0);
        } finally {
            lock.unlockRead();
        }
    }
    
    /**
     * Immutable cache statistics.
     */
    public record CacheStats(
        int size,
        int capacity,
        long hits,
        long misses,
        long evictions
    ) {
        public double getHitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total * 100;
        }
        
        @Override
        public String toString() {
            return String.format(
                "LRUCache[size=%d/%d, hits=%d, misses=%d, evictions=%d, hitRate=%.1f%%]",
                size, capacity, hits, misses, evictions, getHitRate()
            );
        }
    }
}

// src/main/java/com/webserver/RequestProcessor.java
package com.webserver;

/**
 * Processes requests and generates responses
 * This is like our kitchen - where the actual work gets done
 */
public class RequestProcessor {
    private long requestCount = 0;

    /**
     * Process a request and return appropriate response
     */
    public synchronized Response processRequest(Request request) {
        requestCount++;
        String path = request.getPath();
        String method = request.getMethod();

        System.out.println("🍳 Processing request #" + requestCount + ": " + method + " " + path);

        // Add a small delay to simulate processing work
        // This will help us see the multi-threading in action!
        try {
            Thread.sleep(100); // 100ms processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Route to different handlers based on path
        switch (path) {
            case "/":
            case "/hello":
                return createWelcomeResponse();

            case "/time":
                return createTimeResponse();

            case "/stats":
                return createStatsResponse();

            case "/slow":
                return createSlowResponse(); // Test endpoint that takes time

            default:
                return createNotFoundResponse();
        }
    }

    private Response createWelcomeResponse() {
        String html = "<html><body style='font-family: Arial;'>" +
                "<h1>🎉 Multi-Threaded Web Server!</h1>" +
                "<p>Your server is now handling multiple requests simultaneously!</p>" +
                "<ul>" +
                "<li><a href='/hello'>Hello Page</a></li>" +
                "<li><a href='/time'>Current Time</a></li>" +
                "<li><a href='/stats'>Server Stats</a></li>" +
                "<li><a href='/slow'>Slow Endpoint (for testing)</a></li>" +
                "</ul>" +
                "<p><strong>Try opening multiple tabs to see multi-threading in action!</strong></p>" +
                "</body></html>";
        return new Response(200, html);
    }

    private Response createTimeResponse() {
        String currentTime = new java.util.Date().toString();
        String threadName = Thread.currentThread().getName();

        String html = "<html><body style='font-family: Arial;'>" +
                "<h1>⏰ Current Time</h1>" +
                "<p>Time: <strong>" + currentTime + "</strong></p>" +
                "<p>Served by thread: <strong>" + threadName + "</strong></p>" +
                "<a href='/hello'>← Back to Home</a>" +
                "</body></html>";
        return new Response(200, html);
    }

    private Response createStatsResponse() {
        String threadName = Thread.currentThread().getName();
        int activeThreads = Thread.activeCount();

        String html = "<html><body style='font-family: Arial;'>" +
                "<h1>📊 Server Statistics</h1>" +
                "<p>Total requests processed: <strong>" + requestCount + "</strong></p>" +
                "<p>Current thread: <strong>" + threadName + "</strong></p>" +
                "<p>Active threads: <strong>" + activeThreads + "</strong></p>" +
                "<p>Java version: <strong>" + System.getProperty("java.version") + "</strong></p>" +
                "<a href='/hello'>← Back to Home</a>" +
                "</body></html>";
        return new Response(200, html);
    }

    private Response createSlowResponse() {
        // Simulate a slow operation (like database query)
        try {
            Thread.sleep(3000); // 3 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String threadName = Thread.currentThread().getName();
        String html = "<html><body style='font-family: Arial;'>" +
                "<h1>🐌 Slow Response Complete!</h1>" +
                "<p>This response took 3 seconds to generate.</p>" +
                "<p>But other requests were served simultaneously!</p>" +
                "<p>Served by thread: <strong>" + threadName + "</strong></p>" +
                "<a href='/hello'>← Back to Home</a>" +
                "</body></html>";
        return new Response(200, html);
    }

    private Response createNotFoundResponse() {
        String html = "<html><body style='font-family: Arial;'>" +
                "<h1>404 - Page Not Found</h1>" +
                "<p>The page you're looking for doesn't exist.</p>" +
                "<a href='/hello'>← Back to Home</a>" +
                "</body></html>";
        return new Response(404, html);
    }

    public long getRequestCount() {
        return requestCount;
    }
}

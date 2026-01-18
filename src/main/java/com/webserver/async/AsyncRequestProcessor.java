package com.webserver.async;

import com.webserver.Request;
import com.webserver.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * An asynchronous request processing pipeline using CompletableFuture.
 * 
 * Demonstrates non-blocking, composable request processing with:
 * - Async parsing
 * - Async validation
 * - Async execution
 * - Error handling
 * - Pipeline composition
 * 
 * Key concepts demonstrated:
 * - CompletableFuture chaining
 * - Non-blocking I/O patterns
 * - Error recovery and fallbacks
 * - Executor-based async execution
 * 
 * @author Airaj Jena
 */
public class AsyncRequestProcessor {
    
    private final Executor executor;
    
    /**
     * Creates a new async request processor.
     * 
     * @param executor Executor to use for async operations
     */
    public AsyncRequestProcessor(Executor executor) {
        this.executor = executor;
        System.out.println("⚡ AsyncRequestProcessor initialized");
    }
    
    /**
     * Processes a request asynchronously through the pipeline.
     * 
     * Pipeline: Parse -> Validate -> Execute -> Error Handling
     * 
     * @param request The request to process
     * @return CompletableFuture that will complete with the response
     */
    public CompletableFuture<Response> processAsync(Request request) {
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture
            .supplyAsync(() -> {
                // Step 1: Parse/prepare request
                System.out.println("📥 [Async] Parsing request: " + request.getPath());
                return parseRequest(request);
            }, executor)
            .thenApplyAsync(parsedRequest -> {
                // Step 2: Validate request
                System.out.println("✔️ [Async] Validating request");
                return validateRequest(parsedRequest);
            }, executor)
            .thenApplyAsync(validRequest -> {
                // Step 3: Execute request
                System.out.println("⚙️ [Async] Executing request");
                return executeRequest(validRequest);
            }, executor)
            .thenApplyAsync(response -> {
                // Step 4: Post-process response
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("📤 [Async] Request completed in " + duration + "ms");
                response.addHeader("X-Processing-Time-Ms", String.valueOf(duration));
                response.addHeader("X-Processing-Mode", "async");
                return response;
            }, executor)
            .exceptionally(throwable -> {
                // Error handling
                System.err.println("❌ [Async] Request failed: " + throwable.getMessage());
                return createErrorResponse(throwable);
            });
    }
    
    /**
     * Processes a request with timeout.
     * 
     * @param request The request to process
     * @param timeoutMs Maximum processing time in milliseconds
     * @return CompletableFuture with the response
     */
    public CompletableFuture<Response> processWithTimeout(Request request, long timeoutMs) {
        CompletableFuture<Response> future = processAsync(request);
        
        return future.orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .exceptionally(throwable -> {
                if (throwable instanceof java.util.concurrent.TimeoutException) {
                    return new Response(504, 
                        "{\"error\": \"Request timed out after " + timeoutMs + "ms\"}");
                }
                return createErrorResponse(throwable);
            });
    }
    
    /**
     * Processes multiple requests in parallel.
     * 
     * @param requests List of requests to process
     * @return CompletableFuture that completes when all requests are done
     */
    public CompletableFuture<Response[]> processAll(Request... requests) {
        @SuppressWarnings("unchecked")
        CompletableFuture<Response>[] futures = new CompletableFuture[requests.length];
        
        for (int i = 0; i < requests.length; i++) {
            futures[i] = processAsync(requests[i]);
        }
        
        return CompletableFuture.allOf(futures)
            .thenApply(v -> {
                Response[] responses = new Response[futures.length];
                for (int i = 0; i < futures.length; i++) {
                    responses[i] = futures[i].join();
                }
                return responses;
            });
    }
    
    /**
     * Processes a request with retry logic.
     * 
     * @param request The request to process
     * @param maxRetries Maximum number of retries
     * @return CompletableFuture with the response
     */
    public CompletableFuture<Response> processWithRetry(Request request, int maxRetries) {
        return processAsync(request)
            .thenApply(response -> {
                if (response.getStatusCode() >= 500 && maxRetries > 0) {
                    System.out.println("🔄 [Async] Retrying request (" + maxRetries + " retries left)");
                    return processWithRetry(request, maxRetries - 1).join();
                }
                return response;
            });
    }
    
    /**
     * Creates a processing pipeline with custom stages.
     * 
     * @param stages Variable number of processing stages
     * @return Function that processes requests through all stages
     */
    @SafeVarargs
    public final Function<Request, CompletableFuture<Response>> createPipeline(
            Function<Request, Request>... stages) {
        
        return request -> {
            CompletableFuture<Request> pipeline = CompletableFuture.completedFuture(request);
            
            for (Function<Request, Request> stage : stages) {
                pipeline = pipeline.thenApplyAsync(stage, executor);
            }
            
            return pipeline.thenApplyAsync(this::executeRequest, executor);
        };
    }
    
    // ============== Pipeline stages ==============
    
    private Request parseRequest(Request request) {
        // Simulate parsing work
        try {
            Thread.sleep(5); // Simulate I/O
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return request;
    }
    
    private Request validateRequest(Request request) {
        // Validate the request
        String path = request.getPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Invalid request path");
        }
        return request;
    }
    
    private Response executeRequest(Request request) {
        // Simulate execution
        try {
            Thread.sleep(10); // Simulate processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        String responseBody = String.format(
            "{\"status\": \"success\", \"path\": \"%s\", \"method\": \"%s\", \"thread\": \"%s\"}",
            request.getPath(),
            request.getMethod(),
            Thread.currentThread().getName()
        );
        
        Response response = new Response(200, responseBody);
        response.addHeader("Content-Type", "application/json");
        return response;
    }
    
    private Response createErrorResponse(Throwable throwable) {
        String message = throwable.getMessage();
        if (throwable.getCause() != null) {
            message = throwable.getCause().getMessage();
        }
        
        String responseBody = String.format(
            "{\"error\": \"Request processing failed\", \"message\": \"%s\"}",
            message != null ? message.replace("\"", "'") : "Unknown error"
        );
        
        Response response = new Response(500, responseBody);
        response.addHeader("Content-Type", "application/json");
        return response;
    }
}

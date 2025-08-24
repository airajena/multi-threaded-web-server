// src/main/java/com/webserver/StaticFileHandler.java
package com.webserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler {
    private final String staticRoot;

    public StaticFileHandler() {
        // Look for static files in src/main/resources/static
        this.staticRoot = "src/main/resources/static";
    }

    public Response handleStaticFile(String requestPath) {
        try {
            // Remove leading slash and prevent directory traversal
            String cleanPath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
            cleanPath = cleanPath.replace("..", ""); // Security: prevent ../../../etc/passwd

            // Default to index.html if root is requested
            if (cleanPath.isEmpty() || cleanPath.equals("/")) {
                cleanPath = "index.html";
            }

            Path filePath = Paths.get(staticRoot, cleanPath);

            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                byte[] fileContent = Files.readAllBytes(filePath);
                String contentType = getContentType(cleanPath);

                Response response = new Response(200, "OK");
                response.setBinaryBody(fileContent, contentType);

                System.out.println("📁 Served static file: " + cleanPath + " (" + fileContent.length + " bytes)");
                return response;

            } else {
                System.out.println("❌ Static file not found: " + cleanPath);
                return new Response(404, "<h1>404 - File Not Found</h1><p>File: " + requestPath + "</p>");
            }

        } catch (IOException e) {
            System.err.println("❌ Error reading static file: " + e.getMessage());
            return new Response(500, "<h1>500 - Internal Server Error</h1>");
        }
    }

    private String getContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return "text/plain";
    }
}

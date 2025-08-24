// src/main/java/com/webserver/Response.java
package com.webserver;

import java.util.HashMap;
import java.util.Map;

public class Response {
    private int statusCode;
    private String reasonPhrase;
    private Map<String, String> headers;
    private byte[] body;

    // Single constructor that takes status code and body string
    public Response(int statusCode, String body) {
        this.statusCode = statusCode;
        this.reasonPhrase = getReasonPhrase(statusCode);
        this.headers = new HashMap<>();
        this.body = new byte[0]; // Initialize to empty

        // Add default headers
        addHeader("Server", "Enterprise-WebServer/2.0");
        addHeader("Connection", "close");

        // Set the body content
        setTextBody(body);
    }

    public void setTextBody(String textBody) {
        this.body = textBody.getBytes();
        addHeader("Content-Type", "text/html; charset=UTF-8");
        addHeader("Content-Length", String.valueOf(this.body.length));
    }

    public void setBinaryBody(byte[] binaryBody, String contentType) {
        this.body = binaryBody;
        addHeader("Content-Type", contentType);
        addHeader("Content-Length", String.valueOf(binaryBody.length));
    }

    public void addHeader(String name, String value) {
        headers.put(name, value);
    }

    public String toHttpString() {
        StringBuilder response = new StringBuilder();

        // Status line: HTTP/1.1 200 OK
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(reasonPhrase).append("\r\n");

        // Headers: Content-Type: text/html
        for (Map.Entry<String, String> header : headers.entrySet()) {
            response.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }

        // Empty line separates headers from body
        response.append("\r\n");

        return response.toString();
    }

    public byte[] getBody() {
        return body != null ? body : new byte[0];
    }

    private String getReasonPhrase(int code) {
        switch (code) {
            case 200: return "OK";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }

    // Getters for debugging
    public int getStatusCode() { return statusCode; }
    public String getReasonPhrase() { return reasonPhrase; }
    public Map<String, String> getHeaders() { return headers; }
}

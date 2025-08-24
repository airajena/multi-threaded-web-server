// src/main/java/com/webserver/Response.java
package com.webserver;

public class Response {
    private int statusCode;
    private String body;

    public Response(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public String toHttpString() {
        return "HTTP/1.1 " + statusCode + " OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                body;
    }
}

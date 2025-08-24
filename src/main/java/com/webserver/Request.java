// src/main/java/com/webserver/Request.java
package com.webserver;

public class Request {
    private String method;
    private String path;

    public Request(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public String getMethod() { return method; }
    public String getPath() { return path; }

    @Override
    public String toString() {
        return method + " " + path;
    }
}

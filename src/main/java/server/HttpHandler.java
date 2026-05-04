package server;

import http.HttpRequest;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface HttpHandler {
    void handle(HttpRequest request, OutputStream responseBody) throws IOException;
}

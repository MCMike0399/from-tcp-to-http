package server;

import java.io.IOException;

public class HttpServer implements AutoCloseable {
    private final int port;
    private final Router router;

    public HttpServer(int port, Router router) {
        this.port = port;
        this.router = router;
    }

    public void start() throws IOException {
        throw new UnsupportedOperationException("TODO: Implement HTTP server (Module 4, E4.1)");
    }

    public void stop() throws IOException {
        throw new UnsupportedOperationException("TODO: Implement HTTP server (Module 4, E4.1)");
    }

    public int getPort() {
        throw new UnsupportedOperationException("TODO: Implement HTTP server (Module 4, E4.1)");
    }

    public boolean isRunning() {
        throw new UnsupportedOperationException("TODO: Implement HTTP server (Module 4, E4.1)");
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}

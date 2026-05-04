package tcp;

import java.io.IOException;
import java.net.ServerSocket;

public class TcpEchoServer implements AutoCloseable {
    private final int port;

    public TcpEchoServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        throw new UnsupportedOperationException("TODO: Implement TCP echo server (Module 1, E1.1)");
    }

    public void stop() throws IOException {
        throw new UnsupportedOperationException("TODO: Implement TCP echo server (Module 1, E1.1)");
    }

    public int getPort() {
        throw new UnsupportedOperationException("TODO: Implement TCP echo server (Module 1, E1.1)");
    }

    public boolean isRunning() {
        throw new UnsupportedOperationException("TODO: Implement TCP echo server (Module 1, E1.1)");
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}

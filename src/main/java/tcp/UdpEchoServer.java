package tcp;

import java.io.IOException;

public class UdpEchoServer implements AutoCloseable {
    private final int port;

    public UdpEchoServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        throw new UnsupportedOperationException("TODO: Implement UDP echo server (Module 1, E1.3)");
    }

    public void stop() {
        throw new UnsupportedOperationException("TODO: Implement UDP echo server (Module 1, E1.3)");
    }

    public int getPort() {
        throw new UnsupportedOperationException("TODO: Implement UDP echo server (Module 1, E1.3)");
    }

    @Override
    public void close() {
        stop();
    }
}

package testutil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A lightweight test fixture that manages a {@link ServerSocket} on a given port.
 * <p>
 * Provides a factory method {@link #startEchoServer()} that binds to a random
 * available port and echoes lines back to each client. Intended for Module 1
 * integration tests; later modules will test against the student's own HttpServer.
 */
public class TestServerFixture implements AutoCloseable {

    private final ServerSocket serverSocket;
    private volatile Thread acceptThread;

    private TestServerFixture(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public TestServerFixture(int port) throws IOException {
        this(new ServerSocket(port));
    }

    /**
     * Starts an echo server on a random available port.
     * <p>
     * A virtual thread accepts connections in a loop; for each connection,
     * another virtual thread reads lines and echoes them back until the
     * client disconnects.
     */
    public static TestServerFixture startEchoServer() throws IOException {
        return startEchoServer(0);
    }

    /**
     * Starts an echo server on the specified port (use 0 for a random port).
     */
    public static TestServerFixture startEchoServer(int port) throws IOException {
        ServerSocket ss = new ServerSocket(port);
        TestServerFixture fixture = new TestServerFixture(ss);
        fixture.startAcceptLoop();
        return fixture;
    }

    private void startAcceptLoop() {
        acceptThread = Thread.ofVirtual().name("test-echo-accept").start(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    Thread.ofVirtual().name("test-echo-handler").start(() ->
                            handleEchoClient(client));
                }
            } catch (IOException e) {
                // ServerSocket closed — normal shutdown
            }
        });
    }

    private static void handleEchoClient(Socket client) {
        try (client;
             var reader = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
             var writer = new PrintWriter(
                     client.getOutputStream(), true, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
        } catch (IOException e) {
            // Client disconnected — nothing to do
        }
    }

    /**
     * Returns the port this server is bound to.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }
}

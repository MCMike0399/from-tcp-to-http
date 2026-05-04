package tcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Enforcement tests that verify the TCP Echo Server handles edge cases
 * required by correct socket programming: SO_REUSEADDR, resource cleanup,
 * and fragmented input handling.
 */
@DisplayName("Module 1: TCP Echo Server Enforcement Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TcpEchoServerEnforcementTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void echoServer_canRestartImmediately_soReuseAddrEnabled() throws Exception {
        // Find a free port
        int fixedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            fixedPort = probe.getLocalPort();
        }

        // First start-stop cycle
        TcpEchoServer server1 = new TcpEchoServer(fixedPort);
        server1.start();
        Thread.sleep(100);
        server1.stop();

        // Immediately restart on the same port
        final int port = fixedPort;
        assertThatCode(() -> {
            try (TcpEchoServer server2 = new TcpEchoServer(port)) {
                server2.start();
                Thread.sleep(100);

                // Verify it actually works
                try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                    client.sendLine("ReuseTest");
                    String response = client.readLine();
                    assertThat(response).isEqualTo("ReuseTest");
                }
            }
        })
        .as("Your server must set SO_REUSEADDR before binding. Without it, the port stays "
          + "in TIME_WAIT after close and you get 'Address already in use'. "
          + "See Conceptual Foundation 7: Connection Lifecycle and TIME_WAIT.")
        .doesNotThrowAnyException();
    }

    @Test
    @Order(2)
    void echoServer_closesSocketsOnDisconnect_noLeaks() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            int port = server.getPort();

            // Rapidly connect and disconnect 100 times
            for (int i = 0; i < 100; i++) {
                try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                    client.sendLine("Ping-" + i);
                    client.readLine();
                }
            }

            // If sockets aren't closed properly, file descriptor limit will be hit.
            // One more connection should still succeed.
            try (RawTcpClient finalClient = new RawTcpClient("localhost", port)) {
                finalClient.sendLine("Final");
                String response = finalClient.readLine();

                assertThat(response)
                        .as("After 100 rapid connect/disconnect cycles, the server must still accept "
                          + "connections. If client sockets are not closed on the server side, "
                          + "file descriptors leak and eventually the OS refuses new connections. "
                          + "Always close the client Socket in a finally block or try-with-resources.")
                        .isEqualTo("Final");
            }
        }
    }

    @Test
    @Order(3)
    void echoServer_handlesFragmentedInput() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            // Use raw Socket to send one byte at a time
            try (Socket socket = new Socket("localhost", server.getPort())) {
                socket.setSoTimeout(5_000);
                OutputStream out = socket.getOutputStream();

                byte[] message = "Hello\r\n".getBytes(StandardCharsets.US_ASCII);

                // Send each byte individually with a small delay
                for (byte b : message) {
                    out.write(b);
                    out.flush();
                    Thread.sleep(10);
                }

                // Read the response
                StringBuilder response = new StringBuilder();
                int prev = -1;
                while (true) {
                    int ch = socket.getInputStream().read();
                    if (ch == -1) break;
                    if (prev == '\r' && ch == '\n') {
                        // Remove trailing \r and stop
                        response.deleteCharAt(response.length() - 1);
                        break;
                    }
                    response.append((char) ch);
                    prev = ch;
                }

                assertThat(response.toString())
                        .as("TCP delivers bytes in arbitrary chunks. Your echo server must use LineReader "
                          + "to reassemble complete lines from partial reads. "
                          + "When bytes arrive one at a time (7 separate reads for 'Hello\\r\\n'), "
                          + "the server must still produce the correct echo. "
                          + "See Conceptual Foundation 1: TCP is a byte stream.")
                        .isEqualTo("Hello");
            }
        }
    }
}

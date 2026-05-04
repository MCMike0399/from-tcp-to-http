package tcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Validates basic functional correctness of the TCP Echo Server.
 * Each test starts a fresh server on a random port, connects with RawTcpClient,
 * exercises a specific behavior, and tears down cleanly.
 */
@DisplayName("Module 1: TCP Echo Server Correctness Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TcpEchoServerCorrectnessTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void echoServer_startsAndAcceptsConnection() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            assertThatCode(() -> {
                try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                    // Connection established successfully
                }
            })
            .as("The server must accept TCP connections. This is the ServerSocket.accept() loop.")
            .doesNotThrowAnyException();
        }
    }

    @Test
    @Order(2)
    void echoServer_singleLine_echosBack() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.sendLine("Hello");
                String response = client.readLine();

                assertThat(response)
                        .as("After sending 'Hello\\r\\n', the server should echo back 'Hello\\r\\n'. "
                          + "The server reads with LineReader (which strips the delimiter) and "
                          + "echoes the content back with \\r\\n appended.")
                        .isEqualTo("Hello");
            }
        }
    }

    @Test
    @Order(3)
    void echoServer_multipleLines_echosAll() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                String[] lines = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon"};

                for (String line : lines) {
                    client.sendLine(line);
                    String response = client.readLine();

                    assertThat(response)
                            .as("After sending '%s\\r\\n', the server must echo back '%s'. "
                              + "Each line must be echoed independently in the order received. "
                              + "The echo loop reads a line, writes it back, and repeats.",
                                    line, line)
                            .isEqualTo(line);
                }
            }
        }
    }

    @Test
    @Order(4)
    void echoServer_emptyLine_echosEmptyLine() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.sendLine("");
                String response = client.readLine();

                assertThat(response)
                        .as("An empty line (just '\\r\\n') must be echoed back as an empty string. "
                          + "The server should not skip or ignore empty lines. "
                          + "In HTTP, the empty line separating headers from body is critical.")
                        .isEqualTo("");
            }
        }
    }

    @Test
    @Order(5)
    void echoServer_longLine_echosComplete() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                String longLine = "A".repeat(10_000);
                client.sendLine(longLine);
                String response = client.readLine();

                assertThat(response)
                        .as("A 10,000-character line must be echoed back in full. "
                          + "TCP may fragment this into multiple packets, but LineReader "
                          + "must reassemble the complete line before the server echoes it.")
                        .isNotNull()
                        .hasSize(10_000);

                assertThat(response)
                        .as("Every character in the echoed 10,000-char line should be 'A'.")
                        .isEqualTo(longLine);
            }
        }
    }

    @Test
    @Order(6)
    void echoServer_clientDisconnect_serverSurvives() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            // First client: connect, send, disconnect
            try (RawTcpClient client1 = new RawTcpClient("localhost", server.getPort())) {
                client1.sendLine("First");
                client1.readLine();
            }
            // client1 is now closed

            Thread.sleep(200);

            // Second client: connect after first disconnected
            try (RawTcpClient client2 = new RawTcpClient("localhost", server.getPort())) {
                client2.sendLine("Second");
                String response = client2.readLine();

                assertThat(response)
                        .as("After the first client disconnects, the server must continue accepting "
                          + "new connections. A client disconnect may cause an IOException on the "
                          + "server side -- the per-connection handler must catch it and clean up "
                          + "without crashing the accept loop.")
                        .isEqualTo("Second");
            }
        }
    }
}

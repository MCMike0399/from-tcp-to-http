package server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.ConnectException;
import java.net.Socket;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests graceful shutdown behavior: the server must stop accepting new
 * connections after stop() and report its running state accurately.
 */
@DisplayName("Module 4: HTTP Server Graceful Shutdown Tests (E4.5)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpServerGracefulShutdownTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static Router defaultRouter() {
        Router router = new Router();
        router.addRoute("GET", "/hello", (req, out) -> {
            byte[] body = "Hello, World!".getBytes(US_ASCII);
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "\r\n").getBytes(US_ASCII));
            out.write(body);
            out.flush();
        });
        return router;
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void server_shutdown_rejectsNewConnectionsQuickly() throws Exception {
        int port;
        try (HttpServer server = new HttpServer(0, defaultRouter())) {
            server.start();
            Thread.sleep(200);
            port = server.getPort();

            // Verify the server is accepting connections before we stop it
            try (Socket probe = new Socket("localhost", port)) {
                assertThat(probe.isConnected())
                        .as("Server must accept connections while running.")
                        .isTrue();
            }

            server.stop();
            Thread.sleep(500);
        }

        // After stop(), new connections should be refused.
        assertThatThrownBy(() -> {
            try (Socket s = new Socket("localhost", port)) {
                // If we get here, the connection was accepted -- that's a failure
            }
        })
        .as("After stop() is called, the server must close its ServerSocket so that "
          + "new connection attempts are refused by the OS with ConnectException. "
          + "If this test fails, stop() is not closing the ServerSocket.")
        .isInstanceOf(ConnectException.class);
    }

    @Test
    @Order(2)
    void server_afterStop_isRunningReturnsFalse() throws Exception {
        try (HttpServer server = new HttpServer(0, defaultRouter())) {
            server.start();
            Thread.sleep(200);

            assertThat(server.isRunning())
                    .as("After start() returns and the server is accepting connections, "
                      + "isRunning() must return true.")
                    .isTrue();

            server.stop();
            Thread.sleep(200);

            assertThat(server.isRunning())
                    .as("After stop() completes, isRunning() must return false. "
                      + "Use a volatile boolean or AtomicBoolean flag that start() sets "
                      + "to true and stop() sets to false.")
                    .isFalse();
        }
    }
}

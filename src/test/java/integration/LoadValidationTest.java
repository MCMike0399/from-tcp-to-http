package integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import server.HttpServer;
import server.Router;
import testutil.RawTcpClient;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capstone load-validation tests that verify the HTTP server can handle
 * concurrent connections from real HTTP clients and does not leak sockets
 * under sustained traffic.
 */
@DisplayName("Module 6: Load Validation Tests (Capstone)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoadValidationTest {

    // ---------------------------------------------------------------
    // Shared server lifecycle
    // ---------------------------------------------------------------

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        Router router = new Router();
        router.addRoute("GET", "/hello", (req, out) -> {
            byte[] body = "Hello, World!".getBytes(US_ASCII);
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "\r\n").getBytes(US_ASCII));
            out.write(body);
            out.flush();
        });
        server = new HttpServer(0, router);
        server.start();
        port = server.getPort();
        Thread.sleep(200); // let the accept loop begin
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) server.close();
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void load_100ConcurrentClients_allSucceed() throws Exception {
        int clientCount = 100;
        CountDownLatch ready = new CountDownLatch(clientCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(clientCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    // Each virtual thread gets its own HttpClient
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);

                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/hello"))
                            .GET()
                            .build();

                    java.net.http.HttpResponse<String> resp = client.send(
                            req, java.net.http.HttpResponse.BodyHandlers.ofString());

                    if (resp.statusCode() == 200 && "Hello, World!".equals(resp.body())) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        // Wait for all threads to be ready, then release them simultaneously
        assertThat(ready.await(10, TimeUnit.SECONDS))
                .as("All %d client threads should be ready within 10 seconds.", clientCount)
                .isTrue();
        go.countDown();

        // All must complete within 15 seconds
        assertThat(done.await(15, TimeUnit.SECONDS))
                .as("All %d concurrent clients must complete within 15 seconds. "
                  + "If this times out, the server may be handling connections sequentially "
                  + "or deadlocking under load.", clientCount)
                .isTrue();

        assertThat(successes.get())
                .as("Your capstone server must handle 100+ concurrent connections. "
                  + "Module 4 proved this with virtual threads. Module 6 proves it with "
                  + "real HTTP clients. %d of %d succeeded, %d failed.",
                        successes.get(), clientCount, failures.get())
                .isEqualTo(clientCount);

        assertThat(failures.get())
                .as("No client should fail during the concurrent load test.")
                .isZero();
    }

    @Test
    @Order(2)
    void load_noSocketLeaks_afterManyConnections() throws Exception {
        int connectionCount = 500;

        // Open 500 sequential connections, each with Connection: close
        for (int i = 0; i < connectionCount; i++) {
            try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                client.send("GET /hello HTTP/1.1\r\n"
                        + "Host: localhost\r\n"
                        + "Connection: close\r\n"
                        + "\r\n");

                // Read the full response: status line, headers, body
                String statusLine = client.readLine();
                if (statusLine == null) {
                    throw new AssertionError(
                            "Connection #" + (i + 1) + " returned null status line. "
                          + "Server may have stopped accepting connections.");
                }

                // Drain headers until blank line
                int contentLength = 0;
                String headerLine;
                while ((headerLine = client.readLine()) != null && !headerLine.isEmpty()) {
                    if (headerLine.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(headerLine.split(":", 2)[1].trim());
                    }
                }

                // Read body to completion
                if (contentLength > 0) {
                    client.readBytes(contentLength);
                }
            }
            // Socket closed by try-with-resources; server should also close its end
        }

        // After 500 connections, open one more. If the server leaked file
        // descriptors, this connection will fail with "Too many open files".
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            client.send("GET /hello HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Connection: close\r\n"
                    + "\r\n");

            String statusLine = client.readLine();

            assertThat(statusLine)
                    .as("500 request/response cycles must not leak file descriptors. "
                      + "Every accepted socket MUST be closed after the connection ends. "
                      + "Use try-with-resources in your connection handling loop. "
                      + "If this fails with a connection error, the server ran out of "
                      + "file descriptors because it never closed accepted sockets.")
                    .isNotNull()
                    .contains("200");
        }
    }
}

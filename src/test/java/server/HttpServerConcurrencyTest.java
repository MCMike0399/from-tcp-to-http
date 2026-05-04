package server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests that verify the HTTP server can handle many simultaneous
 * connections using virtual threads, and that slow handlers do not block
 * other clients.
 */
@DisplayName("Module 4: HTTP Server Concurrency Tests (E4.1)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpServerConcurrencyTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static HttpServer startTestServer(Router router) throws Exception {
        HttpServer server = new HttpServer(0, router);
        server.start();
        Thread.sleep(200);
        return server;
    }

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
    void server_100ConcurrentConnections_allServed() throws Exception {
        Router router = defaultRouter();

        try (HttpServer server = startTestServer(router)) {
            int clientCount = 100;
            CountDownLatch ready = new CountDownLatch(clientCount);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(clientCount);
            ConcurrentHashMap<Integer, Boolean> successes = new ConcurrentHashMap<>();
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < clientCount; i++) {
                final int clientId = i;
                Thread.startVirtualThread(() -> {
                    try {
                        ready.countDown();
                        go.await(10, TimeUnit.SECONDS);

                        try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                            client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

                            String statusLine = client.readLine();
                            if (statusLine != null && statusLine.contains("200")) {
                                // Read remaining response
                                byte[] rest = client.readAll();
                                String full = new String(rest, US_ASCII);
                                if (full.contains("Hello, World!")) {
                                    successes.put(clientId, true);
                                }
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("All client threads should be ready within 10 seconds.")
                    .isTrue();
            go.countDown();

            assertThat(done.await(10, TimeUnit.SECONDS))
                    .as("All 100 clients must complete within 10 seconds. "
                      + "If this times out, the server likely handles connections "
                      + "sequentially instead of concurrently.")
                    .isTrue();

            assertThat(successes)
                    .as("Your server must handle 100+ concurrent connections. "
                      + "This requires one virtual thread per connection. "
                      + "See Conceptual Foundation 6: Virtual Threads.")
                    .hasSize(clientCount);

            assertThat(errors.get())
                    .as("No client should encounter an exception during concurrent access.")
                    .isZero();
        }
    }

    @Test
    @Order(2)
    void server_slowHandler_doesNotBlockOthers() throws Exception {
        Router router = defaultRouter();
        router.addRoute("GET", "/slow", (req, out) -> {
            try { Thread.sleep(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body = "slow".getBytes(US_ASCII);
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "\r\n").getBytes(US_ASCII));
            out.write(body);
            out.flush();
        });

        try (HttpServer server = startTestServer(router)) {
            int port = server.getPort();

            // Thread A: hits the slow endpoint
            Thread slowThread = Thread.startVirtualThread(() -> {
                try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                    client.send("GET /slow HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
                    client.readAll(); // blocks for ~2 seconds
                } catch (Exception ignored) {
                }
            });

            // Give thread A time to connect and start processing
            Thread.sleep(200);

            // Thread B: hits the fast endpoint
            long startTime = System.nanoTime();
            try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
                String statusLine = client.readLine();
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

                assertThat(statusLine)
                        .as("A slow handler must not block other connections. "
                          + "Each connection should be handled in its own virtual thread.")
                        .contains("200");

                assertThat(elapsedMs)
                        .as("A slow handler must not block other connections. "
                          + "Each connection should be handled in its own virtual thread. "
                          + "The fast request took %dms but should complete under 1000ms.", elapsedMs)
                        .isLessThan(1_000);
            }

            slowThread.join(5_000);
        }
    }

    @Test
    @Order(3)
    void server_rapidFireRequests_noDrops() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                int requestCount = 100;
                int successes = 0;

                for (int i = 0; i < requestCount; i++) {
                    client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n");

                    // Read status line
                    String statusLine = client.readLine();
                    if (statusLine == null) break;

                    // Read headers until blank line
                    int contentLength = -1;
                    String headerLine;
                    while ((headerLine = client.readLine()) != null && !headerLine.isEmpty()) {
                        if (headerLine.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                        }
                    }

                    // Read the body
                    if (contentLength > 0) {
                        byte[] body = client.readBytes(contentLength);
                        String bodyStr = new String(body, US_ASCII);
                        if (statusLine.contains("200") && bodyStr.contains("Hello")) {
                            successes++;
                        }
                    }
                }

                assertThat(successes)
                        .as("Sending 100 sequential requests over a single keep-alive connection "
                          + "must all succeed. This tests HTTP/1.1 persistent connection handling "
                          + "and verifies the server's request-processing loop is robust under load.")
                        .isEqualTo(requestCount);
            }
        }
    }
}

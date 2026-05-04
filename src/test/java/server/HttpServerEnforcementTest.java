package server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforcement tests that verify implementation quality: virtual thread usage,
 * resource cleanup, and handling of fragmented TCP input.
 */
@DisplayName("Module 4: HTTP Server Enforcement Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpServerEnforcementTest {

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
    void server_usesVirtualThreads_lowPlatformThreadCount() throws Exception {
        Router router = new Router();
        // Handler that sleeps to keep the connection alive
        router.addRoute("GET", "/hold", (req, out) -> {
            try { Thread.sleep(3_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body = "done".getBytes(US_ASCII);
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "\r\n").getBytes(US_ASCII));
            out.write(body);
            out.flush();
        });

        try (HttpServer server = startTestServer(router)) {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            int baselineThreadCount = threadBean.getThreadCount();

            int connectionCount = 50;
            CountDownLatch allConnected = new CountDownLatch(connectionCount);
            CountDownLatch holdOpen = new CountDownLatch(1);

            // Open 50 concurrent connections that each hold open for a while
            Thread[] connectors = new Thread[connectionCount];
            for (int i = 0; i < connectionCount; i++) {
                connectors[i] = Thread.startVirtualThread(() -> {
                    try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                        client.send("GET /hold HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
                        allConnected.countDown();
                        holdOpen.await(5, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                });
            }

            assertThat(allConnected.await(5, TimeUnit.SECONDS))
                    .as("All 50 connections should be established within 5 seconds.")
                    .isTrue();

            // Give the OS a moment to reflect thread creation
            Thread.sleep(500);

            int peakThreadCount = threadBean.getThreadCount();
            int threadIncrease = peakThreadCount - baselineThreadCount;

            // Release the held connections
            holdOpen.countDown();
            for (Thread t : connectors) {
                t.join(5_000);
            }

            assertThat(threadIncrease)
                    .as("Your server must use virtual threads (Thread.ofVirtual().start(...)). "
                      + "With 50 concurrent connections, platform threads should not increase by 50. "
                      + "Virtual threads multiplex onto a small carrier pool. "
                      + "Baseline: %d, peak: %d, increase: %d.",
                            baselineThreadCount, peakThreadCount, threadIncrease)
                    .isLessThan(50);
        }
    }

    @Test
    @Order(2)
    void server_closesSocketsAfterManyConnections() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            int port = server.getPort();

            // Open and close 200 connections rapidly
            for (int i = 0; i < 200; i++) {
                try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                    client.send("GET /hello HTTP/1.1\r\n"
                              + "Host: localhost\r\n"
                              + "Connection: close\r\n"
                              + "\r\n");
                    // Read the full response to ensure the server finishes
                    String statusLine = client.readLine();
                    if (statusLine != null) {
                        client.readAll();
                    }
                }
            }

            // After 200 rapid connections, one more should still succeed
            try (RawTcpClient client = new RawTcpClient("localhost", port)) {
                client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
                String statusLine = client.readLine();

                assertThat(statusLine)
                        .as("Every accepted socket must be closed after the connection ends. "
                          + "Use try-with-resources. Leaked file descriptors will exhaust the OS limit. "
                          + "After 200 connections, the server must still accept new ones.")
                        .isNotNull()
                        .contains("200");
            }
        }
    }

    @Test
    @Order(3)
    void server_handlesFragmentedRequest() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (Socket socket = new Socket("localhost", server.getPort())) {
                socket.setSoTimeout(5_000);
                OutputStream out = socket.getOutputStream();

                // Send a valid HTTP request in tiny 3-byte chunks
                byte[] request = "GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                        .getBytes(US_ASCII);

                for (int i = 0; i < request.length; i += 3) {
                    int len = Math.min(3, request.length - i);
                    out.write(request, i, len);
                    out.flush();
                    Thread.sleep(50);
                }

                // Read the response
                StringBuilder response = new StringBuilder();
                int b;
                while ((b = socket.getInputStream().read()) != -1) {
                    response.append((char) b);
                }
                String fullResponse = response.toString();

                assertThat(fullResponse)
                        .as("TCP delivers bytes in arbitrary chunks. Your server's parser must handle "
                          + "partial reads. This request was sent in 3-byte fragments with 50ms delays. "
                          + "The server must still reassemble and parse the complete request. "
                          + "See Conceptual Foundation 1: TCP is a byte stream.")
                        .contains("HTTP/1.1 200");

                assertThat(fullResponse)
                        .as("The response body should contain the expected content despite "
                          + "the request arriving in fragments.")
                        .contains("Hello, World!");
            }
        }
    }
}

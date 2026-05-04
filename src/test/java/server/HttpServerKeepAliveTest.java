package server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests HTTP/1.1 persistent connection (keep-alive) behavior.
 * A conforming HTTP/1.1 server must keep the connection open by default
 * and close it when the client sends "Connection: close".
 */
@DisplayName("Module 4: HTTP Server Keep-Alive Tests (E4.3)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpServerKeepAliveTest {

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

    /**
     * Reads one complete HTTP response from the client: status line, headers,
     * and body (based on Content-Length). Returns the body as a string.
     */
    private static String readFullResponse(RawTcpClient client) throws Exception {
        String statusLine = client.readLine();
        if (statusLine == null) return null;

        int contentLength = 0;
        String headerLine;
        while ((headerLine = client.readLine()) != null && !headerLine.isEmpty()) {
            if (headerLine.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
            }
        }

        if (contentLength > 0) {
            byte[] body = client.readBytes(contentLength);
            return new String(body, US_ASCII);
        }
        return "";
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void server_keepAlive_multipleRequestsOnOneConnection() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                int requestCount = 3;
                for (int i = 0; i < requestCount; i++) {
                    client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n");

                    String body = readFullResponse(client);

                    assertThat(body)
                            .as("HTTP/1.1 connections are persistent by default. The server must handle "
                              + "multiple requests on the same TCP connection. Request #%d of %d "
                              + "should return 'Hello, World!' but got '%s'. "
                              + "See course.md Module 2: Persistent Connections.",
                                    i + 1, requestCount, body)
                            .isEqualTo("Hello, World!");
                }
            }
        }
    }

    @Test
    @Order(2)
    void server_connectionClose_closesAfterResponse() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.send("GET /hello HTTP/1.1\r\n"
                          + "Host: localhost\r\n"
                          + "Connection: close\r\n"
                          + "\r\n");

                String body = readFullResponse(client);
                assertThat(body)
                        .as("The response body should still be correct even with Connection: close.")
                        .isEqualTo("Hello, World!");

                // After Connection: close, the server should close the socket.
                // A subsequent read should return null (EOF).
                String nextLine = client.readLine();

                assertThat(nextLine)
                        .as("After the client sends 'Connection: close', the server must close the "
                          + "TCP connection once the response is sent. The next read should return "
                          + "null (EOF). If the server keeps the connection open, it violates "
                          + "RFC 9112 Section 9.6.")
                        .isNull();
            }
        }
    }

    @Test
    @Order(3)
    void server_defaultKeepAlive_http11() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                // First request -- no Connection header at all
                client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n");
                String body1 = readFullResponse(client);

                assertThat(body1)
                        .as("First request on the connection should succeed.")
                        .isEqualTo("Hello, World!");

                // Second request on the same connection -- proves keep-alive is default
                client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n");
                String body2 = readFullResponse(client);

                assertThat(body2)
                        .as("HTTP/1.1 defaults to keep-alive when no Connection header is present. "
                          + "The server must keep the connection open and accept a second request. "
                          + "If this fails, the server is closing the connection after every response, "
                          + "which is HTTP/1.0 behavior.")
                        .isEqualTo("Hello, World!");
            }
        }
    }
}

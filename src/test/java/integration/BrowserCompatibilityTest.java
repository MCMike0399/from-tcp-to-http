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

import java.net.URI;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capstone integration tests that verify the HTTP server works with Java's
 * built-in {@link java.net.http.HttpClient}. If these tests pass, the server
 * is compatible with a real, production-grade HTTP client implementation --
 * not just raw TCP bytes we craft by hand.
 */
@DisplayName("Module 6: Browser Compatibility Tests (java.net.http.HttpClient)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrowserCompatibilityTest {

    // ---------------------------------------------------------------
    // Shared server lifecycle
    // ---------------------------------------------------------------

    private static HttpServer server;
    private static int port;

    /**
     * A real HTTP client. Uses HTTP/1.1 persistent connections by default.
     */
    private static final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

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
        router.addRoute("POST", "/echo", (req, out) -> {
            byte[] body = req.getBody();
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/octet-stream\r\n"
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
    void browser_httpClientGet_succeeds() throws Exception {
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/hello"))
                .GET()
                .build();

        java.net.http.HttpResponse<String> resp = httpClient.send(
                req, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode())
                .as("Your server must produce responses that Java's HttpClient can parse. "
                  + "HttpClient is a strict, standards-compliant HTTP/1.1 client. "
                  + "If this fails, your response format has a subtle protocol error -- "
                  + "a missing CRLF, wrong Content-Length, or malformed status line.")
                .isEqualTo(200);

        assertThat(resp.body())
                .as("The body returned by HttpClient must match what the handler wrote. "
                  + "If the status is 200 but the body is wrong, check that Content-Length "
                  + "matches the actual body byte count.")
                .isEqualTo("Hello, World!");
    }

    @Test
    @Order(2)
    void browser_httpClientPost_bodyRoundTrips() throws Exception {
        String payload = "Test Body Content";

        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/echo"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                .header("Content-Type", "text/plain")
                .build();

        java.net.http.HttpResponse<String> resp = httpClient.send(
                req, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode())
                .as("POST /echo should return 200. If you get a different status, "
                  + "the server may not be reading the request body correctly. "
                  + "Check that your RequestParser reads Content-Length bytes from the input stream.")
                .isEqualTo(200);

        assertThat(resp.body())
                .as("The echo handler must read the request body (HttpRequest.getBody()) and "
                  + "write it back verbatim. If the body is truncated, Content-Length parsing "
                  + "in your request parser may be off. If the body is empty, getBody() may "
                  + "not be wired to the parsed bytes.")
                .isEqualTo(payload);
    }

    @Test
    @Order(3)
    void browser_httpClientKeepAlive_multipleRequests() throws Exception {
        // Java's HttpClient uses HTTP/1.1 persistent connections by default.
        // Send 5 requests through the same client, which reuses connections.
        int requestCount = 5;

        for (int i = 0; i < requestCount; i++) {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/hello"))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> resp = httpClient.send(
                    req, java.net.http.HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode())
                    .as("Java's HttpClient uses HTTP/1.1 persistent connections by default. "
                      + "Your server must handle multiple requests on the same TCP connection. "
                      + "Request %d of %d failed with status %d. "
                      + "If only the first request succeeds, your server likely closes the "
                      + "connection after each response instead of looping to read the next request.",
                            i + 1, requestCount, resp.statusCode())
                    .isEqualTo(200);

            assertThat(resp.body())
                    .as("Request %d of %d: body mismatch on persistent connection.", i + 1, requestCount)
                    .isEqualTo("Hello, World!");
        }
    }
}

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

import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capstone integration tests that verify the complete HTTP server produces
 * responses conforming to RFC 9112. Each test sends raw bytes on the wire
 * and inspects the response at the protocol level.
 */
@DisplayName("Module 6: RFC 9112 Compliance Tests (Capstone)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RfcComplianceTest {

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
    void rfc_responseStatusLineFormat() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            String statusLine = client.readLine();

            assertThat(statusLine)
                    .as("The status line MUST match the format 'HTTP-version SP status-code SP reason-phrase'. "
                      + "RFC 9112 Section 4 defines: status-line = HTTP-version SP status-code SP [reason-phrase]. "
                      + "Expected 'HTTP/1.1 200 OK' but got '%s'.", statusLine)
                    .isNotNull()
                    .matches("HTTP/1\\.1 \\d{3} .+");

            assertThat(statusLine)
                    .as("GET /hello should produce status 200 OK.")
                    .isEqualTo("HTTP/1.1 200 OK");
        }
    }

    @Test
    @Order(2)
    void rfc_contentLengthMatchesBody() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

            // Read status line
            String statusLine = client.readLine();
            assertThat(statusLine).isNotNull().startsWith("HTTP/1.1 200");

            // Read headers, extract Content-Length
            int contentLength = -1;
            String headerLine;
            while ((headerLine = client.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":", 2)[1].trim());
                }
            }

            assertThat(contentLength)
                    .as("The response must include a Content-Length header so the client "
                      + "knows exactly how many body bytes to read. "
                      + "RFC 9112 Section 6.3.")
                    .isGreaterThanOrEqualTo(0);

            // Read exactly Content-Length bytes
            byte[] body = client.readBytes(contentLength);
            String bodyStr = new String(body, US_ASCII);

            assertThat(bodyStr)
                    .as("Content-Length MUST match the exact byte count of the body. "
                      + "Off-by-one causes the next request on a persistent connection to mismatch. "
                      + "RFC 9112 Section 6.3.")
                    .isEqualTo("Hello, World!");

            assertThat(contentLength)
                    .as("Content-Length value (%d) must equal the actual body size (%d). "
                      + "A mismatch breaks persistent connections because the client will "
                      + "try to parse leftover body bytes as the next request line.",
                            contentLength, "Hello, World!".length())
                    .isEqualTo("Hello, World!".getBytes(US_ASCII).length);
        }
    }

    @Test
    @Order(3)
    void rfc_headersEndWithBlankLine() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

            // Read status line
            String statusLine = client.readLine();
            assertThat(statusLine).isNotNull();

            // Read headers until we find the blank line separator.
            // RawTcpClient.readLine() strips the CRLF, so the blank line
            // appears as an empty string "".
            boolean foundBlankLine = false;
            int headerCount = 0;
            String line;
            while ((line = client.readLine()) != null) {
                if (line.isEmpty()) {
                    foundBlankLine = true;
                    break;
                }
                headerCount++;
            }

            assertThat(foundBlankLine)
                    .as("The empty line (\\r\\n\\r\\n) separates headers from body. "
                      + "This is the most important byte sequence in HTTP/1.1. "
                      + "Without it, the client cannot know where headers end and body begins. "
                      + "Read %d header lines but never encountered the blank separator.", headerCount)
                    .isTrue();
        }
    }

    @Test
    @Order(4)
    void rfc_missingHostHeader_returns400() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            // Send an HTTP/1.1 request with NO Host header
            client.send("GET /hello HTTP/1.1\r\n\r\n");
            String statusLine = client.readLine();

            assertThat(statusLine)
                    .as("A server MUST respond with a 400 (Bad Request) status code to any "
                      + "HTTP/1.1 request message that lacks a Host header field. "
                      + "RFC 9112 Section 3.2. Got status line: '%s'.", statusLine)
                    .isNotNull()
                    .contains("400");
        }
    }

    @Test
    @Order(5)
    void rfc_unknownMethod_handledGracefully() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            client.send("PROPFIND / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            String statusLine = client.readLine();

            assertThat(statusLine)
                    .as("An unknown method (PROPFIND) should return 501 Not Implemented or "
                      + "405 Method Not Allowed. The server must NOT crash. "
                      + "Either status code is acceptable per RFC 9110 Section 15.6.2. "
                      + "Got status line: '%s'.", statusLine)
                    .isNotNull()
                    .satisfiesAnyOf(
                            s -> assertThat(s).contains("501"),
                            s -> assertThat(s).contains("405"),
                            s -> assertThat(s).contains("404")
                    );
        }
    }

    @Test
    @Order(6)
    void rfc_veryLongUri_returns414() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            // Build a URI with 16384 'A' characters
            String longPath = "/" + "A".repeat(16384);
            client.send("GET " + longPath + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            String statusLine = client.readLine();

            assertThat(statusLine)
                    .as("Servers must protect against resource exhaustion from oversized URIs. "
                      + "RFC 9112 Section 3: a server SHOULD respond with 414 if the URI is longer "
                      + "than the server is willing to handle. Any 4xx error is acceptable, but the "
                      + "server must NOT crash or OOM. Got status line: '%s'.", statusLine)
                    .isNotNull()
                    .satisfiesAnyOf(
                            s -> assertThat(s).contains("414"),
                            s -> assertThat(s).matches("HTTP/1\\.1 4\\d{2} .+")
                    );
        }
    }

    @Test
    @Order(7)
    void rfc_veryLongHeaderLine_returns431() throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", port)) {
            // Build a header with a 16384-byte value
            String bigHeaderValue = "A".repeat(16384);
            client.send("GET / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "X-Big: " + bigHeaderValue + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n");
            String statusLine = client.readLine();

            assertThat(statusLine)
                    .as("Servers must protect against resource exhaustion from oversized headers. "
                      + "RFC 9110 Section 15.5.32: 431 Request Header Fields Too Large. "
                      + "Any 4xx error is acceptable, but the server must NOT crash or OOM. "
                      + "Got status line: '%s'.", statusLine)
                    .isNotNull()
                    .satisfiesAnyOf(
                            s -> assertThat(s).contains("431"),
                            s -> assertThat(s).matches("HTTP/1\\.1 4\\d{2} .+")
                    );
        }
    }
}

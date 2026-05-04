package server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Validates basic functional correctness of the HTTP server: startup,
 * request handling, routing to handlers, and error responses.
 */
@DisplayName("Module 4: HTTP Server Basic Tests (E4.1, E4.2)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpServerBasicTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static HttpServer startTestServer(Router router) throws Exception {
        HttpServer server = new HttpServer(0, router);
        server.start();
        Thread.sleep(200); // let the accept loop begin
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
        router.addRoute("GET", "/health", (req, out) -> {
            byte[] body = "{\"status\":\"up\"}".getBytes(US_ASCII);
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "\r\n").getBytes(US_ASCII));
            out.write(body);
            out.flush();
        });
        router.addRoute("POST", "/echo", (req, out) -> {
            byte[] body = req.getBody();
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Content-Type: application/octet-stream\r\n"
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
    void server_startsAndAcceptsConnection() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            assertThatCode(() -> {
                try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                    // Connection established successfully
                }
            })
            .as("The server must accept TCP connections after start() returns. "
              + "This requires a ServerSocket bound to the port and an accept loop "
              + "running on a background thread.")
            .doesNotThrowAnyException();
        }
    }

    @Test
    @Order(2)
    void server_respondsWithValidStatusLine() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n");
                String statusLine = client.readLine();

                assertThat(statusLine)
                        .as("The first line of an HTTP response must be a status line: "
                          + "'HTTP/1.1 <status-code> <reason>'. Your server parsed the request, "
                          + "routed to the /hello handler, and the handler wrote the response. "
                          + "Expected 'HTTP/1.1 200 OK' but got '%s'.", statusLine)
                        .isEqualTo("HTTP/1.1 200 OK");
            }
        }
    }

    @Test
    @Order(3)
    void server_responds200ForKnownRoute() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.send("GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n");
                String statusLine = client.readLine();

                assertThat(statusLine)
                        .as("GET /hello is registered in the router and should produce a 200 response. "
                          + "The server must parse the request, look up the handler via "
                          + "Router.findHandler(), and invoke it.")
                        .startsWith("HTTP/1.1 200");
            }
        }
    }

    @Test
    @Order(4)
    void server_responds404ForUnknownRoute() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.send("GET /nonexistent HTTP/1.1\r\nHost: localhost\r\n\r\n");
                String statusLine = client.readLine();

                assertThat(statusLine)
                        .as("When Router.findHandler() returns null (no matching route), "
                          + "the server must generate a 404 Not Found response itself. "
                          + "This is the server's responsibility, not the router's.")
                        .isNotNull()
                        .contains("404");
            }
        }
    }

    @Test
    @Order(5)
    void server_responds400ForMalformedRequest() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.send("GARBAGE\r\n\r\n");
                String statusLine = client.readLine();

                assertThat(statusLine)
                        .as("A malformed request line should cause RequestParser to throw "
                          + "ProtocolException. The server must catch that exception and return "
                          + "a 400 Bad Request response instead of crashing or hanging.")
                        .isNotNull()
                        .contains("400");
            }
        }
    }

    @Test
    @Order(6)
    void server_postEcho_returnsRequestBody() throws Exception {
        try (HttpServer server = startTestServer(defaultRouter())) {
            try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                client.send("POST /echo HTTP/1.1\r\n"
                          + "Host: localhost\r\n"
                          + "Content-Length: 5\r\n"
                          + "\r\n"
                          + "Hello");

                // Read the status line
                String statusLine = client.readLine();
                assertThat(statusLine)
                        .as("POST /echo should return 200.")
                        .startsWith("HTTP/1.1 200");

                // Read headers until blank line
                int contentLength = -1;
                String headerLine;
                while ((headerLine = client.readLine()) != null && !headerLine.isEmpty()) {
                    if (headerLine.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                    }
                }

                assertThat(contentLength)
                        .as("The echo handler must include a Content-Length header.")
                        .isGreaterThan(0);

                // Read the body
                byte[] body = client.readBytes(contentLength);
                String bodyStr = new String(body, US_ASCII);

                assertThat(bodyStr)
                        .as("The /echo handler receives the request body via HttpRequest.getBody() "
                          + "and writes it back. The server must parse Content-Length from the "
                          + "request headers and read that many bytes into the request body.")
                        .isEqualTo("Hello");
            }
        }
    }
}

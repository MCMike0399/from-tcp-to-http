package response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import server.HttpServer;
import server.Router;
import testutil.RawTcpClient;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests content negotiation behavior: the server should inspect the Accept
 * header and return the most appropriate Content-Type.
 * <p>
 * These tests start a real HTTP server with a /data endpoint that supports
 * multiple representations (JSON and HTML). Requests with different Accept
 * headers should receive different Content-Types.
 * <p>
 * References: RFC 9110 Section 12.5.1 (Accept), RFC 9110 Section 15.5.7 (406).
 */
@DisplayName("Module 5: Content Negotiation Tests (E5.4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContentNegotiationTest {

    private HttpServer server;

    // ---------------------------------------------------------------
    // Setup / Teardown
    // ---------------------------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        Router router = negotiatingRouter();
        server = new HttpServer(0, router);
        server.start();
        Thread.sleep(200); // let the accept loop begin
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    /**
     * Creates a router with a /data endpoint that supports content negotiation.
     * The handler inspects the Accept header and returns either JSON or HTML.
     * If the Accept header requests an unsupported type, it returns 406.
     */
    private static Router negotiatingRouter() {
        Router router = new Router();
        router.addRoute("GET", "/data", (req, out) -> {
            String accept = req.getHeader("accept");

            String contentType;
            byte[] body;

            if (accept == null || accept.contains("*/*")) {
                // Default: return HTML
                contentType = "text/html";
                body = "<p>hello</p>".getBytes(US_ASCII);
            } else if (supportsJson(accept) && supportsHtml(accept)) {
                // Both requested: pick the one with higher quality factor
                if (jsonPreferred(accept)) {
                    contentType = "application/json";
                    body = "{\"message\":\"hello\"}".getBytes(US_ASCII);
                } else {
                    contentType = "text/html";
                    body = "<p>hello</p>".getBytes(US_ASCII);
                }
            } else if (supportsJson(accept)) {
                contentType = "application/json";
                body = "{\"message\":\"hello\"}".getBytes(US_ASCII);
            } else if (supportsHtml(accept)) {
                contentType = "text/html";
                body = "<p>hello</p>".getBytes(US_ASCII);
            } else {
                // 406 Not Acceptable
                body = "Not Acceptable".getBytes(US_ASCII);
                out.write(("HTTP/1.1 406 Not Acceptable\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "\r\n").getBytes(US_ASCII));
                out.write(body);
                out.flush();
                return;
            }

            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "\r\n").getBytes(US_ASCII));
            out.write(body);
            out.flush();
        });
        return router;
    }

    private static boolean supportsJson(String accept) {
        return accept.contains("application/json");
    }

    private static boolean supportsHtml(String accept) {
        return accept.contains("text/html");
    }

    /**
     * Very simplified quality factor check: returns true if JSON has a higher
     * q-value than HTML in the Accept header.
     */
    private static boolean jsonPreferred(String accept) {
        double jsonQ = extractQuality(accept, "application/json");
        double htmlQ = extractQuality(accept, "text/html");
        return jsonQ > htmlQ;
    }

    private static double extractQuality(String accept, String mediaType) {
        int idx = accept.indexOf(mediaType);
        if (idx < 0) return 0.0;
        // Look for ;q= after the media type
        int afterType = idx + mediaType.length();
        int commaIdx = accept.indexOf(',', afterType);
        String segment = (commaIdx >= 0)
                ? accept.substring(afterType, commaIdx)
                : accept.substring(afterType);
        int qIdx = segment.indexOf(";q=");
        if (qIdx >= 0) {
            String qStr = segment.substring(qIdx + 3).trim();
            try {
                return Double.parseDouble(qStr);
            } catch (NumberFormatException e) {
                return 1.0;
            }
        }
        return 1.0; // default quality is 1.0
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Sends a GET /data request with the given Accept header and returns
     * the full response headers as a single string (up to the blank line).
     */
    private String sendRequestAndGetHeaders(String acceptHeader) throws Exception {
        try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
            StringBuilder request = new StringBuilder();
            request.append("GET /data HTTP/1.1\r\n");
            request.append("Host: localhost\r\n");
            if (acceptHeader != null) {
                request.append("Accept: ").append(acceptHeader).append("\r\n");
            }
            request.append("Connection: close\r\n");
            request.append("\r\n");

            client.send(request.toString());

            StringBuilder headers = new StringBuilder();
            String line;
            while ((line = client.readLine()) != null && !line.isEmpty()) {
                headers.append(line).append("\r\n");
            }
            return headers.toString();
        }
    }

    /**
     * Extracts the status code from the response headers.
     */
    private static int extractStatusCode(String headers) {
        // "HTTP/1.1 200 OK\r\n..."
        String statusLine = headers.substring(0, headers.indexOf("\r\n"));
        String[] parts = statusLine.split(" ");
        return Integer.parseInt(parts[1]);
    }

    /**
     * Extracts the Content-Type value from the response headers.
     */
    private static String extractContentType(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-type:")) {
                return line.substring("content-type:".length()).trim();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void negotiation_acceptJson_returnsJson() throws Exception {
        String headers = sendRequestAndGetHeaders("application/json");

        int status = extractStatusCode(headers);
        assertThat(status)
                .as("A request with Accept: application/json should succeed with 200.")
                .isEqualTo(200);

        String contentType = extractContentType(headers);
        assertThat(contentType)
                .as("When the client sends Accept: application/json, the server must respond "
                  + "with Content-Type: application/json. Content negotiation means the server "
                  + "inspects the Accept header and returns the matching representation.")
                .contains("application/json");
    }

    @Test
    @Order(2)
    void negotiation_acceptHtml_returnsHtml() throws Exception {
        String headers = sendRequestAndGetHeaders("text/html");

        int status = extractStatusCode(headers);
        assertThat(status)
                .as("A request with Accept: text/html should succeed with 200.")
                .isEqualTo(200);

        String contentType = extractContentType(headers);
        assertThat(contentType)
                .as("When the client sends Accept: text/html, the server must respond "
                  + "with Content-Type: text/html. The same /data endpoint returns different "
                  + "representations based on what the client can accept.")
                .contains("text/html");
    }

    @Test
    @Order(3)
    void negotiation_acceptAll_returnsDefault() throws Exception {
        String headers = sendRequestAndGetHeaders("*/*");

        int status = extractStatusCode(headers);
        assertThat(status)
                .as("Accept: */* means the client accepts any content type. "
                  + "The server should return 200 with its default representation.")
                .isEqualTo(200);

        String contentType = extractContentType(headers);
        assertThat(contentType)
                .as("When Accept: */* is sent, the server should return its default type. "
                  + "The specific default (HTML or JSON) is an implementation choice, "
                  + "but a Content-Type header must be present.")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @Order(4)
    void negotiation_qualityFactors_respectsPriority() throws Exception {
        // JSON has q=1.0, HTML has q=0.9 -> JSON is preferred
        String headers = sendRequestAndGetHeaders("text/html;q=0.9, application/json;q=1.0");

        int status = extractStatusCode(headers);
        assertThat(status)
                .as("Both types are acceptable, so the server should return 200.")
                .isEqualTo(200);

        String contentType = extractContentType(headers);
        assertThat(contentType)
                .as("Quality factors (q=) express client preference. Higher q means preferred. "
                  + "See RFC 9110 Section 12.5.1. "
                  + "With 'text/html;q=0.9, application/json;q=1.0', JSON has higher quality "
                  + "(1.0 > 0.9), so the server should prefer application/json.")
                .contains("application/json");
    }

    @Test
    @Order(5)
    void negotiation_noAcceptHeader_returnsDefault() throws Exception {
        String headers = sendRequestAndGetHeaders(null);

        int status = extractStatusCode(headers);
        assertThat(status)
                .as("When no Accept header is present, the server should return 200 "
                  + "with its default representation. Per RFC 9110 Section 12.5.1, "
                  + "the absence of an Accept header means the client accepts any type.")
                .isEqualTo(200);

        String contentType = extractContentType(headers);
        assertThat(contentType)
                .as("Even without an Accept header, the response must include a Content-Type "
                  + "so the client knows how to interpret the body.")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @Order(6)
    void negotiation_unsupportedType_returns406() throws Exception {
        String headers = sendRequestAndGetHeaders("application/xml");

        int status = extractStatusCode(headers);
        assertThat(status)
                .as("When the server cannot produce any of the requested types, "
                  + "it should return 406 Not Acceptable. RFC 9110 Section 15.5.7: "
                  + "'The origin server MUST generate a 406 response if it does not have "
                  + "a current representation that would be acceptable to the user agent'. "
                  + "The /data endpoint supports JSON and HTML but not XML.")
                .isEqualTo(406);
    }
}

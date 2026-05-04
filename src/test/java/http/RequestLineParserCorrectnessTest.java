package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that the RequestParser correctly parses well-formed HTTP/1.1 requests.
 * Each test constructs raw HTTP bytes, feeds them through the parser, and asserts
 * the returned HttpRequest has the expected method, URI, version, headers, and body.
 */
@DisplayName("Module 3: Request Line Parser Correctness Tests (E3.1)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestLineParserCorrectnessTest {

    @Test
    @Order(1)
    void parse_simpleGet_extractsMethodUriVersion() throws IOException {
        byte[] raw = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getMethod())
                .as("The method token is the first element of the request line (RFC 9112 Section 3). "
                  + "For 'GET / HTTP/1.1', the method must be 'GET'.")
                .isEqualTo("GET");

        assertThat(req.getUri())
                .as("The request-target is the second element of the request line. "
                  + "For 'GET / HTTP/1.1', the URI must be '/'.")
                .isEqualTo("/");

        assertThat(req.getVersion())
                .as("The HTTP-version is the third element of the request line. "
                  + "For 'GET / HTTP/1.1', the version must be 'HTTP/1.1'.")
                .isEqualTo("HTTP/1.1");
    }

    @Test
    @Order(2)
    void parse_getWithPath_extractsUri() throws IOException {
        byte[] raw = "GET /hello/world HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getUri())
                .as("The URI must preserve the full path including all segments.")
                .isEqualTo("/hello/world");
    }

    @Test
    @Order(3)
    void parse_postMethod_extracted() throws IOException {
        byte[] raw = "POST /data HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getMethod())
                .as("The parser must handle POST as a valid method token.")
                .isEqualTo("POST");
    }

    @Test
    @Order(4)
    void parse_putAndDelete_extracted() throws IOException {
        byte[] rawPut = "PUT /resource HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest putReq = new RequestParser().parse(new ByteArrayInputStream(rawPut));

        assertThat(putReq.getMethod())
                .as("The parser must handle PUT as a valid method token.")
                .isEqualTo("PUT");

        byte[] rawDelete = "DELETE /resource HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest deleteReq = new RequestParser().parse(new ByteArrayInputStream(rawDelete));

        assertThat(deleteReq.getMethod())
                .as("The parser must handle DELETE as a valid method token.")
                .isEqualTo("DELETE");
    }

    @Test
    @Order(5)
    void parse_withQueryString_preservesQuery() throws IOException {
        byte[] raw = "GET /search?q=java&page=1 HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getUri())
                .as("The request-target includes the query string. "
                  + "RFC 9112 Section 3.2: the request-target is sent as-is; "
                  + "the parser must not strip or decode the query component.")
                .isEqualTo("/search?q=java&page=1");
    }

    @Test
    @Order(6)
    void parse_http10_extractsVersion() throws IOException {
        byte[] raw = "GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getVersion())
                .as("The parser must correctly extract HTTP/1.0 as the version string. "
                  + "While we target HTTP/1.1, recognizing 1.0 is needed for interoperability.")
                .isEqualTo("HTTP/1.0");
    }

    @Test
    @Order(7)
    void parse_withMultipleHeaders_extractsAll() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: text/plain\r\n"
                + "Accept: application/json\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeader("Host"))
                .as("The parser must extract the Host header value.")
                .isEqualTo("localhost");

        assertThat(req.getHeader("Content-Type"))
                .as("The parser must extract the Content-Type header value.")
                .isEqualTo("text/plain");

        assertThat(req.getHeader("Accept"))
                .as("The parser must extract the Accept header value.")
                .isEqualTo("application/json");
    }

    @Test
    @Order(8)
    void parse_duplicateHeaders_preservedAsList() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Accept: text/html\r\n"
                + "Accept: application/json\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeaderValues("Accept"))
                .as("Multiple headers with the same name must be preserved as a list, not overwritten. "
                  + "RFC 9110: a recipient MUST handle multiple header fields with the same name.")
                .containsExactly("text/html", "application/json");
    }

    @Test
    @Order(9)
    void parse_headerNamesCaseInsensitive() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeader("content-type"))
                .as("Header name lookup must be case-insensitive per RFC 9110 Section 5.1: "
                  + "'Field names are case-insensitive.'")
                .isEqualTo("text/plain");

        assertThat(req.getHeader("CONTENT-TYPE"))
                .as("Header name lookup in all-caps must also match.")
                .isEqualTo("text/plain");
    }

    @Test
    @Order(10)
    void parse_headerValueWhitespaceTrimmed() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host:   localhost   \r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeader("Host"))
                .as("OWS (optional whitespace) around header values must be trimmed. RFC 9112 Section 5.")
                .isEqualTo("localhost");
    }

    @Test
    @Order(11)
    void parse_withContentLengthBody_readsExactBytes() throws IOException {
        byte[] raw = ("POST /data HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 5\r\n"
                + "\r\n"
                + "Hello").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getBody())
                .as("When Content-Length is present, the parser must read exactly that many bytes "
                  + "from the stream as the message body (RFC 9112 Section 6.2).")
                .isEqualTo("Hello".getBytes(US_ASCII));
    }

    @Test
    @Order(12)
    void parse_emptyBody_noContentLength() throws IOException {
        byte[] raw = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getBody())
                .as("A GET request with no Content-Length header should have an empty body. "
                  + "RFC 9112 Section 6: if no Transfer-Encoding and no Content-Length, body length is zero.")
                .isEmpty();
    }

    @Test
    @Order(13)
    void parse_binaryBody_preservesExactBytes() throws IOException {
        byte[] binaryBody = new byte[]{0x00, (byte) 0xFF, 0x0A, 0x0D};
        byte[] requestHead = ("POST /data HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 4\r\n"
                + "\r\n").getBytes(US_ASCII);

        byte[] raw = new byte[requestHead.length + binaryBody.length];
        System.arraycopy(requestHead, 0, raw, 0, requestHead.length);
        System.arraycopy(binaryBody, 0, raw, requestHead.length, binaryBody.length);

        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getBody())
                .as("HTTP bodies are raw bytes. The parser must not interpret body bytes as text "
                  + "or strip newlines from the body. Only headers use CRLF framing.")
                .isEqualTo(binaryBody);
    }
}

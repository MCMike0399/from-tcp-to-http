package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests edge cases in header parsing: empty header blocks, many headers,
 * very long values, colons in values, and empty values.
 */
@DisplayName("Module 3: Header Parser Edge Case Tests (E3.4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HeaderParserEdgeCaseTest {

    @Test
    @Order(1)
    void parse_noHeaders_emptyMap() throws IOException {
        byte[] raw = "GET / HTTP/1.1\r\n\r\n".getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeaders())
                .as("A request with no header lines (just the blank terminator after the request line) "
                  + "should produce an empty headers map.")
                .isEmpty();
    }

    @Test
    @Order(2)
    void parse_singleHeader_onePair() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeaders())
                .as("A request with exactly one header should produce a map with one entry.")
                .hasSize(1);

        assertThat(req.getHeader("Host"))
                .as("The single Host header value must be 'localhost'.")
                .isEqualTo("localhost");
    }

    @Test
    @Order(3)
    void parse_manyHeaders_allPreserved() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("GET / HTTP/1.1\r\n");
        for (int i = 0; i < 20; i++) {
            sb.append("X-Header-").append(i).append(": value-").append(i).append("\r\n");
        }
        sb.append("\r\n");

        byte[] raw = sb.toString().getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        for (int i = 0; i < 20; i++) {
            String headerName = "X-Header-" + i;
            assertThat(req.getHeader(headerName))
                    .as("Header '%s' must be present with value 'value-%d'. "
                      + "The parser must not silently drop headers when there are many of them.",
                            headerName, i)
                    .isEqualTo("value-" + i);
        }
    }

    @Test
    @Order(4)
    void parse_veryLongHeaderValue_accepted() throws IOException {
        String longValue = "x".repeat(8192);
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "X-Long: " + longValue + "\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeader("X-Long"))
                .as("An 8192-byte header value must be accepted and preserved in full. "
                  + "The parser should not impose an artificially low limit on header value length.")
                .hasSize(8192)
                .isEqualTo(longValue);
    }

    @Test
    @Order(5)
    void parse_headerValueWithColons_preserved() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Location: http://example.com:8080/path\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeader("Location"))
                .as("The header value may contain colons (e.g., in URLs with port numbers). "
                  + "Only the first colon separates the field name from the value. "
                  + "RFC 9112 Section 5: field-value can contain any VCHAR/obs-text including ':'.")
                .isEqualTo("http://example.com:8080/path");
    }

    @Test
    @Order(6)
    void parse_emptyHeaderValue_preserved() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "X-Empty: \r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getHeader("X-Empty"))
                .as("A header with an empty value (just whitespace after the colon, which gets trimmed) "
                  + "must be preserved as an empty string, not null or omitted.")
                .isEqualTo("");
    }

    @Test
    @Order(7)
    void parse_missingHostHeader_http11_throwsProtocolException() {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Accept: text/html\r\n"
                + "\r\n").getBytes(US_ASCII);

        assertThatThrownBy(() -> new RequestParser().parse(new ByteArrayInputStream(raw)))
                .as("RFC 9112 Section 3.2 requires that an HTTP/1.1 request contain a Host header. "
                  + "A server MUST respond with 400 to any HTTP/1.1 request that lacks a Host header.")
                .isInstanceOf(ProtocolException.class);
    }
}

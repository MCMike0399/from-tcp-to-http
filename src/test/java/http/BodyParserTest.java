package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import testutil.ChunkedOnlyInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the body-reading logic of the RequestParser.
 * Verifies that Content-Length bodies are read exactly, that chunked delivery
 * from the underlying stream is handled correctly, and that missing body
 * indicators result in an empty body.
 */
@DisplayName("Module 3: Body Parser Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BodyParserTest {

    @Test
    @Order(1)
    void parse_bodyMatchesContentLength_exact() throws IOException {
        byte[] raw = ("POST /submit HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 13\r\n"
                + "\r\n"
                + "Hello, World!").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getBody())
                .as("The parser must read exactly Content-Length bytes as the body. "
                  + "RFC 9112 Section 6.2: the message body length is determined by "
                  + "the Content-Length field value.")
                .isEqualTo("Hello, World!".getBytes(US_ASCII));
    }

    @Test
    @Order(2)
    void parse_bodyDeliveredInChunks_stillComplete() throws IOException {
        String bodyText = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdef"; // 42 bytes
        byte[] raw = ("POST /upload HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 42\r\n"
                + "\r\n"
                + bodyText).getBytes(US_ASCII);

        // ChunkedOnlyInputStream delivers at most 5 bytes per read() call,
        // simulating TCP fragmentation. The parser must loop and accumulate.
        ChunkedOnlyInputStream chunked = new ChunkedOnlyInputStream(raw, 5);
        HttpRequest req = new RequestParser().parse(chunked);

        assertThat(req.getBody())
                .as("When the underlying stream delivers data in small fragments (as TCP does), "
                  + "the parser must accumulate reads in a loop until exactly Content-Length bytes "
                  + "have been consumed. A single read(byte[]) call may return fewer bytes than requested.")
                .isEqualTo(bodyText.getBytes(US_ASCII));
    }

    @Test
    @Order(3)
    void parse_bodyZeroLength_emptyByteArray() throws IOException {
        byte[] raw = ("POST /empty HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 0\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getBody())
                .as("Content-Length: 0 explicitly indicates no body. "
                  + "The body must be an empty byte array, not null.")
                .isEmpty();
    }

    @Test
    @Order(4)
    void parse_noContentLengthNoBody_emptyByteArray() throws IOException {
        byte[] raw = ("GET /page HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "\r\n").getBytes(US_ASCII);
        HttpRequest req = new RequestParser().parse(new ByteArrayInputStream(raw));

        assertThat(req.getBody())
                .as("A request with no Content-Length and no Transfer-Encoding has a body length of zero. "
                  + "RFC 9112 Section 6.3: if neither is present, the message body length is zero.")
                .isEmpty();
    }
}

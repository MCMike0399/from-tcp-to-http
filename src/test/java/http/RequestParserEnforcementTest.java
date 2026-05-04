package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import testutil.ChunkedOnlyInputStream;
import testutil.PartialInputStream;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforcement tests verify that the parser reads from the InputStream correctly:
 * using bulk read(byte[], int, int) rather than single-byte read(), and handling
 * fragmentation gracefully. These tests use ChunkedOnlyInputStream (which throws
 * on single-byte reads) and PartialInputStream (which returns random-sized chunks).
 */
@DisplayName("Module 3: Request Parser Enforcement Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestParserEnforcementTest {

    @Test
    @Order(1)
    void parse_withChunkedStream_neverCallsSingleByteRead() throws IOException {
        byte[] raw = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        ChunkedOnlyInputStream chunked = new ChunkedOnlyInputStream(raw, 10);

        HttpRequest req = new RequestParser().parse(chunked);

        assertThat(req.getMethod())
                .as("The parser must produce a valid HttpRequest even when reading from "
                  + "a ChunkedOnlyInputStream that forbids single-byte read(). "
                  + "If this test throws UnsupportedOperationException, your parser is calling "
                  + "read() instead of read(byte[], int, int).")
                .isEqualTo("GET");

        assertThat(chunked.getSingleByteReadAttempts())
                .as("The parser must never call read() (single-byte). "
                  + "Single-byte reading defeats TCP buffering and is O(n) in syscalls. "
                  + "Always use read(byte[], int, int) with a buffer.")
                .isZero();
    }

    @Test
    @Order(2)
    void parse_withPartialStream_handlesFragmentation() throws IOException {
        byte[] raw = ("POST /data HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 11\r\n"
                + "\r\n"
                + "Hello World").getBytes(US_ASCII);
        PartialInputStream partial = new PartialInputStream(raw, 42L);

        HttpRequest req = new RequestParser().parse(partial);

        assertThat(req.getMethod())
                .as("The parser must handle random-sized chunks from PartialInputStream. "
                  + "TCP delivers data in unpredictable fragments; the parser must buffer "
                  + "and accumulate until a complete token or line is available.")
                .isEqualTo("POST");

        assertThat(req.getUri())
                .isEqualTo("/data");

        assertThat(req.getBody())
                .isEqualTo("Hello World".getBytes(US_ASCII));
    }

    @Test
    @Order(3)
    void parse_headersSpanningMultipleChunks_parsedCorrectly() throws IOException {
        byte[] raw = ("GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: text/html\r\n"
                + "Accept: application/json\r\n"
                + "\r\n").getBytes(US_ASCII);

        // chunkSize=5 means "Content-Type: text/html\r\n" arrives as:
        // "Conte", "nt-Ty", "pe: t", "ext/h", "tml\r\n" -- split mid-name and mid-value
        ChunkedOnlyInputStream chunked = new ChunkedOnlyInputStream(raw, 5);
        HttpRequest req = new RequestParser().parse(chunked);

        assertThat(req.getHeader("Content-Type"))
                .as("Headers split across multiple small chunks must still be parsed correctly. "
                  + "The parser must use persistent buffering to accumulate partial lines.")
                .isEqualTo("text/html");

        assertThat(req.getHeader("Accept"))
                .isEqualTo("application/json");
    }

    @Test
    @Order(4)
    void parse_requestLineSpanningMultipleChunks_parsedCorrectly() throws IOException {
        byte[] raw = ("GET /hello HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "\r\n").getBytes(US_ASCII);

        // chunkSize=4 means "GET /hello HTTP/1.1\r\n" arrives as:
        // "GET ", "/hel", "lo H", "TTP/", "1.1\r", "\nHos"... -- the request line is fragmented
        ChunkedOnlyInputStream chunked = new ChunkedOnlyInputStream(raw, 4);
        HttpRequest req = new RequestParser().parse(chunked);

        assertThat(req.getMethod())
                .as("The request line may be split across multiple read() calls. "
                  + "The parser must buffer bytes until the complete CRLF-terminated line is available.")
                .isEqualTo("GET");

        assertThat(req.getUri())
                .isEqualTo("/hello");

        assertThat(req.getVersion())
                .isEqualTo("HTTP/1.1");
    }
}

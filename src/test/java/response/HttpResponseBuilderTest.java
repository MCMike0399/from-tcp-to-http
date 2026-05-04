package response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RecordingOutputStream;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the HttpResponse builder and its writeTo() serialization method.
 * <p>
 * The builder is already implemented; the student must implement writeTo()
 * which serializes an HttpResponse into the HTTP/1.1 wire format:
 * <pre>
 *   HTTP/1.1 {status-code} {reason}\r\n
 *   {Header-Name}: {value}\r\n
 *   ...
 *   \r\n
 *   {body bytes}
 * </pre>
 */
@DisplayName("Module 5: HTTP Response Builder & Writer Tests (E5.1)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpResponseBuilderTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Writes the response to a RecordingOutputStream and returns the
     * full output as a UTF-8 string.
     */
    private static String serialize(HttpResponse response) throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();
        response.writeTo(out);
        return out.getAllAsString();
    }

    /**
     * Writes the response and returns the raw bytes.
     */
    private static byte[] serializeBytes(HttpResponse response) throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();
        response.writeTo(out);
        return out.getAllBytes();
    }

    /**
     * Extracts the first line (status line) from a serialized response.
     */
    private static String statusLine(String serialized) {
        int idx = serialized.indexOf("\r\n");
        return (idx >= 0) ? serialized.substring(0, idx) : serialized;
    }

    /**
     * Extracts the header block (between the status line and the blank line)
     * from a serialized response.
     */
    private static String headerBlock(String serialized) {
        int firstCrlf = serialized.indexOf("\r\n");
        int blankLine = serialized.indexOf("\r\n\r\n");
        if (firstCrlf < 0 || blankLine < 0) return "";
        return serialized.substring(firstCrlf + 2, blankLine);
    }

    /**
     * Extracts the body portion (after the blank line) from the raw
     * serialized bytes.
     */
    private static byte[] bodyBytes(byte[] serialized) {
        // Find \r\n\r\n in the byte array
        for (int i = 0; i < serialized.length - 3; i++) {
            if (serialized[i] == '\r' && serialized[i + 1] == '\n'
                    && serialized[i + 2] == '\r' && serialized[i + 3] == '\n') {
                byte[] body = new byte[serialized.length - (i + 4)];
                System.arraycopy(serialized, i + 4, body, 0, body.length);
                return body;
            }
        }
        return new byte[0];
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void response_200Ok_correctStatusLine() throws IOException {
        HttpResponse response = HttpResponse.builder()
                .status(200)
                .body("OK")
                .build();

        String output = serialize(response);

        assertThat(statusLine(output))
                .as("writeTo() must start with the HTTP status line: "
                  + "'HTTP/1.1 {code} {reason}\\r\\n'. For a 200 response, "
                  + "the first line should be 'HTTP/1.1 200 OK'. "
                  + "See RFC 9112 Section 4 for the status-line grammar.")
                .isEqualTo("HTTP/1.1 200 OK");
    }

    @Test
    @Order(2)
    void response_404NotFound_correctStatusLine() throws IOException {
        HttpResponse response = HttpResponse.builder()
                .status(404)
                .body("Not Found")
                .build();

        String output = serialize(response);

        assertThat(statusLine(output))
                .as("The status line must reflect the actual status code and reason phrase. "
                  + "status(404) should auto-fill the reason phrase to 'Not Found'. "
                  + "Expected 'HTTP/1.1 404 Not Found'.")
                .isEqualTo("HTTP/1.1 404 Not Found");
    }

    @Test
    @Order(3)
    void response_withHeaders_allPresentInOutput() throws IOException {
        HttpResponse response = HttpResponse.builder()
                .status(200)
                .header("Content-Type", "text/plain")
                .header("X-Custom", "value")
                .body("test")
                .build();

        String output = serialize(response);
        String headers = headerBlock(output);

        assertThat(headers)
                .as("All headers set via builder.header() must appear in the serialized output "
                  + "between the status line and the blank line. Each header is formatted as "
                  + "'Name: Value\\r\\n'. The Content-Type header should be present.")
                .contains("Content-Type: text/plain");

        assertThat(headers)
                .as("Custom headers (X-Custom) must also be written to the output. "
                  + "writeTo() must iterate all headers from getHeaders(), not just "
                  + "well-known ones.")
                .contains("X-Custom: value");
    }

    @Test
    @Order(4)
    void response_withBody_contentLengthMatchesActualBytes() throws IOException {
        HttpResponse response = HttpResponse.builder()
                .status(200)
                .body("Hello, World!")
                .build();

        String output = serialize(response);
        byte[] rawBytes = serializeBytes(response);
        byte[] body = bodyBytes(rawBytes);

        assertThat(output)
                .as("writeTo() should include a Content-Length header that matches the "
                  + "body size. 'Hello, World!' is 13 bytes in UTF-8. The header must "
                  + "reflect the actual byte count, not the character count (they differ "
                  + "for multi-byte characters).")
                .contains("Content-Length: 13");

        assertThat(body)
                .as("The body bytes after the blank line must match the original body. "
                  + "Expected exactly 13 bytes for 'Hello, World!'.")
                .hasSize(13);

        assertThat(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .as("The body content must be written verbatim after the blank line separator.")
                .isEqualTo("Hello, World!");
    }

    @Test
    @Order(5)
    void response_binaryBody_preservesExactBytes() throws IOException {
        byte[] binaryBody = {0x00, 0x01, (byte) 0xFF, 0x0A, 0x0D};

        HttpResponse response = HttpResponse.builder()
                .status(200)
                .body(binaryBody)
                .build();

        String output = serialize(response);
        byte[] rawBytes = serializeBytes(response);
        byte[] body = bodyBytes(rawBytes);

        assertThat(output)
                .as("Content-Length must reflect the exact byte count of the binary body. "
                  + "The body is 5 bytes: {0x00, 0x01, 0xFF, 0x0A, 0x0D}.")
                .contains("Content-Length: 5");

        assertThat(body)
                .as("HTTP response bodies are raw bytes. writeTo() must not modify body bytes. "
                  + "Even if the body contains \\r\\n, those are data, not protocol delimiters. "
                  + "0x0A is LF and 0x0D is CR -- they must be written as-is, not interpreted "
                  + "as line endings or stripped.")
                .containsExactly(0x00, 0x01, (byte) 0xFF, 0x0A, 0x0D);
    }
}

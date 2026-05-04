package streams;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case tests for boundary conditions that trip up many implementations.
 * These cover single-byte inputs, lone carriage returns, null bytes, and
 * tricky interleaving patterns.
 */
@DisplayName("Module 0: LineReader Edge Case Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineReaderEdgeCaseTest {

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private static LineReader readerOf(String text) {
        return new LineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static LineReader readerOf(byte[] data) {
        return new LineReader(new ByteArrayInputStream(data));
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void readLine_singleByte_LF() throws IOException {
        LineReader reader = readerOf("\n");

        assertThat(reader.readLine())
                .as("A lone \\n byte should return an empty string, not null. "
                  + "In HTTP, \\r\\n\\r\\n marks the end of headers. The empty line "
                  + "between \\r\\n pairs must be returned as empty string ''. "
                  + "Returning null would signal EOF and break HTTP header parsing.")
                .isEqualTo("");
    }

    @Test
    @Order(2)
    void readLine_singleByte_CRLF() throws IOException {
        LineReader reader = readerOf("\r\n");

        assertThat(reader.readLine())
                .as("A lone \\r\\n pair should return an empty string. "
                  + "This is the canonical HTTP empty line. "
                  + "The reader must distinguish between 'no content before delimiter' (empty string) "
                  + "and 'no more data' (null).")
                .isEqualTo("");
    }

    @Test
    @Order(3)
    void readLine_loneCR_treatedAsContent() throws IOException {
        LineReader reader = readerOf("A\rB\n");

        assertThat(reader.readLine())
                .as("A lone \\r (not followed by \\n) is NOT a line ending. "
                  + "RFC 9112 requires \\r\\n as the line terminator. "
                  + "A bare \\r should be treated as ordinary content. "
                  + "The only valid line endings are \\n and \\r\\n.")
                .isEqualTo("A\rB");
    }

    @Test
    @Order(4)
    void readLine_nullByteInLine() throws IOException {
        LineReader reader = readerOf("He\0llo\n");

        assertThat(reader.readLine())
                .as("Null bytes (0x00) are valid in a byte stream and must be preserved. "
                  + "Unlike C strings, Java strings and byte streams have no special meaning "
                  + "for the null byte. The line should contain the null byte as-is.")
                .isEqualTo("He\0llo");
    }

    @Test
    @Order(5)
    void readLine_interleaved_emptyAndNonEmpty() throws IOException {
        LineReader reader = readerOf("\nA\n\nB\n");

        assertThat(reader.readLine())
                .as("First \\n is an empty line -> \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("After empty line, 'A\\n' -> 'A'.")
                .isEqualTo("A");

        assertThat(reader.readLine())
                .as("Another empty line between A and B -> \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("Final line 'B\\n' -> 'B'.")
                .isEqualTo("B");

        assertThat(reader.readLine())
                .as("Stream exhausted -> null.")
                .isNull();
    }

    @Test
    @Order(6)
    void readLine_trailingCR_atEOF() throws IOException {
        LineReader reader = readerOf(new byte[]{'H', 'e', 'l', 'l', 'o', '\r'});

        assertThat(reader.readLine())
                .as("A trailing \\r at EOF (no \\n follows) means the \\r cannot be part of a \\r\\n pair. "
                  + "Since \\r alone is not a line ending, it must be included in the line content. "
                  + "The stream ends without a proper delimiter, so this is an unterminated final line.")
                .isEqualTo("Hello\r");
    }

    @Test
    @Order(7)
    void readLine_consecutiveCRLF() throws IOException {
        LineReader reader = readerOf("\r\n\r\n\r\n");

        assertThat(reader.readLine())
                .as("First \\r\\n pair with no preceding content -> empty string \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("Second \\r\\n pair -> empty string \"\". "
                  + "Three consecutive CRLF pairs represent three empty lines. "
                  + "In HTTP, this pattern appears after a header section with one blank separator line.")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("Third \\r\\n pair -> empty string \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("After three CRLF pairs, stream is exhausted -> null.")
                .isNull();
    }
}

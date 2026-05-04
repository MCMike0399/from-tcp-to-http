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
 * Validates basic functional correctness of LineReader.
 * These tests use a standard ByteArrayInputStream, so the only thing under test
 * is whether LineReader correctly identifies line boundaries and returns content.
 */
@DisplayName("Module 0: LineReader Correctness Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineReaderCorrectnessTest {

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
    void readLine_singleLineLF_returnsLine() throws IOException {
        LineReader reader = readerOf("Hello\n");

        assertThat(reader.readLine())
                .as("After 'Hello\\n', readLine() should return 'Hello'. "
                  + "LineReader must scan for \\n delimiter and return everything before it. "
                  + "This is delimiter-based message framing (Conceptual Foundation 2).")
                .isEqualTo("Hello");
    }

    @Test
    @Order(2)
    void readLine_singleLineCRLF_returnsLine() throws IOException {
        LineReader reader = readerOf("Hello\r\n");

        assertThat(reader.readLine())
                .as("After 'Hello\\r\\n', readLine() should return 'Hello'. "
                  + "HTTP uses \\r\\n (CRLF) as its line terminator per RFC 9112. "
                  + "LineReader must recognize both \\n and \\r\\n as line endings.")
                .isEqualTo("Hello");
    }

    @Test
    @Order(3)
    void readLine_multipleLines_returnsInOrder() throws IOException {
        LineReader reader = readerOf("Alpha\nBeta\nGamma\n");

        assertThat(reader.readLine())
                .as("First readLine() from 'Alpha\\nBeta\\nGamma\\n' should return 'Alpha'. "
                  + "The reader must buffer excess bytes from a chunk and return them on subsequent calls.")
                .isEqualTo("Alpha");

        assertThat(reader.readLine())
                .as("Second readLine() should return 'Beta'. "
                  + "Bytes left over from the first read(byte[]) call must be preserved in an internal buffer.")
                .isEqualTo("Beta");

        assertThat(reader.readLine())
                .as("Third readLine() should return 'Gamma'.")
                .isEqualTo("Gamma");

        assertThat(reader.readLine())
                .as("After all lines have been consumed, readLine() must return null to signal EOF. "
                  + "This is analogous to read() returning -1: the stream is exhausted.")
                .isNull();
    }

    @Test
    @Order(4)
    void readLine_mixedDelimiters_handlesAll() throws IOException {
        LineReader reader = readerOf("A\nB\r\nC\n");

        assertThat(reader.readLine())
                .as("Line terminated by \\n should return 'A'.")
                .isEqualTo("A");

        assertThat(reader.readLine())
                .as("Line terminated by \\r\\n should return 'B'. "
                  + "Real-world streams mix line endings. A robust reader handles both.")
                .isEqualTo("B");

        assertThat(reader.readLine())
                .as("Line terminated by \\n should return 'C'.")
                .isEqualTo("C");
    }

    @Test
    @Order(5)
    void readLine_noTrailingNewline_returnsLastLine() throws IOException {
        LineReader reader = readerOf("Hello");

        assertThat(reader.readLine())
                .as("When the stream ends without a trailing newline, the remaining bytes "
                  + "must still be returned as a final line. This happens when read(byte[]) returns -1 "
                  + "and the internal buffer is non-empty -- flush the buffer as the last line.")
                .isEqualTo("Hello");

        assertThat(reader.readLine())
                .as("After the final unterminated line has been returned, subsequent calls must return null.")
                .isNull();
    }

    @Test
    @Order(6)
    void readLine_emptyStream_returnsNull() throws IOException {
        LineReader reader = readerOf(new byte[0]);

        assertThat(reader.readLine())
                .as("An empty stream contains no data at all. The very first read(byte[]) returns -1, "
                  + "and the buffer is empty, so readLine() must return null immediately.")
                .isNull();
    }

    @Test
    @Order(7)
    void readLine_emptyLines_preservesEmpty() throws IOException {
        LineReader reader = readerOf("\n\n\n");

        assertThat(reader.readLine())
                .as("A lone \\n with nothing before it is an empty line and must return \"\", NOT null. "
                  + "CRITICAL for HTTP: the empty line between headers and body (\\r\\n\\r\\n) is "
                  + "the most important delimiter in the protocol. Returning null here would make "
                  + "the parser think the stream ended instead of recognizing the header/body boundary.")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("Second consecutive empty line must also return \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("Third consecutive empty line must also return \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("After three \\n bytes have been consumed, readLine() must return null (EOF).")
                .isNull();
    }

    @Test
    @Order(8)
    void readLine_emptyLinesCRLF_preservesEmpty() throws IOException {
        LineReader reader = readerOf("\r\n\r\n");

        assertThat(reader.readLine())
                .as("\\r\\n with nothing before it is an empty line and must return \"\". "
                  + "This is exactly how the HTTP header/body separator looks on the wire: "
                  + "the final header line ends with \\r\\n, followed by the separator \\r\\n.")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("Second \\r\\n is another empty line -> \"\".")
                .isEqualTo("");

        assertThat(reader.readLine())
                .as("After two CRLF pairs, the stream is exhausted -> null.")
                .isNull();
    }

    @Test
    @Order(9)
    void readLine_afterNull_continuesReturningNull() throws IOException {
        LineReader reader = readerOf("Hi\n");

        assertThat(reader.readLine())
                .as("First call should return 'Hi'.")
                .isEqualTo("Hi");

        assertThat(reader.readLine())
                .as("Second call: stream is at EOF -> null.")
                .isNull();

        assertThat(reader.readLine())
                .as("Third call after EOF must still return null. "
                  + "EOF is a permanent state -- once the stream is exhausted, it stays exhausted. "
                  + "readLine() must be idempotent at EOF (no exceptions, no state corruption).")
                .isNull();

        assertThat(reader.readLine())
                .as("Fourth call after EOF must still return null (idempotent).")
                .isNull();
    }

    @Test
    @Order(10)
    void readLine_longLine_handlesLargeInput() throws IOException {
        int length = 10_000;
        byte[] data = new byte[length + 1];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) 'A';
        }
        data[length] = (byte) '\n';

        LineReader reader = readerOf(data);

        String line = reader.readLine();
        assertThat(line)
                .as("A 10,000-character line must be returned in full. "
                  + "If the internal buffer is too small (e.g., fixed 1024 bytes), "
                  + "the reader must grow or accumulate across multiple read(byte[]) calls.")
                .isNotNull()
                .hasSize(length);

        assertThat(line)
                .as("Every character in the 10,000-char line should be 'A'.")
                .matches("A{10000}");
    }

    @Test
    @Order(11)
    void readLine_utf8Content_preservesBytes() throws IOException {
        byte[] data = "café\n".getBytes(StandardCharsets.UTF_8);
        LineReader reader = readerOf(data);

        assertThat(reader.readLine())
                .as("Multi-byte UTF-8 characters (like 'e' in 'cafe') must be preserved. "
                  + "The 'e' character is encoded as two bytes (0xC3 0xA9) in UTF-8. "
                  + "If you construct the String with the wrong charset or corrupt bytes, "
                  + "the character will be garbled.")
                .isEqualTo("café");
    }

    @Test
    @Order(12)
    void readLine_binaryContent_preservesAllBytes() throws IOException {
        // Build a byte array containing every byte value except 0x0A (LF) and 0x0D (CR),
        // followed by a \n terminator.
        // Bytes: 0x01-0x09, 0x0B, 0x0C, 0x0E-0xFF, then 0x0A
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        for (int b = 0x01; b <= 0xFF; b++) {
            if (b == 0x0A || b == 0x0D) continue; // skip LF and CR
            bos.write(b);
        }
        int expectedLength = bos.size();
        bos.write(0x0A); // terminating \n

        byte[] data = bos.toByteArray();
        LineReader reader = readerOf(data);

        String line = reader.readLine();
        assertThat(line)
                .as("LineReader must preserve all byte values (0x01-0xFF except LF/CR) in the returned line. "
                  + "Network protocols can carry arbitrary binary data within a line. "
                  + "The reader must not corrupt, drop, or re-interpret any byte values.")
                .isNotNull()
                .hasSize(expectedLength);

        // Verify each byte is correct
        byte[] lineBytes = line.getBytes(StandardCharsets.ISO_8859_1);
        int idx = 0;
        for (int b = 0x01; b <= 0xFF; b++) {
            if (b == 0x0A || b == 0x0D) continue;
            assertThat(lineBytes[idx] & 0xFF)
                    .as("Byte at position %d should be 0x%02X but was 0x%02X. "
                      + "Binary content must pass through the reader unchanged.",
                            idx, b, lineBytes[idx] & 0xFF)
                    .isEqualTo(b);
            idx++;
        }
    }
}

package streams;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.ChunkedOnlyInputStream;
import testutil.PartialInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforcement tests that catch naive or disallowed implementations.
 * These tests verify that LineReader uses chunk-based reading, its own buffering,
 * and efficient string construction -- not just that it produces correct output.
 */
@DisplayName("Module 0: LineReader Enforcement Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineReaderEnforcementTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void readLine_withChunkedStream_neverCallsSingleByteRead() throws IOException {
        byte[] data = "Hello\nWorld\r\n".getBytes(StandardCharsets.UTF_8);
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 3);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("First line from chunked stream (maxChunk=3) should be 'Hello'. "
                  + "If this throws UnsupportedOperationException, your code called read() "
                  + "(single-byte) instead of read(byte[], int, int). TCP delivers data in chunks; "
                  + "reading one byte at a time wastes syscalls and ignores buffering.")
                .isEqualTo("Hello");

        assertThat(reader.readLine())
                .as("Second line should be 'World'. The \\r\\n terminator must be handled correctly "
                  + "even when bytes arrive 3 at a time.")
                .isEqualTo("World");
    }

    @Test
    @Order(2)
    void readLine_withChunkedStream_chunk1_handlesMinimalChunks() throws IOException {
        byte[] data = "Hi\nBye\n".getBytes(StandardCharsets.UTF_8);
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 1);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("With maxChunkSize=1, each read(byte[]) returns exactly 1 byte. "
                  + "LineReader must accumulate bytes across many read() calls to assemble a line. "
                  + "This tests the buffer accumulation logic at its most granular.")
                .isEqualTo("Hi");

        assertThat(reader.readLine())
                .as("Second line 'Bye' must also be correctly assembled from 1-byte chunks.")
                .isEqualTo("Bye");
    }

    @Test
    @Order(3)
    void readLine_withChunkedStream_lineSpanningMultipleChunks() throws IOException {
        byte[] data = "HelloWorld\n".getBytes(StandardCharsets.UTF_8);
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 3);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("'HelloWorld\\n' with chunkSize=3 arrives as 'Hel','loW','orl','d\\n'. "
                  + "The line spans 4 chunk reads. LineReader must concatenate chunks in its "
                  + "internal buffer until the \\n delimiter is found, then extract the complete line.")
                .isEqualTo("HelloWorld");
    }

    @Test
    @Order(4)
    void readLine_withPartialStream_handlesRandomChunking() throws IOException {
        byte[] data = "Line1\nLine2\nLine3\n".getBytes(StandardCharsets.UTF_8);
        PartialInputStream stream = new PartialInputStream(data, 42);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("With random chunk sizes (seed=42), the first line must still be 'Line1'. "
                  + "PartialInputStream simulates TCP fragmentation: each read returns a random "
                  + "number of bytes. A correct implementation handles any fragmentation pattern.")
                .isEqualTo("Line1");

        assertThat(reader.readLine())
                .as("Second line with random chunking should be 'Line2'.")
                .isEqualTo("Line2");

        assertThat(reader.readLine())
                .as("Third line with random chunking should be 'Line3'.")
                .isEqualTo("Line3");

        assertThat(reader.readLine())
                .as("After all lines consumed with random chunking, should return null.")
                .isNull();
    }

    @Test
    @Order(5)
    void readLine_crlfSplitAcrossChunks_handlesCorrectly() throws IOException {
        byte[] data = "Hello\r\nWorld\n".getBytes(StandardCharsets.UTF_8);
        // chunkSize=6: first chunk "Hello\r", second chunk "\nWorld", third chunk "\n"
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 6);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("The \\r\\n terminator for 'Hello' is SPLIT across two chunks: "
                  + "chunk 1 ends with \\r, chunk 2 starts with \\n. "
                  + "This is the classic TCP byte-stream problem -- message boundaries do not "
                  + "align with read boundaries. Your FSM must remember that it saw \\r and check "
                  + "the next byte (possibly from a new read) for \\n.")
                .isEqualTo("Hello");

        assertThat(reader.readLine())
                .as("After the split \\r\\n, the next line 'World' must be returned correctly.")
                .isEqualTo("World");
    }

    @Test
    @Order(6)
    void readLine_doesNotUseBufferedReader() {
        Field[] fields = LineReader.class.getDeclaredFields();
        for (Field field : fields) {
            Class<?> type = field.getType();
            assertThat(type)
                    .as("LineReader must implement its own buffering. Using BufferedReader/Scanner "
                      + "defeats the learning objective of Module 0. You must understand "
                      + "how buffering works by building it yourself. "
                      + "Found field '%s' of type %s.", field.getName(), type.getName())
                    .isNotEqualTo(BufferedReader.class);

            assertThat(type)
                    .as("LineReader must not use Scanner. Scanner hides the buffering logic "
                      + "that you need to understand. Found field '%s' of type %s.",
                            field.getName(), type.getName())
                    .isNotEqualTo(Scanner.class);

            assertThat(type)
                    .as("LineReader must not use InputStreamReader. Convert bytes to String "
                      + "directly via new String(byte[], offset, length, charset). "
                      + "Found field '%s' of type %s.", field.getName(), type.getName())
                    .isNotEqualTo(InputStreamReader.class);
        }
    }

    @Test
    @Order(7)
    void readLine_doesNotBuildStringWithConcatenation() throws IOException {
        int length = 100_000;
        byte[] data = new byte[length + 1];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) 'A';
        }
        data[length] = (byte) '\n';

        LineReader reader = new LineReader(new java.io.ByteArrayInputStream(data));

        long start = System.nanoTime();
        String line = reader.readLine();
        long elapsed = System.nanoTime() - start;

        assertThat(line)
                .as("The 100,000-character line must be returned correctly.")
                .isNotNull()
                .hasSize(length);

        long elapsedMs = elapsed / 1_000_000;
        assertThat(Duration.ofNanos(elapsed))
                .as("Reading a 100KB line took %dms. If >500ms, this suggests O(n^2) string "
                  + "concatenation (String += in a loop). Each += creates a new String object "
                  + "and copies all previous characters. Use ByteArrayOutputStream or byte[] buffer instead.",
                        elapsedMs)
                .isLessThan(Duration.ofMillis(500));
    }

    @Test
    @Order(8)
    void readLine_multipleCallsReuseInternalBuffer() throws IOException {
        byte[] data = "A\nB\nC\n".getBytes(StandardCharsets.UTF_8);
        // chunkSize=10 means all 6 bytes fit in one read
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 10);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("First readLine() should return 'A'.")
                .isEqualTo("A");
        assertThat(reader.readLine())
                .as("Second readLine() should return 'B'.")
                .isEqualTo("B");
        assertThat(reader.readLine())
                .as("Third readLine() should return 'C'.")
                .isEqualTo("C");

        assertThat(stream.getChunkReadCalls())
                .as("All 6 bytes ('A\\nB\\nC\\n') fit in one chunk (maxChunkSize=10). "
                  + "A well-buffered implementation should need only 1-2 read(byte[]) calls "
                  + "to get all the data, then serve 3 readLine() calls from its internal buffer. "
                  + "If chunkReadCalls is high (e.g., 6), the buffer is not being reused "
                  + "across readLine() invocations -- bytes are being discarded and re-read.")
                .isLessThanOrEqualTo(2);
    }
}

package response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RecordingOutputStream;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the correctness of chunked transfer encoding as defined in
 * RFC 9112 Section 7.1.
 * <p>
 * Chunked encoding sends data as a series of chunks, each prefixed with
 * its size in hexadecimal:
 * <pre>
 *   {hex-size}\r\n
 *   {data}\r\n
 *   ...
 *   0\r\n
 *   \r\n
 * </pre>
 * The terminating chunk has size 0, followed by a final CRLF.
 */
@DisplayName("Module 5: Chunked Transfer Encoding Correctness Tests (E5.2)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChunkedEncoderCorrectnessTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void encode_singleChunk_correctFormat() throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();

        ChunkedEncoder.writeChunk(out, "Hello".getBytes());

        assertThat(out.getAllAsString())
                .as("A single chunk of 'Hello' (5 bytes) must be encoded as "
                  + "'5\\r\\nHello\\r\\n'. The hex size comes first, then CRLF, "
                  + "then the data, then another CRLF. "
                  + "See RFC 9112 Section 7.1: chunk = chunk-size CRLF chunk-data CRLF.")
                .isEqualTo("5\r\nHello\r\n");
    }

    @Test
    @Order(2)
    void encode_multipleChunks_allCorrect() throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();

        ChunkedEncoder.writeChunk(out, "Hello".getBytes());
        ChunkedEncoder.writeChunk(out, "World".getBytes());
        ChunkedEncoder.writeTerminator(out);

        assertThat(out.getAllAsString())
                .as("Multiple chunks followed by a terminator must produce the complete "
                  + "chunked encoding: each chunk with its hex size, then the zero-length "
                  + "terminator. The receiver reads chunks until it sees size 0, which "
                  + "signals the end of the transfer.")
                .isEqualTo("5\r\nHello\r\n5\r\nWorld\r\n0\r\n\r\n");
    }

    @Test
    @Order(3)
    void encode_terminator_correctFormat() throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();

        ChunkedEncoder.writeTerminator(out);

        assertThat(out.getAllAsString())
                .as("The chunked encoding terminator is a zero-length chunk: '0\\r\\n\\r\\n'. "
                  + "The first \\r\\n ends the chunk-size line, and the second \\r\\n is the "
                  + "empty chunk-data followed by its trailing CRLF. This signals the receiver "
                  + "that the transfer is complete.")
                .isEqualTo("0\r\n\r\n");
    }

    @Test
    @Order(4)
    void encode_binaryData_correctHexLength() throws IOException {
        // 256 bytes of data: 0x00 through 0xFF
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte) i;
        }

        RecordingOutputStream out = new RecordingOutputStream();
        ChunkedEncoder.writeChunk(out, data);

        String output = out.getAllAsString();
        // 256 in hex is "100"
        // The output should start with "100\r\n"
        assertThat(output)
                .as("256 bytes must be encoded with hex size '100' (since 0x100 = 256). "
                  + "The chunk format is '{hex-size}\\r\\n{data}\\r\\n'. "
                  + "Chunk sizes are always in hexadecimal per RFC 9112 Section 7.1.")
                .startsWith("100\r\n");

        // Verify total structure: "100\r\n" + 256 bytes + "\r\n"
        byte[] rawOutput = out.getAllBytes();
        int expectedLength = "100\r\n".length() + 256 + "\r\n".length();
        assertThat(rawOutput)
                .as("Total output should be the hex prefix + data bytes + trailing CRLF.")
                .hasSize(expectedLength);
    }

    @Test
    @Order(5)
    void encode_largeChunk_correctHexLength() throws IOException {
        // 0xABCD = 43981 bytes
        int size = 0xABCD;
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 'X');

        RecordingOutputStream out = new RecordingOutputStream();
        ChunkedEncoder.writeChunk(out, data);

        String output = out.getAllAsString();

        // Extract the hex prefix (everything before the first \r\n)
        int crlfIdx = output.indexOf("\r\n");
        String hexPrefix = output.substring(0, crlfIdx);

        assertThat(hexPrefix.toLowerCase())
                .as("Chunk size must be encoded as hexadecimal per RFC 9112 Section 7.1. "
                  + "Use Integer.toHexString() for conversion. "
                  + "43981 bytes should produce the hex prefix 'abcd'. "
                  + "Both lowercase and uppercase hex digits are valid per the RFC.")
                .isEqualTo("abcd");

        // Verify the trailing CRLF after the data
        byte[] rawOutput = out.getAllBytes();
        int expectedLength = hexPrefix.length() + "\r\n".length() + size + "\r\n".length();
        assertThat(rawOutput)
                .as("Total output must be: hex-size + CRLF + data + CRLF.")
                .hasSize(expectedLength);
    }
}

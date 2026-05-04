package response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RecordingOutputStream;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge case tests for the chunked transfer encoder. These cover boundary
 * conditions that are easy to get wrong: empty chunks, single-byte payloads,
 * and data that contains CRLF sequences (which must NOT be confused with
 * chunk framing).
 */
@DisplayName("Module 5: Chunked Encoder Edge Case Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChunkedEncoderEdgeCaseTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void encode_emptyChunk_handledCorrectly() throws IOException {
        // Per RFC 9112, a chunk with size 0 IS the terminator.
        // writeChunk() with empty data should either:
        //   (a) write "0\r\n\r\n" (treating it as a terminator), or
        //   (b) throw IllegalArgumentException (prevent accidental termination)
        // Both are valid design choices. We test for either.

        RecordingOutputStream out = new RecordingOutputStream();

        try {
            ChunkedEncoder.writeChunk(out, new byte[0]);

            // If it didn't throw, the output must be the terminator format
            // (since a zero-length chunk IS the last-chunk per the RFC)
            // OR it could write nothing (no-op for empty data).
            String output = out.getAllAsString();
            assertThat(output)
                    .as("writeChunk() with empty data either writes nothing (no-op) or writes "
                      + "the terminator '0\\r\\n\\r\\n'. A zero-length chunk in the chunked "
                      + "encoding is defined as the last-chunk, so sending one mid-stream "
                      + "would prematurely terminate the transfer. A defensive implementation "
                      + "may choose to skip the write or produce the terminator.")
                    .satisfiesAnyOf(
                            s -> assertThat(s).isEmpty(),
                            s -> assertThat(s).isEqualTo("0\r\n\r\n")
                    );
        } catch (IllegalArgumentException e) {
            // This is also acceptable: the encoder refuses to write an empty
            // chunk to prevent accidental termination of the chunked stream.
            assertThat(e)
                    .as("Throwing IllegalArgumentException for empty data is a valid "
                      + "design choice. It prevents accidentally sending the terminator "
                      + "when the caller passes empty data.")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @Order(2)
    void encode_singleByte_correctFormat() throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();

        ChunkedEncoder.writeChunk(out, new byte[]{0x41}); // 'A'

        assertThat(out.getAllAsString())
                .as("A single byte (0x41 = 'A') should produce '1\\r\\nA\\r\\n'. "
                  + "The hex size of 1 byte is '1'. This is the smallest non-empty chunk.")
                .isEqualTo("1\r\nA\r\n");
    }

    @Test
    @Order(3)
    void encode_dataContainingCRLF_sizeCountsRawBytes() throws IOException {
        // "AB\r\nCD" is 6 bytes: A, B, \r, \n, C, D
        byte[] data = "AB\r\nCD".getBytes();

        RecordingOutputStream out = new RecordingOutputStream();
        ChunkedEncoder.writeChunk(out, data);

        byte[] rawOutput = out.getAllBytes();

        // The output should start with "6\r\n" (hex size of 6 bytes)
        String prefix = new String(rawOutput, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(prefix)
                .as("Chunk size counts raw bytes, not 'lines'. Even if the data contains "
                  + "\\r\\n sequences, they are payload, not framing. "
                  + "'AB\\r\\nCD' is 6 bytes, so the chunk size must be '6'. "
                  + "This is why length-prefix framing is unambiguous (Conceptual Foundation 2).")
                .isEqualTo("6\r\n");

        // Verify the data portion preserves the embedded CRLF
        // Expected raw output: "6\r\n" + "AB\r\nCD" + "\r\n"
        //                       4 bytes + 6 bytes    + 2 bytes = 12 bytes total
        assertThat(rawOutput)
                .as("Total output must include the complete data with embedded CRLF intact. "
                  + "The chunk framing CRLF at the end is separate from the CRLF in the data.")
                .hasSize(12);

        // Extract the 6 data bytes (after "6\r\n", before trailing "\r\n")
        byte[] extractedData = new byte[6];
        System.arraycopy(rawOutput, 4, extractedData, 0, 6);
        assertThat(extractedData)
                .as("The data bytes must be written verbatim. The \\r\\n inside the payload "
                  + "must not be escaped, doubled, or stripped.")
                .isEqualTo(data);
    }
}

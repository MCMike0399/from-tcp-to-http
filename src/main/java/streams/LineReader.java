package streams;

import java.io.InputStream;
import java.io.IOException;

/**
 * LineReader implements delimiter-based message framing over a raw byte stream.
 *
 * COURSE REQUIREMENT (Module 0, E0.1):
 * You MUST implement this using:
 * 1. read(byte[]) to read CHUNKS from the stream, NOT read() one byte at a time.
 * 2. A persistent byte buffer field that survives between readLine() calls.
 * 3. Extraction of complete lines, with leftover bytes kept for the next call.
 *
 * Why this matters: TCP is a byte stream with no message boundaries. One read(byte[])
 * call might return multiple lines, a partial line, or anything in between. The
 * application (your LineReader) must impose structure by buffering and scanning.
 */
public class LineReader {
    private final InputStream input;

    // TODO(student): Declare a persistent buffer field here.
    // This field must hold bytes that arrived in a chunk but don't yet form a complete line.
    // Example: if read(byte[]) returns "Hello\nWorl", you return "Hello" and keep "Worl".
    // HINT: java.io.ByteArrayOutputStream is designed for exactly this: appending bytes
    //       and extracting them as a contiguous byte[].

    public LineReader(InputStream input) {
        this.input = input;
    }

    public String readLine() throws IOException {
        // TODO(student): Read a chunk from 'input' using read(byte[]), NOT read().
        // TODO(student): Append the chunk to your persistent buffer.
        // TODO(student): Scan the combined buffer for \n (or \r\n). If found:
        //                - Extract the line without the newline bytes.
        //                - Remove the extracted bytes from the buffer.
        //                - Return the line.
        // TODO(student): If read(byte[]) returns -1 (EOF):
        //                - If the buffer has content, flush it as the final line and clear the buffer.
        //                - If the buffer is empty, return null.
        // TODO(student): Convert bytes to String using new String(bytes, offset, length, charset).
        //                Do NOT build strings with += inside a loop (that creates O(n^2) garbage).

        // ------------------------------------------------------------------
        // STEPPING-STONE CODE: The logic below is correct for delimiters and EOF,
        // but it reads one byte at a time and has no persistent buffer.
        // Refactor this to use read(byte[]) + a persistent buffer before moving to Module 1.
        // ------------------------------------------------------------------
        int readInput = 0;
        String word = "";

        while (true) {
            readInput = input.read();

            if (readInput == 10 || readInput < 0) {
                break;
            }
            else if (readInput == 13) {
                input.read(); // consume following \n
                break;
            }
            else {
                word += (char) readInput;
            }
        }
        return word.length() > 0 ? word : null;
    }
}

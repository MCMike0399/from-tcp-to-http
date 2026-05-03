package streams;

import java.io.*;

public class LineReaderTestMain {
    public static void main(String[] args) throws IOException {
        // ------------------------------------------------------------------
        // Tests 1-3: Logic validation (delimiters, EOF, empty stream)
        // These pass even with byte-by-byte reading.
        // ------------------------------------------------------------------

        System.out.println("=== Test 1: Happy path ===");
        byte[] data1 = "Hello\nWorld\r\n".getBytes();
        LineReader reader1 = new LineReader(new ByteArrayInputStream(data1));
        System.out.println("[" + reader1.readLine() + "]");
        System.out.println("[" + reader1.readLine() + "]");
        System.out.println("[" + reader1.readLine() + "]");

        System.out.println("\n=== Test 2: No trailing newline ===");
        byte[] data2 = "Hello".getBytes();
        LineReader reader2 = new LineReader(new ByteArrayInputStream(data2));
        System.out.println("[" + reader2.readLine() + "]");
        System.out.println("[" + reader2.readLine() + "]");

        System.out.println("\n=== Test 3: Empty stream ===");
        byte[] data3 = new byte[0];
        LineReader reader3 = new LineReader(new ByteArrayInputStream(data3));
        System.out.println("[" + reader3.readLine() + "]");

        // ------------------------------------------------------------------
        // Test 4: TCP chunking simulation with ENFORCEMENT
        // This stream delivers at most 3 bytes per read(byte[]) call.
        // Calling read() throws an exception — this proves your LineReader
        // is reading chunks, not single bytes.
        // ------------------------------------------------------------------
        System.out.println("\n=== Test 4: TCP chunking (ENFORCED) ===");
        byte[] data4 = "Hello\nWorld\r\nExtra".getBytes();
        InputStream in4 = new ChunkedOnlyInputStream(data4, 3);
        LineReader reader4 = new LineReader(in4);
        System.out.println("[" + reader4.readLine() + "]");  // Hello
        System.out.println("[" + reader4.readLine() + "]");  // World
        System.out.println("[" + reader4.readLine() + "]");  // Extra
        System.out.println("[" + reader4.readLine() + "]");  // null
    }

    /**
     * Simulates a TCP socket that delivers at most 'chunkSize' bytes per read(byte[]).
     * Calling read() (single-byte) throws UnsupportedOperationException.
     * This forces the LineReader to use chunk reading with a persistent buffer.
     */
    static class ChunkedOnlyInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int chunkSize;

        ChunkedOnlyInputStream(byte[] data, int chunkSize) {
            this.delegate = new ByteArrayInputStream(data);
            this.chunkSize = chunkSize;
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException(
                "FAIL: LineReader called read() instead of read(byte[]). " +
                "You must read chunks with a buffer, not single bytes."
            );
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, Math.min(len, chunkSize));
        }
    }
}

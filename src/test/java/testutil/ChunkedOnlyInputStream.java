package testutil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A decorator InputStream that enforces chunk-based reading and caps read sizes.
 * <p>
 * Single-byte {@link #read()} calls throw {@link UnsupportedOperationException}
 * to enforce the discipline of reading in chunks, which mirrors how data arrives
 * over TCP. The bulk {@link #read(byte[], int, int)} method delegates to the
 * underlying stream but never returns more than {@code maxChunkSize} bytes per call.
 */
public class ChunkedOnlyInputStream extends InputStream {

    private static final String ENFORCEMENT_MESSAGE =
            "ENFORCEMENT FAILURE: Your code called read() (single-byte). "
            + "You MUST use read(byte[], int, int) to read chunks. "
            + "Single-byte reading defeats TCP buffering. "
            + "See Conceptual Foundation 1: TCP is a byte stream.";

    private final InputStream delegate;
    private final int maxChunkSize;

    private int singleByteReadAttempts;
    private int chunkReadCalls;
    private long totalBytesDelivered;

    public ChunkedOnlyInputStream(byte[] data, int maxChunkSize) {
        this(new ByteArrayInputStream(data), maxChunkSize);
    }

    public ChunkedOnlyInputStream(InputStream delegate, int maxChunkSize) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (maxChunkSize < 1) {
            throw new IllegalArgumentException("maxChunkSize must be >= 1");
        }
        this.delegate = delegate;
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public int read() throws IOException {
        singleByteReadAttempts++;
        throw new UnsupportedOperationException(ENFORCEMENT_MESSAGE);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        chunkReadCalls++;
        int capped = Math.min(len, maxChunkSize);
        int n = delegate.read(b, off, capped);
        if (n > 0) {
            totalBytesDelivered += n;
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public int getSingleByteReadAttempts() {
        return singleByteReadAttempts;
    }

    public int getChunkReadCalls() {
        return chunkReadCalls;
    }

    public long getTotalBytesDelivered() {
        return totalBytesDelivered;
    }
}

package testutil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Simulates TCP fragmentation by returning randomly-sized chunks from each read.
 * <p>
 * Each call to {@link #read(byte[], int, int)} delivers between 1 and
 * {@code Math.min(len, remaining)} bytes, chosen by a seeded {@link Random}
 * for reproducible test behavior. Like {@link ChunkedOnlyInputStream}, the
 * single-byte {@link #read()} method is forbidden.
 */
public class PartialInputStream extends InputStream {

    private static final String ENFORCEMENT_MESSAGE =
            "ENFORCEMENT FAILURE: Your code called read() (single-byte). "
            + "You MUST use read(byte[], int, int) to read chunks. "
            + "Single-byte reading defeats TCP buffering. "
            + "See Conceptual Foundation 1: TCP is a byte stream.";

    private final ByteArrayInputStream delegate;
    private final Random random;

    public PartialInputStream(byte[] data, long seed) {
        this.delegate = new ByteArrayInputStream(data);
        this.random = new Random(seed);
    }

    @Override
    public int read() throws IOException {
        throw new UnsupportedOperationException(ENFORCEMENT_MESSAGE);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int remaining = delegate.available();
        if (remaining == 0) {
            return -1;
        }
        int maxBytes = Math.min(len, remaining);
        // Return between 1 and maxBytes, chosen randomly
        int toRead = 1 + random.nextInt(maxBytes);
        return delegate.read(b, off, toRead);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}

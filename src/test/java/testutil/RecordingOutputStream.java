package testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An OutputStream that records every write call for later inspection.
 * <p>
 * Each write call stores an independent copy of the bytes written, and all
 * bytes are also accumulated in a combined buffer. Useful for verifying that
 * code produces the expected output in the expected chunking pattern.
 */
public class RecordingOutputStream extends OutputStream {

    private final List<byte[]> chunks = new ArrayList<>();
    private final ByteArrayOutputStream combined = new ByteArrayOutputStream();

    @Override
    public void write(int b) throws IOException {
        byte[] single = new byte[]{(byte) b};
        chunks.add(single);
        combined.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        byte[] copy = Arrays.copyOfRange(b, off, off + len);
        chunks.add(copy);
        combined.write(b, off, len);
    }

    /**
     * Returns an unmodifiable view of the recorded chunks.
     * Each element is an independent copy of the bytes from one write call.
     */
    public List<byte[]> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Returns all bytes written so far, concatenated in order.
     */
    public byte[] getAllBytes() {
        return combined.toByteArray();
    }

    /**
     * Returns all bytes written so far as a UTF-8 string.
     */
    public String getAllAsString() {
        return combined.toString(StandardCharsets.UTF_8);
    }

    /**
     * Returns the number of individual write calls made.
     */
    public int getWriteCount() {
        return chunks.size();
    }
}

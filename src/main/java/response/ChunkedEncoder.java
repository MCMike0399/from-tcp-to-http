package response;

import java.io.IOException;
import java.io.OutputStream;

public class ChunkedEncoder {

    public static void writeChunk(OutputStream out, byte[] data) throws IOException {
        throw new UnsupportedOperationException("TODO: Implement chunked encoding (Module 5, E5.2)");
    }

    public static void writeChunk(OutputStream out, byte[] data, int off, int len) throws IOException {
        throw new UnsupportedOperationException("TODO: Implement chunked encoding (Module 5, E5.2)");
    }

    public static void writeTerminator(OutputStream out) throws IOException {
        throw new UnsupportedOperationException("TODO: Implement chunked encoding (Module 5, E5.2)");
    }
}

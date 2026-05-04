package testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A minimal TCP client for sending and receiving raw bytes over a socket.
 * <p>
 * Designed for protocol-level testing where you need precise control over
 * what bytes go on the wire, without any HTTP library abstractions.
 */
public class RawTcpClient implements AutoCloseable {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public RawTcpClient(String host, int port) throws IOException {
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /**
     * Sends raw bytes and flushes.
     */
    public void send(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    /**
     * Sends a string as US-ASCII bytes and flushes.
     */
    public void send(String text) throws IOException {
        send(text.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Sends a line terminated with CRLF.
     */
    public void sendLine(String line) throws IOException {
        send(line + "\r\n");
    }

    /**
     * Reads bytes until a CRLF sequence is found and returns the line
     * (without the trailing CRLF) as a US-ASCII string.
     *
     * @return the line, or {@code null} if EOF is reached before any data
     */
    public String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                // EOF before any data means no more lines
                if (buf.size() == 0 && prev == -1) {
                    return null;
                }
                // EOF mid-line: return what we have
                return buf.toString(StandardCharsets.US_ASCII);
            }
            if (prev == '\r' && b == '\n') {
                // Remove the trailing \r that we already wrote
                byte[] raw = buf.toByteArray();
                return new String(raw, 0, raw.length - 1, StandardCharsets.US_ASCII);
            }
            buf.write(b);
            prev = b;
        }
    }

    /**
     * Reads exactly {@code n} bytes from the socket.
     *
     * @throws IOException if EOF is reached before all bytes are read
     */
    public byte[] readBytes(int n) throws IOException {
        byte[] buf = new byte[n];
        int offset = 0;
        while (offset < n) {
            int read = in.read(buf, offset, n - offset);
            if (read == -1) {
                throw new IOException(
                        "EOF after " + offset + " bytes, expected " + n);
            }
            offset += read;
        }
        return buf;
    }

    /**
     * Reads all remaining bytes until EOF.
     */
    public byte[] readAll() throws IOException {
        return in.readAllBytes();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}

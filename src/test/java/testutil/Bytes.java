package testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static helpers for building byte arrays used in protocol-level tests.
 * <p>
 * All text is treated as US-ASCII unless otherwise noted, matching the
 * HTTP/1.1 requirement that request/status lines and header fields are
 * composed of ASCII octets.
 */
public final class Bytes {

    private static final byte[] CRLF = {'\r', '\n'};

    private Bytes() {
        // utility class
    }

    /**
     * Converts an ASCII string to bytes.
     */
    public static byte[] of(String asciiText) {
        return asciiText.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Concatenates multiple byte arrays into one.
     */
    public static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] a : arrays) {
            totalLength += a.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, offset, a.length);
            offset += a.length;
        }
        return result;
    }

    /**
     * Builds a complete HTTP request as raw bytes.
     * <p>
     * If {@code body} is non-null and {@code headers} does not already contain
     * a {@code Content-Length} entry, one is added automatically. The header
     * block is always terminated with a blank CRLF line.
     *
     * @param method  HTTP method (e.g. "GET", "POST")
     * @param uri     request target (e.g. "/index.html")
     * @param version protocol version (e.g. "HTTP/1.1")
     * @param headers header name-value pairs (may be empty; iteration order is preserved)
     * @param body    request body, or {@code null} for no body
     * @return the complete HTTP request as a byte array
     */
    public static byte[] httpRequest(String method, String uri, String version,
                                     Map<String, String> headers, byte[] body) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Request line
            out.write(of(method + " " + uri + " " + version));
            out.write(CRLF);

            // Possibly add Content-Length
            Map<String, String> effectiveHeaders = new LinkedHashMap<>(headers);
            if (body != null && !containsKeyIgnoreCase(effectiveHeaders, "Content-Length")) {
                effectiveHeaders.put("Content-Length", String.valueOf(body.length));
            }

            // Headers
            for (Map.Entry<String, String> entry : effectiveHeaders.entrySet()) {
                out.write(of(entry.getKey() + ": " + entry.getValue()));
                out.write(CRLF);
            }

            // Blank line ending headers
            out.write(CRLF);

            // Body
            if (body != null) {
                out.write(body);
            }

            return out.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream never throws — this is unreachable
            throw new AssertionError(e);
        }
    }

    /**
     * Builds a complete HTTP request with no body.
     */
    public static byte[] httpRequest(String method, String uri,
                                     Map<String, String> headers) {
        return httpRequest(method, uri, "HTTP/1.1", headers, null);
    }

    /**
     * Builds a complete HTTP response as raw bytes.
     * <p>
     * If {@code body} is non-null and {@code headers} does not already contain
     * a {@code Content-Length} entry, one is added automatically. The header
     * block is always terminated with a blank CRLF line.
     *
     * @param status  HTTP status code (e.g. 200)
     * @param reason  reason phrase (e.g. "OK")
     * @param headers header name-value pairs (may be empty; iteration order is preserved)
     * @param body    response body, or {@code null} for no body
     * @return the complete HTTP response as a byte array
     */
    public static byte[] httpResponse(int status, String reason,
                                      Map<String, String> headers, byte[] body) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Status line
            out.write(of("HTTP/1.1 " + status + " " + reason));
            out.write(CRLF);

            // Possibly add Content-Length
            Map<String, String> effectiveHeaders = new LinkedHashMap<>(headers);
            if (body != null && !containsKeyIgnoreCase(effectiveHeaders, "Content-Length")) {
                effectiveHeaders.put("Content-Length", String.valueOf(body.length));
            }

            // Headers
            for (Map.Entry<String, String> entry : effectiveHeaders.entrySet()) {
                out.write(of(entry.getKey() + ": " + entry.getValue()));
                out.write(CRLF);
            }

            // Blank line ending headers
            out.write(CRLF);

            // Body
            if (body != null) {
                out.write(body);
            }

            return out.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean containsKeyIgnoreCase(Map<String, String> map, String key) {
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }
}

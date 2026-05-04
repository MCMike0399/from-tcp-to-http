package response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpResponse {
    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    private HttpResponse(int statusCode, String reasonPhrase,
                         Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = (body != null) ? Arrays.copyOf(body, body.length) : new byte[0];
    }

    public static Builder builder() {
        return new Builder();
    }

    public void writeTo(OutputStream out) throws IOException {
        throw new UnsupportedOperationException("TODO: Implement response writing (Module 5, E5.1)");
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return Arrays.copyOf(body, body.length);
    }

    public static class Builder {
        private int statusCode = 200;
        private String reasonPhrase = "OK";
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];

        private Builder() {
        }

        public Builder status(int code, String reason) {
            this.statusCode = code;
            this.reasonPhrase = reason;
            return this;
        }

        public Builder status(int code) {
            this.statusCode = code;
            this.reasonPhrase = defaultReason(code);
            return this;
        }

        public Builder header(String name, String value) {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder body(byte[] body) {
            this.body = (body != null) ? Arrays.copyOf(body, body.length) : new byte[0];
            return this;
        }

        public Builder body(String text) {
            this.body = (text != null) ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return this;
        }

        public Builder contentType(String type) {
            return header("Content-Type", type);
        }

        public HttpResponse build() {
            return new HttpResponse(statusCode, reasonPhrase, headers, body);
        }

        private static String defaultReason(int code) {
            return switch (code) {
                case 200 -> "OK";
                case 201 -> "Created";
                case 204 -> "No Content";
                case 301 -> "Moved Permanently";
                case 302 -> "Found";
                case 304 -> "Not Modified";
                case 400 -> "Bad Request";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 405 -> "Method Not Allowed";
                case 500 -> "Internal Server Error";
                case 501 -> "Not Implemented";
                case 502 -> "Bad Gateway";
                case 503 -> "Service Unavailable";
                default -> "Unknown";
            };
        }
    }
}

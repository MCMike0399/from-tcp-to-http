package http;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpRequest {
    private final String method;
    private final String uri;
    private final String version;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public HttpRequest(String method, String uri, String version,
                       Map<String, List<String>> headers, byte[] body) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        this.method = method;
        this.uri = uri;
        this.version = version;
        this.headers = (headers != null)
                ? Collections.unmodifiableMap(new LinkedHashMap<>(headers))
                : Collections.emptyMap();
        this.body = (body != null) ? Arrays.copyOf(body, body.length) : new byte[0];
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return Arrays.copyOf(body, body.length);
    }

    public String getHeader(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                return (values != null && !values.isEmpty()) ? values.get(0) : null;
            }
        }
        return null;
    }

    public List<String> getHeaderValues(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                return (values != null) ? Collections.unmodifiableList(values) : Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}

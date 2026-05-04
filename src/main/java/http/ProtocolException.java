package http;

import java.io.IOException;

public class ProtocolException extends IOException {
    private final int statusCode;

    public ProtocolException(String message) {
        super(message);
        this.statusCode = 400;
    }

    public ProtocolException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

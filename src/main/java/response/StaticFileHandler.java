package response;

import http.HttpRequest;
import server.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class StaticFileHandler implements HttpHandler {
    private final Path documentRoot;

    public StaticFileHandler(Path documentRoot) {
        this.documentRoot = documentRoot;
    }

    @Override
    public void handle(HttpRequest request, OutputStream responseBody) throws IOException {
        throw new UnsupportedOperationException("TODO: Implement static file serving (Module 5, E5.1)");
    }
}

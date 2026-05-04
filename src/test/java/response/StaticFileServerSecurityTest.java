package response;

import http.HttpRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import testutil.RecordingOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security-focused tests for the static file server.
 * <p>
 * Serving files from disk is one of the most dangerous operations a web server
 * performs. A single bug in path resolution can expose the entire filesystem.
 * These tests verify that the StaticFileHandler:
 * <ul>
 *   <li>Serves files correctly from the document root</li>
 *   <li>Rejects directory traversal attacks (..)</li>
 *   <li>Rejects percent-encoded and double-encoded traversal attempts</li>
 *   <li>Rejects null-byte injection</li>
 *   <li>Returns 404 for missing files</li>
 * </ul>
 */
@DisplayName("Module 5: Static File Server Security Tests (E5.1)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaticFileServerSecurityTest {

    @TempDir
    Path documentRoot;

    private StaticFileHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        // Create a small test filesystem
        Files.writeString(documentRoot.resolve("index.html"), "<h1>Hello</h1>");
        Files.writeString(documentRoot.resolve("style.css"), "body { color: red; }");
        Files.createDirectories(documentRoot.resolve("sub"));
        Files.writeString(documentRoot.resolve("sub/page.html"), "<h1>Sub</h1>");

        handler = new StaticFileHandler(documentRoot);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Creates a minimal GET request for the given URI.
     */
    private static HttpRequest request(String uri) {
        return new HttpRequest("GET", uri, "HTTP/1.1",
                Map.of("host", List.of("localhost")), new byte[0]);
    }

    /**
     * Handles a request and returns the full response as a string.
     */
    private String handleAndCapture(String uri) throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();
        handler.handle(request(uri), out);
        return out.getAllAsString();
    }

    /**
     * Extracts the HTTP status code from a raw response string.
     * Expects the response to start with "HTTP/1.1 {code} ...".
     */
    private static int extractStatusCode(String response) {
        // Status line: "HTTP/1.1 200 OK\r\n..."
        int firstSpace = response.indexOf(' ');
        int secondSpace = response.indexOf(' ', firstSpace + 1);
        if (firstSpace < 0 || secondSpace < 0) {
            throw new IllegalArgumentException(
                    "Cannot parse status code from response: " + response.substring(0, Math.min(80, response.length())));
        }
        return Integer.parseInt(response.substring(firstSpace + 1, secondSpace));
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void staticFile_validPath_served() throws IOException {
        String response = handleAndCapture("/index.html");

        int status = extractStatusCode(response);
        assertThat(status)
                .as("A request for /index.html should return 200 when the file exists "
                  + "in the document root. The handler resolves the URI path against "
                  + "the document root directory.")
                .isEqualTo(200);

        assertThat(response)
                .as("The response body must contain the file content. "
                  + "index.html contains '<h1>Hello</h1>'.")
                .contains("<h1>Hello</h1>");
    }

    @Test
    @Order(2)
    void staticFile_directoryTraversal_dotDot_rejected() throws IOException {
        String response = handleAndCapture("/../../../etc/passwd");

        int status = extractStatusCode(response);
        assertThat(status)
                .as("SECURITY: Directory traversal via .. must be rejected. "
                  + "An attacker sending GET /../../../etc/passwd is trying to read system files. "
                  + "Canonicalize paths and verify they stay within the document root. "
                  + "Use Path.toRealPath() or Path.normalize() and check that the resolved "
                  + "path startsWith(documentRoot).")
                .isBetween(400, 403);
    }

    @Test
    @Order(3)
    void staticFile_directoryTraversal_encoded_rejected() throws IOException {
        // %2e = '.' (percent-encoded)
        // %2e%2e/%2e%2e = ../../
        String response = handleAndCapture("/%2e%2e/%2e%2e/etc/passwd");

        int status = extractStatusCode(response);
        assertThat(status)
                .as("SECURITY: Percent-encoded directory traversal (%%2e%%2e = ..) must also be rejected. "
                  + "Your path resolver must decode URIs BEFORE checking for traversal. "
                  + "A common mistake is to check for '..' in the raw URI string but not in "
                  + "the decoded version. Use java.net.URLDecoder or manual percent-decoding.")
                .isBetween(400, 403);
    }

    @Test
    @Order(4)
    void staticFile_directoryTraversal_doubleEncoded_rejected() throws IOException {
        // %25 = '%', so %252e decodes to %2e, which decodes to '.'
        // %252e%252e = .. (after double decoding)
        String response = handleAndCapture("/%252e%252e/etc/passwd");

        int status = extractStatusCode(response);
        assertThat(status)
                .as("SECURITY: Double-encoded directory traversal must be rejected. "
                  + "%%252e decodes to %%2e (first pass), which decodes to '.' (second pass). "
                  + "This is a common bypass technique: if the server only decodes once and "
                  + "checks for '..', then double-encoded sequences slip through. "
                  + "Either decode fully before path resolution, or reject any URI containing "
                  + "'%%25' (which indicates double encoding).")
                .isBetween(400, 403);
    }

    @Test
    @Order(5)
    void staticFile_nullByteInPath_rejected() throws IOException {
        // Null byte injection: %00 = null byte (0x00)
        // In some languages/runtimes, the null byte truncates the string
        // so "/index.html\0.png" might be interpreted as "/index.html"
        String response = handleAndCapture("/index.html%00.png");

        int status = extractStatusCode(response);
        assertThat(status)
                .as("SECURITY: Null byte injection must be rejected. "
                  + "A URI containing %%00 (null byte) is a classic attack vector. "
                  + "In C-based runtimes, the null byte truncates the filename, so "
                  + "'/index.html%%00.png' might open '/index.html' instead of "
                  + "'/index.html\\0.png'. Reject any URI containing null bytes.")
                .isBetween(400, 403);
    }

    @Test
    @Order(6)
    void staticFile_nonexistentFile_returns404() throws IOException {
        String response = handleAndCapture("/doesnotexist.html");

        int status = extractStatusCode(response);
        assertThat(status)
                .as("A request for a file that does not exist in the document root must "
                  + "return 404 Not Found. Use Files.exists() or catch NoSuchFileException "
                  + "to detect missing files.")
                .isEqualTo(404);
    }
}

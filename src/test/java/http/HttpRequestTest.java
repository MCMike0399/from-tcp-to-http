package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the HttpRequest data class.
 * Verifies that the constructor stores values correctly and that the class
 * enforces immutability (unmodifiable headers, defensive copy of body).
 */
@DisplayName("Module 3: HttpRequest Data Class Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpRequestTest {

    @Test
    @Order(1)
    void httpRequest_gettersReturnConstructorValues() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("localhost"));
        headers.put("Accept", List.of("text/html"));
        byte[] body = "Hello".getBytes();

        HttpRequest request = new HttpRequest("GET", "/index.html", "HTTP/1.1", headers, body);

        assertThat(request.getMethod())
                .as("getMethod() must return the exact method string passed to the constructor.")
                .isEqualTo("GET");

        assertThat(request.getUri())
                .as("getUri() must return the exact URI string passed to the constructor.")
                .isEqualTo("/index.html");

        assertThat(request.getVersion())
                .as("getVersion() must return the exact version string passed to the constructor.")
                .isEqualTo("HTTP/1.1");

        assertThat(request.getHeaders())
                .as("getHeaders() must contain the same header entries passed to the constructor.")
                .containsEntry("Host", List.of("localhost"))
                .containsEntry("Accept", List.of("text/html"));

        assertThat(request.getBody())
                .as("getBody() must return the exact bytes passed to the constructor.")
                .isEqualTo("Hello".getBytes());
    }

    @Test
    @Order(2)
    void httpRequest_headersReturnedAsUnmodifiable() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("localhost"));

        HttpRequest request = new HttpRequest("GET", "/", "HTTP/1.1", headers, null);
        Map<String, List<String>> returned = request.getHeaders();

        assertThatThrownBy(() -> returned.put("Injected", List.of("evil")))
                .as("HttpRequest must be immutable. The headers map must be unmodifiable.")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @Order(3)
    void httpRequest_bodyReturnedAsDefensiveCopy() {
        byte[] original = "Hello".getBytes();
        HttpRequest request = new HttpRequest("GET", "/", "HTTP/1.1", Collections.emptyMap(), original);

        byte[] firstCopy = request.getBody();
        firstCopy[0] = 'X'; // mutate the returned array

        byte[] secondCopy = request.getBody();

        assertThat(secondCopy)
                .as("getBody() must return a defensive copy each time. "
                  + "Mutating a previously returned array must not affect the internal state. "
                  + "This prevents callers from accidentally corrupting the request data.")
                .isEqualTo("Hello".getBytes());
    }

    @Test
    @Order(4)
    void httpRequest_nullMethodThrows() {
        assertThatThrownBy(() ->
                new HttpRequest(null, "/", "HTTP/1.1", Collections.emptyMap(), null))
                .as("Constructing an HttpRequest with a null method must throw IllegalArgumentException. "
                  + "Every HTTP request must have a method (RFC 9110 Section 9).")
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates that the RequestParser correctly rejects malformed HTTP requests
 * by throwing ProtocolException. A robust parser must never crash on bad input;
 * it must diagnose the problem and signal it with an appropriate error.
 */
@DisplayName("Module 3: Malformed Request Rejection Tests (E3.2)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestLineParserMalformedTest {

    private final RequestParser parser = new RequestParser();

    @Test
    @Order(1)
    void parse_emptyInput_throwsProtocolException() {
        byte[] raw = "".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("An empty input stream contains no request line at all. "
                  + "The parser must throw ProtocolException, not return null or crash.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(2)
    void parse_onlyCRLF_throwsProtocolException() {
        byte[] raw = "\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("A bare CRLF with no request line is not a valid HTTP request. "
                  + "RFC 9112 Section 2.2 allows ignoring a leading CRLF, "
                  + "but there must still be a request line following it.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(3)
    void parse_missingMethod_throwsProtocolException() {
        byte[] raw = "/ HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("A request line must have exactly three tokens: method SP request-target SP HTTP-version. "
                  + "'/ HTTP/1.1' has the wrong structure and must be rejected.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(4)
    void parse_missingUri_throwsProtocolException() {
        byte[] raw = "GET HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("'GET HTTP/1.1' only has two tokens. The request-target (URI) is missing, "
                  + "so this is malformed per RFC 9112 Section 3.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(5)
    void parse_missingVersion_throwsProtocolException() {
        byte[] raw = "GET /\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("'GET /' is an HTTP/0.9 simple request, but we require HTTP/1.1 format. "
                  + "The version token is missing. RFC 9112 Section 3: "
                  + "request-line = method SP request-target SP HTTP-version.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(6)
    void parse_invalidMethodChars_throwsProtocolException() {
        byte[] raw = "G3T / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("HTTP method tokens must consist of uppercase letters only (tchar subset). "
                  + "'G3T' contains a digit, which is not a valid method character. "
                  + "RFC 9110 Section 9.1: method = token.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(7)
    void parse_extraSpaces_throwsProtocolException() {
        byte[] raw = "GET  / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("RFC 9112 Section 3 requires exactly one SP between each request-line component. "
                  + "Double spaces between method and URI make the request malformed.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(8)
    void parse_headerWithoutColon_throwsProtocolException() {
        byte[] raw = "GET / HTTP/1.1\r\nBadHeader\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("Each header field must contain a colon separating the name from the value. "
                  + "'BadHeader' has no colon and is not a valid header field. "
                  + "RFC 9112 Section 5: header-field = field-name ':' OWS field-value OWS.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(9)
    void parse_headerNameWithSpace_throwsProtocolException() {
        byte[] raw = "GET / HTTP/1.1\r\nBad Header: value\r\n\r\n".getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("Header field names must not contain spaces. "
                  + "'Bad Header' has a space before the colon and is malformed. "
                  + "RFC 9112 Section 5: no whitespace is allowed between the field-name and colon.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(10)
    void parse_contentLengthNegative_throwsProtocolException() {
        byte[] raw = ("POST /data HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: -1\r\n"
                + "\r\n").getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("A negative Content-Length is invalid. "
                  + "RFC 9110 Section 8.6: Content-Length = 1*DIGIT (non-negative integer).")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(11)
    void parse_contentLengthNonNumeric_throwsProtocolException() {
        byte[] raw = ("POST /data HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: abc\r\n"
                + "\r\n").getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("A non-numeric Content-Length must be rejected. "
                  + "The server must not attempt to guess or ignore an invalid length.")
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @Order(12)
    void parse_contentLengthOverflow_throwsProtocolException() {
        byte[] raw = ("POST /data HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 99999999999999999\r\n"
                + "\r\n").getBytes(US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(raw)))
                .as("A Content-Length value that exceeds reasonable bounds must be rejected. "
                  + "Accepting absurdly large values could lead to resource exhaustion attacks.")
                .isInstanceOf(ProtocolException.class);
    }
}

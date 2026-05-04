package http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Fuzz tests feed the parser random, truncated, or corrupted input to verify
 * it never crashes with an unexpected exception. A production-quality parser
 * must handle any byte sequence gracefully -- either parsing it successfully
 * or throwing a clean IOException/ProtocolException.
 */
@DisplayName("Module 3: Request Parser Fuzz Tests (E3.5)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestParserFuzzTest {

    @Test
    @Order(1)
    void fuzz_randomBytes_neverThrowsUnexpectedException() {
        Random rng = new Random(12345L);
        RequestParser parser = new RequestParser();

        for (int i = 0; i < 1000; i++) {
            int length = 1 + rng.nextInt(500);
            byte[] garbage = new byte[length];
            rng.nextBytes(garbage);

            try {
                parser.parse(new ByteArrayInputStream(garbage));
                // If it parses without error, that's fine (unlikely but allowed)
            } catch (IOException e) {
                // IOException (including ProtocolException) is the expected outcome
                // for random bytes -- this is correct behavior
            } catch (Exception e) {
                fail("Random input (seed=12345, iteration=%d, length=%d) caused %s: %s. "
                   + "The parser must only throw IOException (including ProtocolException) "
                   + "on malformed input. Any other exception (NPE, ArrayIndexOutOfBoundsException, etc.) "
                   + "indicates a robustness bug.",
                        i, length, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Test
    @Order(2)
    void fuzz_truncatedRequests_throwProtocolException() {
        byte[] valid = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        RequestParser parser = new RequestParser();

        for (int truncateAt = 1; truncateAt < valid.length; truncateAt++) {
            byte[] truncated = new byte[truncateAt];
            System.arraycopy(valid, 0, truncated, 0, truncateAt);

            try {
                parser.parse(new ByteArrayInputStream(truncated));
                // Some prefixes might be parseable if they happen to form a valid
                // (shorter) request -- but most truncations should fail
            } catch (IOException e) {
                // Expected: ProtocolException or IOException for incomplete request
            } catch (Exception e) {
                fail("A truncated HTTP request (connection closed mid-parse) must throw ProtocolException, "
                   + "not crash. TCP connections can close at any time. "
                   + "Truncated at byte %d of %d, got %s: %s",
                        truncateAt, valid.length, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Test
    @Order(3)
    void fuzz_nullBytesInRequest_handledGracefully() {
        byte[] valid = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII);
        Random rng = new Random(67890L);
        RequestParser parser = new RequestParser();

        for (int i = 0; i < 100; i++) {
            // Create a copy and insert null bytes at random positions
            byte[] corrupted = valid.clone();
            int numNulls = 1 + rng.nextInt(5);
            for (int n = 0; n < numNulls; n++) {
                int pos = rng.nextInt(corrupted.length);
                corrupted[pos] = 0x00;
            }

            try {
                parser.parse(new ByteArrayInputStream(corrupted));
                // Parsing may succeed if nulls landed in non-critical positions
            } catch (IOException e) {
                // ProtocolException or IOException is acceptable
            } catch (Exception e) {
                fail("Null bytes (0x00) injected into a request (iteration=%d) caused %s: %s. "
                   + "The parser must handle null bytes gracefully -- either ignore them "
                   + "or throw ProtocolException. Never NPE or crash.",
                        i, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}

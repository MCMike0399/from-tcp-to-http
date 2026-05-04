package streams;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.ChunkedOnlyInputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that validate the finite state machine (FSM) behavior of LineReader (E0.3).
 *
 * LineReader's delimiter detection is best modeled as a 2-state FSM:
 *
 *   State: READING_CHARS (initial)
 *     - byte is \n         -> emit line, stay in READING_CHARS
 *     - byte is \r         -> transition to SAW_CR
 *     - any other byte     -> append to buffer, stay in READING_CHARS
 *
 *   State: SAW_CR
 *     - byte is \n         -> emit line (the \r\n pair is consumed), -> READING_CHARS
 *     - byte is not \n     -> append \r to buffer, append this byte, -> READING_CHARS
 *     - EOF                -> flush buffer (including the \r) as final line
 *
 * These tests verify each state transition, using ChunkedOnlyInputStream to force
 * the transitions to occur at chunk boundaries (the hardest case).
 */
@DisplayName("Module 0: LineReader FSM Behavior Tests (E0.3)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineReaderFSMTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void fsm_normalChars_thenLF_producesLine() throws IOException {
        byte[] data = "abc\n".getBytes(StandardCharsets.UTF_8);
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 2);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("FSM transition: READING_CHARS -> (accumulate 'a','b','c') -> (sees \\n) -> emit 'abc'. "
                  + "With chunkSize=2, data arrives as 'ab','c\\n'. The \\n in the second chunk "
                  + "triggers line emission. This is the simplest FSM path: only READING_CHARS state.")
                .isEqualTo("abc");
    }

    @Test
    @Order(2)
    void fsm_normalChars_thenCRLF_producesLine() throws IOException {
        byte[] data = "abc\r\n".getBytes(StandardCharsets.UTF_8);
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 2);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("FSM transition: READING_CHARS -> (accumulate 'a','b','c') -> (sees \\r) -> SAW_CR "
                  + "-> (sees \\n) -> emit 'abc'. With chunkSize=2, data arrives as 'ab','c\\r','\\n'. "
                  + "The FSM enters SAW_CR when it sees \\r, then confirms the \\r\\n pair when \\n follows. "
                  + "Neither \\r nor \\n appears in the returned line.")
                .isEqualTo("abc");
    }

    @Test
    @Order(3)
    void fsm_crlfSplitAtChunkBoundary_producesLine() throws IOException {
        byte[] data = "abc\r\ndef\n".getBytes(StandardCharsets.UTF_8);
        // chunkSize=4: first chunk "abc\r", second chunk "\ndef", third chunk "\n"
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 4);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("FSM state persistence across chunks: chunk 1 is 'abc\\r'. The FSM sees \\r at the "
                  + "end of the chunk and enters SAW_CR state. Chunk 2 starts with \\n, confirming the "
                  + "\\r\\n pair. The SAW_CR state MUST persist across read(byte[]) calls -- this is why "
                  + "it must be a field, not a local variable. This is the core TCP challenge: "
                  + "protocol tokens split across network reads.")
                .isEqualTo("abc");

        assertThat(reader.readLine())
                .as("After the split \\r\\n, the remaining bytes '\\ndef' from chunk 2 plus chunk 3 "
                  + "form the line 'def\\n' -> 'def'. The FSM resets to READING_CHARS after emitting 'abc'.")
                .isEqualTo("def");
    }

    @Test
    @Order(4)
    void fsm_crFollowedByNonLF_crIsContent() throws IOException {
        byte[] data = "abc\rd\n".getBytes(StandardCharsets.UTF_8);
        // chunkSize=4: first chunk "abc\r", second chunk "d\n"
        ChunkedOnlyInputStream stream = new ChunkedOnlyInputStream(data, 4);
        LineReader reader = new LineReader(stream);

        assertThat(reader.readLine())
                .as("FSM fallback transition: chunk 1 is 'abc\\r'. The FSM enters SAW_CR. "
                  + "Chunk 2 starts with 'd' (not \\n). The FSM must realize the \\r was NOT part "
                  + "of a \\r\\n pair, so it transitions back to READING_CHARS and includes the \\r "
                  + "in the line content. The line is 'abc\\rd', terminated by \\n. "
                  + "This tests: SAW_CR -> (non-\\n byte) -> back to READING_CHARS with \\r as content.")
                .isEqualTo("abc\rd");
    }
}

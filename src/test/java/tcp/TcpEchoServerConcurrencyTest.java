package tcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import testutil.RawTcpClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests that verify the TCP Echo Server can handle multiple
 * simultaneous clients. These tests require the server to spawn a thread
 * (or virtual thread) per connection.
 */
@DisplayName("Module 1: TCP Echo Server Concurrency Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TcpEchoServerConcurrencyTest {

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void echoServer_multipleConcurrentClients_allServed() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            int clientCount = 10;
            CountDownLatch ready = new CountDownLatch(clientCount);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(clientCount);
            ConcurrentHashMap<Integer, String> results = new ConcurrentHashMap<>();
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < clientCount; i++) {
                final int clientId = i;
                Thread.startVirtualThread(() -> {
                    try {
                        ready.countDown();
                        go.await(5, TimeUnit.SECONDS);

                        try (RawTcpClient client = new RawTcpClient("localhost", server.getPort())) {
                            String message = "Client-" + clientId;
                            client.sendLine(message);
                            String response = client.readLine();
                            results.put(clientId, response);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // Wait for all threads to be ready, then release them simultaneously
            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("All client threads should be ready within 5 seconds.")
                    .isTrue();
            go.countDown();

            // Wait for all threads to complete
            assertThat(done.await(5, TimeUnit.SECONDS))
                    .as("All %d clients must complete within 5 seconds. If this times out, "
                      + "the server is likely single-threaded and processes connections sequentially. "
                      + "Each connection must be handled on its own thread.", clientCount)
                    .isTrue();

            assertThat(errors.get())
                    .as("No client threads should have thrown exceptions.")
                    .isZero();

            assertThat(results)
                    .as("All %d clients must receive their echoed response.", clientCount)
                    .hasSize(clientCount);

            for (int i = 0; i < clientCount; i++) {
                assertThat(results.get(i))
                        .as("Client-%d should receive 'Client-%d' echoed back, "
                          + "but got '%s'. Each connection must be independent.", i, i, results.get(i))
                        .isEqualTo("Client-" + i);
            }
        }
    }

    @Test
    @Order(2)
    void echoServer_slowClient_doesNotBlockOthers() throws Exception {
        try (TcpEchoServer server = new TcpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            int port = server.getPort();

            // Client A: connects but does not send anything (slow client)
            Thread slowClient = Thread.startVirtualThread(() -> {
                try (RawTcpClient clientA = new RawTcpClient("localhost", port)) {
                    // Just hold the connection open for 2 seconds without sending
                    Thread.sleep(2_000);
                } catch (Exception ignored) {
                    // Expected -- connection may be interrupted
                }
            });

            // Give Client A time to connect
            Thread.sleep(100);

            // Client B: connects after A, sends immediately
            long startTime = System.nanoTime();
            try (RawTcpClient clientB = new RawTcpClient("localhost", port)) {
                clientB.sendLine("Fast");
                String response = clientB.readLine();
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

                assertThat(response)
                        .as("Client B sent 'Fast' and should receive 'Fast' back, regardless of "
                          + "Client A being idle. Got '%s' instead.", response)
                        .isEqualTo("Fast");

                assertThat(elapsedMs)
                        .as("A slow client must not block other clients. This requires one thread "
                          + "per connection (or an event loop). Client B's response took %dms, "
                          + "but it should complete well under 1 second. If it took ~2s, the server "
                          + "is blocked waiting on Client A. "
                          + "See Conceptual Foundation 6: Blocking I/O and the C10K Problem.",
                                elapsedMs)
                        .isLessThan(1_000);
            }

            slowClient.join(3_000);
        }
    }
}

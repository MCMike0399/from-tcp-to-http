package tcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the UDP Echo Server, verifying connectionless datagram-based
 * echo behavior. Unlike TCP, UDP has no connection setup, no ordering
 * guarantees, and no stream abstraction.
 */
@DisplayName("Module 1: UDP Echo Server Tests (E1.3)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UdpEchoServerTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void sendUdp(DatagramSocket socket, String message, int port) throws Exception {
        byte[] data = message.getBytes(StandardCharsets.US_ASCII);
        DatagramPacket packet = new DatagramPacket(
                data, data.length,
                InetAddress.getLoopbackAddress(), port
        );
        socket.send(packet);
    }

    private static String receiveUdp(DatagramSocket socket) throws Exception {
        byte[] buf = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII);
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void udpEcho_singlePacket_echosBack() throws Exception {
        try (UdpEchoServer server = new UdpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            try (DatagramSocket clientSocket = new DatagramSocket()) {
                clientSocket.setSoTimeout(5_000);

                sendUdp(clientSocket, "Hello", server.getPort());
                String response = receiveUdp(clientSocket);

                assertThat(response)
                        .as("After sending a UDP datagram with 'Hello', the server should "
                          + "echo back a datagram containing 'Hello'. Unlike TCP, there is no "
                          + "connection -- each datagram is self-contained with its own destination.")
                        .isEqualTo("Hello");
            }
        }
    }

    @Test
    @Order(2)
    void udpEcho_multiplePackets_allEchoed() throws Exception {
        try (UdpEchoServer server = new UdpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            try (DatagramSocket clientSocket = new DatagramSocket()) {
                clientSocket.setSoTimeout(5_000);
                int packetCount = 10;

                // Send all packets
                Set<String> sent = new HashSet<>();
                for (int i = 0; i < packetCount; i++) {
                    String message = "Msg-" + i;
                    sent.add(message);
                    sendUdp(clientSocket, message, server.getPort());
                }

                // Receive all packets (UDP is unordered, so collect as a set)
                Set<String> received = new HashSet<>();
                for (int i = 0; i < packetCount; i++) {
                    received.add(receiveUdp(clientSocket));
                }

                assertThat(received)
                        .as("All %d UDP datagrams must be echoed back. "
                          + "UDP does not guarantee ordering, so we compare as sets. "
                          + "Note: UDP can also lose packets, but on localhost this is extremely rare.",
                                packetCount)
                        .isEqualTo(sent);
            }
        }
    }

    @Test
    @Order(3)
    void udpEcho_noConnectionRequired() throws Exception {
        try (UdpEchoServer server = new UdpEchoServer(0)) {
            server.start();
            Thread.sleep(100);

            int port = server.getPort();

            // Two completely independent DatagramSockets -- no connect() called
            try (DatagramSocket socket1 = new DatagramSocket();
                 DatagramSocket socket2 = new DatagramSocket()) {

                socket1.setSoTimeout(5_000);
                socket2.setSoTimeout(5_000);

                sendUdp(socket1, "FromSocket1", port);
                String response1 = receiveUdp(socket1);

                sendUdp(socket2, "FromSocket2", port);
                String response2 = receiveUdp(socket2);

                assertThat(response1)
                        .as("UDP is connectionless. Unlike TCP, there is no accept(), no handshake, "
                          + "no connection state. Each datagram is independent. "
                          + "Socket 1 sent 'FromSocket1' and should receive it back. "
                          + "See Module 1: TCP vs UDP comparison.")
                        .isEqualTo("FromSocket1");

                assertThat(response2)
                        .as("Socket 2 sent 'FromSocket2' and should receive it back. "
                          + "The server uses the source address from each incoming datagram "
                          + "to send the reply -- no prior connection needed.")
                        .isEqualTo("FromSocket2");
            }
        }
    }
}

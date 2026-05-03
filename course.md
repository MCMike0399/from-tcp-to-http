# From TCP to HTTP: Building a Protocol Server from Scratch in Java

> **Course Philosophy**: This course adapts the "HTTP from Scratch" methodology to the Java ecosystem. We will not use Spring Boot, Tomcat, Jetty, Netty, or any framework. We will use only `java.net`, `java.io`, `java.nio`, and the standard library to construct an HTTP/1.1 server atop raw TCP sockets.
>
> **Golden Rule**: If you cannot build the abstraction yourself, you do not truly understand the abstraction you use every day at work.

---

## Prerequisites & Mindset
- You know the OSI model (you studied computer engineering).
- You know Java syntax and OOP.
- You do **not** need to know Go. The video's Go code is only a reference; we translate concepts to Java idioms.
- **Required Tools**: `curl`, `nc`/`ncat` (netcat), Wireshark or `tcpdump`, a hex viewer (`hexdump -C` or an IDE plugin), Java 21+ (for virtual threads, though 17 works with classic threads).

---

## Module 0: The Stream Abstraction — Files as Networks
**Goal**: Internalize that a network connection is just a stream of ordered bytes. The interface is identical to a file.

### Topics
- **Java Stream Interfaces**: `InputStream`, `OutputStream`, `Reader`, `Writer`. The contract: `read(byte[])` returns `-1` on EOF.
- **Reading 8 bytes at a time**: Using `FileInputStream` with a small `byte[8]` buffer. Observe how arbitrary chunking breaks text lines.
- **The Line Problem**: Building a line iterator over raw bytes. Detecting `\n` and `\r\n` (the "Registered Nurse" sequence from the video).
- **Buffering logic**: Why we need a buffer that persists across `read()` calls. Partial lines, leftover bytes, and state management.
- **Java NIO Preview**: `ByteBuffer`, `ReadableByteChannel` — how they map to the same stream concept but with explicit buffer control.

### Exercises
1. **E0.1**: Write a `LineReader` class that takes any `InputStream` and yields one `String` line at a time. **Do not use `BufferedReader` or `Scanner`.** Implement your own byte buffer and newline detection.
2. **E0.2**: Test your `LineReader` against a text file. Then test it against `System.in` (type lines in terminal). Observe: the interface is identical whether the bytes come from disk or a human.

### Pedagogical Note
This mirrors the video's opening: before touching the network, master the stream. In Java, `Socket.getInputStream()` and `FileInputStream` share the same abstract contract. This is your "aha" moment for Layer 4 → Layer 7.

---

## Module 1: TCP — The Reliable Pipe
**Goal**: Replace the file stream with a TCP socket. Observe that the *reading code does not change*.

### Topics
- **ServerSocket & Socket**: `ServerSocket.bind()`, the blocking `accept()` loop, and the resulting `Socket` instance.
- **TCP Mechanics** (deep dive from video):
  - **Sliding Window**: How TCP sends packets 1–4 before waiting for ACK of packet 1.
  - **Reliability**: ACKs, retransmission timeouts, duplicate detection.
  - **Ordering**: Sequence numbers and reconstruction. Why a JSON blob would explode if bytes arrived out of order.
  - **Handshake**: SYN → SYN-ACK → ACK. Why `ServerSocket` throws if no one is listening.
- **TCP vs UDP**:
  - UDP: Datagrams, stateless, no handshake, no ordering. The "I could tell you a UDP joke, but you might not get it" lesson.
  - Packet loss: NACKs vs ACKs. Why UDP is faster (1% loss rate means 99% of the time you wait for nothing).
  - **QUIC & HTTP/3**: TCP semantics implemented over UDP (relevance to modern engineering).
  - **DTLS**: Why encryption over UDP is "trixy" — no structure means no easy TLS.
- **Java Contrast**: Blocking I/O (`ServerSocket`) vs NIO non-blocking (`ServerSocketChannel`, `Selector`). We start with blocking to reduce complexity, then refactor later.

### Exercises
1. **E1.1**: Build a TCP echo server on port `42069` (or any port). It accepts a connection, reads lines using your `LineReader` from Module 0, and echoes them back. Test with `telnet localhost 42069` or `nc localhost 42069`.
2. **E1.2**: Kill the server. Run `nc` again. Observe `Connection refused`. Feel TCP's requirement for a live listener.
3. **E1.3**: Build a UDP echo server using `DatagramSocket`. Send packets with `nc -u`. Notice: no connection, no refusal, packets may vanish silently.

### Milestone Checkpoint
> **M1**: You can explain to a junior engineer why `Socket.getInputStream()` and `FileInputStream` are interchangeable from the parser's perspective, but differ in who controls the timing (pull vs push).

---

## Module 2: HTTP Semantics & The RFC
**Goal**: Understand HTTP/1.1 as a text protocol specification, not as magic performed by frameworks.

### Topics
- **Why HTTP over TCP?**: TCP gives ordered bytes; HTTP gives *meaning* to those bytes (method, path, headers, body type).
- **The RFCs**:
  - **RFC 9110**: HTTP Semantics (applies to HTTP/1.1, HTTP/2, HTTP/3).
  - **RFC 9112**: HTTP/1.1 Messaging (what we implement).
- **HTTP Message Structure**:
  ```
  Start-Line CRLF
  *( Field-Line CRLF )
  CRLF
  [ Message-Body ]
  ```
  - **Start-Line**: For requests, the `Request-Line` = `METHOD SP REQUEST-URI SP HTTP-VERSION`.
  - **Field-Lines (Headers)**: `Field-Name: Field-Value`. Case-insensitive names. Order technically doesn't matter for semantics, but matters for parsing.
  - **The Empty Line**: `\r\n` with nothing before it. This is the only delimiter between headers and body.
  - **Body strategies**: `Content-Length` (fixed size) vs `Transfer-Encoding: chunked` (streaming, unknown size).
- **Methods & Idempotency**: GET, POST, HEAD. Why GET usually has no body.
- **Raw HTTP Inspection**:
  - `curl -v http://localhost:42069/ > /dev/null` to see request headers.
  - `curl -X POST -d '{"key":"val"}' -H "Content-Type: application/json"` to see a body-bearing request.
  - Capture with `nc` and inspect the raw bytes.

### Exercises
1. **E2.1**: Use `curl` against any public site. Save the raw request to a temp file using `--trace-ascii`. Identify every `\r\n`. Count them.
2. **E2.2**: Manually type an HTTP request into `nc`:
   ```
   GET / HTTP/1.1

   Host: localhost

   

   ```
   Observe the server response (or lack thereof if you miss the empty line).
3. **E2.3**: Read RFC 9112 §3. Request Line. Write a one-page summary in your own words.

### Milestone Checkpoint
> **M2**: You can draw an HTTP request on paper, labeling every CRLF, and explain why the empty line is the most important byte sequence in HTTP/1.1.

---

## Module 3: The Request Parser (Test-Driven Protocol Implementation)
**Goal**: Convert raw TCP bytes into a structured `HttpRequest` Java object using TDD.

### Topics
- **Testing Philosophy** (ThePrimeagen's approach, adapted):
  - Tests should be **declarative**, not clever. Avoid table-driven tests while learning — they hide logic inside loops.
  - Write tests for things you *cannot* get right first try. Text parsing always deserves tests.
  - Use JUnit 5 with explicit, named test methods. Example: `void shouldParseSimpleGetRequestLine()`.
- **Parsing the Request-Line**:
  - `METHOD`: Token parsing (uppercase letters, but be tolerant).
  - `REQUEST-URI`: Path parsing. Absolute vs origin-form.
  - `HTTP-VERSION`: `HTTP/1.1` → `HTTP` + `/` + `1` + `.` + `1`.
- **Parsing Headers**:
  - Folding (deprecated but know it exists).
  - Multi-value headers (`Accept: text/html, application/json`).
  - Header name normalization (lowercase internal storage).
- **Body Awareness**:
  - When to read the body: only after headers are fully parsed.
  - How many bytes to read: `Content-Length` header value.
  - What if `Content-Length` is missing and it's not chunked? In HTTP/1.1, assume no body.

### Target Data Structure
```java
public class HttpRequest {
    private final String method;
    private final String uri;
    private final String version;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    // ... getters, but no public setters (immutable after parsing)
}
```

### Exercises
1. **E3.1**: Write `RequestLineParserTest`. Start with the simplest possible GET request. Red → Green → Refactor.
2. **E3.2**: Add a test for malformed request line (e.g., missing HTTP version). Your parser should throw a `ProtocolException`.
3. **E3.3**: Integrate with your TCP server from Module 1. When a client connects, parse the first line. Print the method and URI to stdout.

### Milestone Checkpoint
> **M3**: Your parser passes explicit JUnit tests for: (a) simple GET, (b) POST with headers, (c) malformed request line rejection.

---

## Module 4: Server Architecture & Concurrency
**Goal**: Handle multiple simultaneous TCP connections and route HTTP requests to handlers.

### Topics
- **The Classic Thread-per-Connection Model**:
  - `new Thread(() -> handle(socket)).start()`. Simple, but heavy.
  - Thread pools (`ExecutorService`) for resource control.
- **Java Virtual Threads (Java 21+)**:
  - `Thread.startVirtualThread(() -> handle(socket))`.
  - How they compare to Go goroutines (lightweight, managed by JVM/runtime).
  - Why this matters: you can spawn 10,000 virtual threads but not 10,000 platform threads.
- **Connection State**:
  - HTTP/1.1 persistent connections (`Connection: keep-alive`).
  - Detecting connection close: `read()` returns `-1`.
- **Routing**: A simple `Map<String, BiConsumer<HttpRequest, OutputStream>>` or a `Handler` interface.
  ```java
  public interface HttpHandler {
      void handle(HttpRequest request, OutputStream responseBody) throws IOException;
  }
  ```

### Exercises
1. **E4.1**: Refactor your server to handle each connection in a virtual thread. Load-test with `wrk` or `oha`. Observe memory usage.
2. **E4.2**: Implement a router. `GET /` → "Hello World". `GET /health` → `{"status":"up"}`. `POST /echo` → echoes body.
3. **E4.3**: Add `Connection: close` support. After serving one request, close the socket. Then implement keep-alive (serve multiple requests on one connection until client closes).

### Milestone Checkpoint
> **M4**: Your server serves a simple HTML page to a real browser (Chrome/Firefox) using `ServerSocket` and virtual threads.

---

## Module 5: HTTP Responses & Advanced Messaging
**Goal**: Generate valid HTTP responses and serve non-text content over raw TCP.

### Topics
- **Status-Line**: `HTTP/1.1 200 OK`. Parsing and generating.
- **Response Headers**:
  - `Content-Type`: MIME types. `text/html`, `application/json`, `video/mp4`.
  - `Content-Length`: Mandatory for responses with body (unless chunked).
  - `Connection`: `keep-alive` vs `close`.
- **Serving Static Files**:
  - Mapping URI path to filesystem path securely (prevent directory traversal: `GET ../../../etc/passwd` should fail).
  - Reading files and streaming directly to `Socket.getOutputStream()`.
- **Chunked Transfer Encoding** (from video):
  - Why: streaming data when total size is unknown.
  - Format: `size_in_hex\r\n` + `data` + `\r\n`. Final chunk: `0\r\n\r\n`.
  - Exercise: implement a `/stream` endpoint that sends chunks every second.
- **Binary Data & MP4 Serving**:
  - HTTP does not care about content. Bytes are bytes.
  - Serving an MP4 video file. Setting `Content-Type: video/mp4`.
  - Verifying in browser DevTools → Network → Response headers.

### Exercises
1. **E5.1**: Serve a directory of files. Implement safe path resolution.
2. **E5.2**: Implement chunked encoding. Create an endpoint that generates infinite `Hello\n` chunks until client disconnects.
3. **E5.3**: Serve a 50MB MP4 file. Open it in a browser `<video>` tag. Verify the browser sends a `Range` request (optional bonus: handle `206 Partial Content`).

### Milestone Checkpoint
> **M5**: A browser can play an MP4 video served by your Java server over a raw TCP socket with no framework.

---

## Module 6: Testing, Debugging, and Protocol Compliance
**Goal**: Harden your server against real-world HTTP clients.

### Topics
- **Integration Testing with Java's `HttpClient`** (Java 11+):
  - Write tests that start your server on a random port, send a request, and assert on the response.
- **Wireshark/tcpdump**:
  - Filter: `tcp.port == 42069`.
  - Inspect TCP segments: SYN, ACK, PSH, FIN.
  - Inspect HTTP layer: confirm your bytes are exactly right.
- **Common Bugs**:
  - Off-by-one `Content-Length`.
  - Missing `\r\n` after headers → browser hangs forever waiting for body start.
  - Blocking on body read when client sent no body.
  - Not handling `-1` from `read()` → infinite loops.
- **Fuzzing**: Send garbage bytes to your server. Does it crash or gracefully return `400 Bad Request`?

### Capstone Project
Build a server that:
1. Passes a custom RFC-compliance test suite (provided by your agent/tutor).
2. Serves a single-page website with HTML, CSS, and an MP4 video.
3. Handles at least 100 concurrent connections without crashing.
4. Logs every request line to stdout.

### Milestone Checkpoint
> **M6**: Your server is demonstrably compliant, concurrent, and capable of serving rich media.

---

## Module 7: The OSI Model in Practice — Closing the Loop
**Goal**: Map your daily software engineering work back to the layers you built.

### Topics
- **Layer 4 (Transport)**: You built this. TCP segments, ports, flow control, kernel socket buffers.
- **Layer 5 (Session)**: HTTP is stateless, so cookies/JWT are a poor man's session layer. TLS handshakes live here too.
- **Layer 6 (Presentation)**: JSON, Protobuf, encryption. HTTP doesn't care; it just carries bytes.
- **Layer 7 (Application)**: HTTP methods, status codes, REST semantics. The layer you *thought* you knew until you built it.
- **The Java Stack Deep Dive**:
  - `Socket.getOutputStream().write()` → JVM native call → OS socket buffer (`sendbuf`) → TCP segmentation → NIC driver → Ethernet → Wire.
  - Where does the JVM end and the kernel begin?
- **Reflection Essay**:
  - Write 500 words on: *"Every time I call `restTemplate.getForObject()` at work, these are the layers I am implicitly trusting. Now that I have built them, which abstractions do I respect more, and which do I distrust?"*

### Milestone Checkpoint
> **M7**: You have written the reflection and can whiteboard the full path of an HTTP POST from browser → your Java server → response, labeling OSI layers and Java classes at each step.

---

## Creative Extensions (Beyond the Video)
1. **The NIO Refactor**: Rebuild your server with `ServerSocketChannel`, `Selector`, and non-blocking I/O. Understand why Netty exists.
2. **HTTP/2 Frame Parser**: Read RFC 9113. Implement a toy HPACK decoder. Appreciate why HTTP/1.1's text format is beautiful for learning.
3. **Load Balancer Simulation**: Write a TCP proxy in Java that accepts connections and forwards to your server. Inspect how `X-Forwarded-For` is a Layer 7 hack for Layer 3 information.
4. **Benchmark & Optimize**: Use `wrk -t12 -c400 -d30s`. Profile with `async-profiler`. Is the bottleneck parsing, I/O, or thread scheduling?

---

## Artifact Registry
As you progress, maintain these files in your repo:
- `src/main/java/streams/LineReader.java` (Module 0)
- `src/main/java/tcp/TcpEchoServer.java` (Module 1)
- `src/main/java/http/HttpRequest.java` (Module 3)
- `src/main/java/http/RequestLineParser.java` (Module 3)
- `src/test/java/http/RequestLineParserTest.java` (Module 3)
- `src/main/java/server/HttpServer.java` (Module 4)
- `src/main/java/server/Router.java` (Module 4)
- `src/main/java/response/ChunkedEncoder.java` (Module 5)
- `REFLECTION.md` (Module 7)

---

## Resources
- **RFC 9110**: [HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- **RFC 9112**: [HTTP/1.1](https://www.rfc-editor.org/rfc/rfc9112.html)
- **Java 21 Virtual Threads**: [JEP 444](https://openjdk.org/jeps/444)
- **Tools**: `curl`, `ncat`, Wireshark, `tcpdump`, `wrk`, `oha`, `hexdump -C`

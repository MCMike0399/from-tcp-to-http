# From TCP to HTTP: Building a Protocol Server from Scratch in Java

> **Course Philosophy**: This course adapts the "HTTP from Scratch" methodology (inspired by [ThePrimeagen's Boot.dev course](https://www.boot.dev/courses/learn-http-protocol-golang)) to the Java ecosystem. We will not use Spring Boot, Tomcat, Jetty, Netty, or any framework. We will use only `java.net`, `java.io`, `java.nio`, and the standard library to construct an HTTP/1.1 server atop raw TCP sockets.
>
> **Golden Rule**: If you cannot build the abstraction yourself, you do not truly understand the abstraction you use every day at work.
>
> **Design Philosophy**: "There is no better way to understand how something works than to implement it yourself." -- ThePrimeagen

---

## Prerequisites & Mindset
- You know the OSI model (you studied computer engineering).
- You know Java syntax and OOP.
- You do **not** need to know Go. The video's Go code is only a reference; we translate concepts to Java idioms.
- **Required Tools**: `curl`, `nc`/`ncat` (netcat), Wireshark or `tcpdump`, a hex viewer (`hexdump -C` or an IDE plugin), Java 21+ (for virtual threads).

---

## Core References

### Required Reading (pick one textbook + the RFCs)

| Resource | Author(s) | Why It Matters |
|---|---|---|
| **Computer Networking: A Top-Down Approach**, 9th Ed. (2025) | James Kurose & Keith Ross | The standard university networking textbook. Starts from HTTP and works down to the physical layer. Free companion video lectures available. |
| **TCP/IP Illustrated, Vol. 1: The Protocols**, 2nd Ed. (2011) | W. Richard Stevens & Kevin Fall | The definitive visual guide to TCP/IP. Uses real packet traces and diagrams to explain every protocol. Indispensable for understanding what happens on the wire. |
| **RFC 9112: HTTP/1.1** | IETF | The wire format we implement: request/response lines, chunked encoding, connection management. Read at [httpwg.org](https://httpwg.org/specs/rfc9112.html). |
| **RFC 9110: HTTP Semantics** | IETF | Defines methods, status codes, headers, content negotiation -- the "what" of HTTP, independent of wire format. Read at [httpwg.org](https://httpwg.org/specs/rfc9110.html). |

### Supplementary Reading

| Resource | Author(s) | Why It Matters |
|---|---|---|
| **HTTP: The Definitive Guide** (2002) | David Gourley & Brian Totty | 600+ page deep dive into HTTP message format, methods, status codes, caching, proxies, HTTPS. Old but exhaustive. |
| **High Performance Browser Networking** (free at [hpbn.co](https://hpbn.co/)) | Ilya Grigorik (Google) | Covers TCP, TLS, HTTP/1.1, HTTP/2, WebSocket with a performance focus. Essential for understanding real-world protocol behavior. |
| **Java Network Programming**, 4th Ed. (2013) | Elliotte Rusty Harold | Java sockets, server sockets, UDP, NIO with complete working examples. The most comprehensive Java-specific networking book. |
| **TCP/IP Sockets in Java**, 2nd Ed. (2008) | Kenneth Calvert & Michael Donahoo | The most directly relevant book: Java socket programming with line-by-line commentary, NIO channels, and mapping from Java to underlying protocol mechanics. [Companion site](https://cs.baylor.edu/~donahoo/practical/JavaSockets/). |
| **Java Concurrency in Practice** (2006) | Brian Goetz et al. | The definitive guide to writing correct concurrent Java. Critical for building a multi-threaded HTTP server. |
| **Effective Java**, 3rd Ed. (2018) | Joshua Bloch | 90 best-practice items for writing robust Java code. Not networking-specific but essential for production-quality Java. |
| **UNIX Network Programming, Vol. 1**, 3rd Ed. (2003) | W. Richard Stevens et al. | The authoritative sockets API guide. Even Java developers benefit because Java's socket model mirrors BSD sockets 1:1. |
| **Practical Packet Analysis**, 3rd Ed. (2017) | Chris Sanders (No Starch Press) | Hands-on Wireshark-based book. Capture your own server's traffic and analyze TCP handshakes, HTTP framing, retransmissions on the wire. |
| **Beej's Guide to Network Programming** (free at [beej.us/guide/bgnet](https://beej.us/guide/bgnet/)) | Brian "Beej" Hall | The most beloved introductory guide to C socket programming. Understanding the underlying C APIs illuminates Java's abstractions. |
| **Wizard Zines: Bite Size Networking!** + **HTTP: Learn Your Browser's Language!** | Julia Evans | Visual one-page explanations of networking concepts. Exceptionally clear for working engineers. [wizardzines.com](https://wizardzines.com/) |
| **Networking! ACK! (zine)** (free [PDF](https://jvns.ca/networking-zine.pdf)) | Julia Evans | 24-page illustrated zine covering packets to TCP handshakes to HTTP. Surprisingly dense. Builds intuition about what happens at each layer. |
| **http2 explained** (free at [daniel.haxx.se/http2](https://daniel.haxx.se/http2/)) | Daniel Stenberg (curl author) | Concise practical guide to HTTP/2 binary framing, multiplexing, HPACK. |
| **HTTP/3 Explained** (free at [http3-explained.haxx.se](https://http3-explained.haxx.se/en)) | Daniel Stenberg | Covers QUIC, UDP-based transport, and why TCP's head-of-line blocking motivated a complete transport rethink. |
| **Netty in Action** (2015) | Norman Maurer & Marvin Wolfthal | After building from scratch, study how Netty solves the same problems (event loops, channel pipelines, buffer management). The "why frameworks exist" book. |

### Foundational Papers (read these to understand *why*, not just *how*)

| Paper | Author(s) | Year | Core Idea |
|---|---|---|---|
| **"A Protocol for Packet Network Intercommunication"** | Vint Cerf & Robert Kahn | 1974 | The paper that invented TCP. Describes reliable host-to-host communication across heterogeneous networks. [PDF](https://www.cs.princeton.edu/courses/archive/fall06/cos561/papers/cerf74.pdf) |
| **"The Design Philosophy of the DARPA Internet Protocols"** | David D. Clark | 1988 | Explains *why* TCP/IP is designed the way it is -- the priority ordering of goals that drove every decision. [PDF](https://web.mit.edu/6.033/www/papers/darpa.pdf) |
| **"End-to-End Arguments in System Design"** | J.H. Saltzer, D.P. Reed & D.D. Clark | 1984 | The most influential network design principle: implement functions at endpoints, not in the network. Explains why TCP does retransmission at endpoints. [PDF](https://web.mit.edu/saltzer/www/publications/endtoend/endtoend.pdf) |
| **"Architectural Styles and the Design of Network-Based Software Architectures"** (PhD Dissertation) | Roy T. Fielding | 2000 | Defines REST. Fielding co-authored HTTP/1.1 -- this dissertation explains the design rationale behind HTTP's methods, status codes, and statelessness. [HTML](https://ics.uci.edu/~fielding/pubs/dissertation/top.htm) |
| **"Information Management: A Proposal"** | Tim Berners-Lee | 1989 | The original proposal for the World Wide Web that led to HTTP, HTML, and URLs. The document that started it all. [HTML](https://www.w3.org/History/1989/proposal.html) |
| **"Congestion Avoidance and Control"** | Van Jacobson | 1988 | Introduced slow start, congestion avoidance, fast retransmit, and fast recovery -- the algorithms that make TCP work on congested networks. [PDF](https://ee.lbl.gov/papers/congavoid.pdf) |
| **"On Holy Wars and a Plea for Peace"** | Danny Cohen | 1980 | The paper that coined "big-endian" and "little-endian" (from Gulliver's Travels). A short, funny, essential read on byte order and why network byte order exists. [PDF](https://www.ietf.org/rfc/ien/ien137.txt) |
| **"Development of the Domain Name System"** | Paul Mockapetris & Kevin Dunlap | 1988 | How DNS was designed and why. Understanding DNS is prerequisite to understanding how HTTP clients resolve hostnames before opening TCP connections. |

### Course Inspiration

| Resource | What It Covers |
|---|---|
| **ThePrimeagen: From TCP to HTTP** ([Boot.dev](https://www.boot.dev/courses/learn-http-protocol-golang)) | The inspiration for this course. Building an HTTP server from TCP sockets in Go. We translate concepts to Java idioms. |

### RFC Reference Table

| RFC | Title | When You Need It |
|-----|-------|-----------------|
| **RFC 9110** | HTTP Semantics | Methods, status codes, headers, content negotiation |
| **RFC 9111** | HTTP Caching | Cache-Control, ETag, conditional requests |
| **RFC 9112** | HTTP/1.1 | Wire format: request/response lines, chunked encoding, connection management |
| **RFC 9113** | HTTP/2 | Binary framing, multiplexing, HPACK (Module 7 extension) |
| **RFC 9114** | HTTP/3 | HTTP over QUIC (future reference) |
| **RFC 9293** | TCP (2022, replaces RFC 793) | The current TCP specification with state diagrams |
| **RFC 793** | TCP (1981 original) | Worth reading for its clarity; the original state machine diagrams |
| **RFC 768** | UDP | The UDP spec; useful as contrast to TCP |
| **RFC 6265** | HTTP Cookies | Set-Cookie/Cookie headers for stateful HTTP |
| **RFC 7617** | HTTP Basic Authentication | Base64 user:password encoding |
| **RFC 2818** | HTTP Over TLS | How HTTPS works |
| **RFC 896** | Nagle's Algorithm | The original congestion control for small packets; why TCP_NODELAY exists |

### How to Read RFCs

Before implementing RFC 9112, learn to read RFCs effectively:

| Resource | Author | Why It Matters |
|---|---|---|
| **[How to Read an RFC](https://www.mnot.net/blog/2018/07/31/read_rfc)** | Mark Nottingham (former IETF HTTP WG chair) | Written by the person who shepherded RFCs 9110-9112. Covers MUST/SHOULD/MAY keywords, requirement targets, and how to navigate the IETF ecosystem. The definitive guide. |
| **[IETF Blog: How to Read an RFC](https://www.ietf.org/blog/how-read-rfc/)** | IETF | Official guide to RFC categories (Standards Track, Informational, BCP) and the standards process. |
| **[The Networking RFCs: To read or not to read?](https://packetpushers.net/blog/the-networking-rfcs-to-read-or-not-to-read/)** | Packet Pushers | Pragmatic take on which RFCs are worth reading directly vs. absorbed through secondary sources. |

### Articles & Blog Posts

#### TCP Internals

| Article | Author | Why It Matters |
|---|---|---|
| **[Let's Code a TCP/IP Stack](https://www.saminiir.com/lets-code-tcp-ip-stack-1-ethernet-arp/)** (5-part series) | Sami Niiranen | Builds a userspace TCP/IP stack in C from Ethernet frames up through TCP retransmission. The best "implement TCP yourself" blog series. Source: [github.com/saminiir/level-ip](https://github.com/saminiir/level-ip). |
| **[Why You Should Understand (a Little) About TCP](https://jvns.ca/blog/2015/11/21/why-you-should-understand-a-little-about-tcp/)** | Julia Evans | The delayed ACK + Nagle interaction that causes 40ms latency in HTTP request/response patterns. Your server will exhibit this exact bug without TCP_NODELAY. |
| **[Why Do We Use the Linux Kernel's TCP Stack?](https://jvns.ca/blog/2016/06/30/why-do-we-use-the-linux-kernels-tcp-stack/)** | Julia Evans | What the kernel TCP stack actually does (retransmission, congestion control, flow control) and why userspace stacks exist. |
| **[It's Always TCP_NODELAY](https://brooker.co.za/blog/2024/05/09/nagle.html)** | Marc Brooker (AWS) | TCP_NODELAY should always be enabled for request/response protocols. The 1984 problem is irrelevant today. |
| **[TCP Congestion Control Deep Dive](https://pingdo.net/performance/tcp-congestion-control-deep-dive/)** | Pingdo | Window scaling, CUBIC, and BBR algorithms beyond the textbook slow-start explanation. |
| **[The Internet is Cool. Thank you, TCP](https://cefboud.com/posts/tcp-deep-dive-internals/)** | Moncef Abboud | Modern, well-illustrated deep dive into TCP internals from a practitioner's perspective. |
| **[Optimizing TCP for High WAN Throughput](https://blog.cloudflare.com/optimizing-tcp-for-high-throughput-and-low-latency/)** | Cloudflare Engineering | TCP receive windows, `tcp_rmem`, bandwidth-delay product. Read after your server works, when you want to understand why large transfers are slow over high-latency links. |
| **[How to Read from a TCP Socket](https://incoherency.co.uk/blog/stories/reading-tcp-sockets.html)** | James Stanley | Why `read()` doesn't return what you expect. The article that destroys the "one write = one read" myth. |

#### HTTP Protocol

| Article | Author | Why It Matters |
|---|---|---|
| **[HTTP/1.0 From Scratch](https://kmcd.dev/posts/http1.0-from-scratch/)** | Kevin McDonald | Concise walkthrough of building a minimal HTTP/1.0 server. Good warm-up before HTTP/1.1 complexity. |
| **[HTTP Made Really Easy](https://www.jmarshall.com/easy/http/)** | James Marshall | A classic 15-page practical guide to HTTP/1.0 and 1.1. Focused on the wire format for implementers. |
| **[HTTP/1.1 Explained](https://http.dev/1.1)** | http.dev | Comprehensive guide more accessible than the RFC itself. Good companion reader alongside RFC 9112. |
| **[HTTP Keep-Alive, Pipelining, Multiplexing & Connection Pooling](https://www.haproxy.com/blog/http-keep-alive-pipelining-multiplexing-and-connection-pooling)** | HAProxy team | Production-grade explanation by people who handle millions of connections daily. |
| **[Connection Management in HTTP/1.x](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Connection_management_in_HTTP_1.x)** | Mozilla (MDN) | Authoritative reference on persistent connections and pipelining with clean diagrams. |
| **[HTTP Desync Attacks: Request Smuggling Reborn](https://portswigger.net/research/http-desync-attacks-request-smuggling-reborn)** | James Kettle (PortSwigger) | Catalogs every edge case where HTTP parsers disagree. Content-Length vs Transfer-Encoding conflicts, chunked extension parsing, line terminator variations. The adversarial spec your parser must survive. |
| **[The Road to QUIC](https://blog.cloudflare.com/the-road-to-quic/)** | Cloudflare Engineering | Why QUIC replaces TCP for HTTP: handshake costs and transport-layer head-of-line blocking. |
| **[Message Framing](https://blog.stephencleary.com/2009/04/message-framing.html)** | Stephen Cleary | The foundational article on delimiter vs length-prefix framing strategies. |

#### Java Networking & I/O

| Article | Author | Why It Matters |
|---|---|---|
| **[Virtual Threads Deep Dive](https://www.happycoders.eu/java/virtual-threads/)** | Sven Woltmann (HappyCoders) | How virtual threads mount/unmount on carrier threads, why pooling them is an anti-pattern, memory/performance considerations. |
| **[Java 21 Virtual Threads -- Dude, Where's My Lock?](https://netflixtechblog.com/java-21-virtual-threads-dude-wheres-my-lock-3052540e231d)** | Netflix Technology Blog | Production war story: Netflix hit a deadlock where all carrier threads were pinned inside `synchronized` blocks. Essential before using `synchronized` in your server. |
| **[Beyond Loom: Weaving New Concurrency Patterns](https://developers.redhat.com/articles/2023/10/03/beyond-loom-weaving-new-concurrency-patterns)** | Red Hat Developer | Structured concurrency patterns for network servers beyond basic virtual thread usage. |
| **[Efficient Java I/O: byte[], ByteBuffers, and OutputStreams](https://www.evanjones.ca/software/java-bytebuffers.html)** | Evan Jones | NIO write path benchmarks. Reveals that NIO heap buffers secretly allocate temporary direct buffers. Essential for HTTP response performance. |
| **[Deep Dive into Java IO Models: BIO, NIO, AIO](https://gfish.online/en/blog/java-bio-nio-aio-io-models/)** | gfish.online | Systematic comparison with the restaurant analogy (BIO = one waiter per table; NIO = one waiter polling many tables). Covers epoll, kqueue, and why Netty prefers edge-triggered epoll. |
| **[Asynchronous Non-blocking I/O Under the Hood](https://tuhuynh.com/en/posts/nio-under-the-hood/)** | Tu Huynh | The OS-level system calls Java NIO's Selector wraps: `select()`, `poll()`, `epoll()`, `kqueue()`. Why epoll is O(1) while select is O(n). |
| **[Java Sequential I/O Performance](https://mechanical-sympathy.blogspot.com/2011/12/java-sequential-io-performance.html)** | Martin Thompson (Mechanical Sympathy) | Benchmarks of every Java I/O mechanism by the LMAX Disruptor creator. Understand overhead layers in Java I/O. |
| **[JEP 444: Virtual Threads](https://openjdk.org/jeps/444)** | OpenJDK | The JDK Enhancement Proposal itself. Surprisingly readable -- motivation, design decisions, and alternatives considered. |
| **[JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)** | OpenJDK | Fixes the Netflix deadlock problem. Short and worth reading in full. |

### Open Source Code to Study

Study these after building your own implementation to see how production code solves the same problems:

| Project | Language | Why Study It |
|---|---|---|
| **[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)** | Java | Single-file HTTP server (~1500 lines). Request parsing, MIME types, cookies, SSL, chunked encoding. Read the whole thing in an afternoon. |
| **[JLHTTP](https://github.com/curtcox/JLHTTP)** | Java | Single source file, ~50KB jar, zero dependencies. Small enough to read completely and understand every design decision. |
| **[picohttpparser](https://github.com/h2o/picohttpparser)** | C | Tiny, zero-allocation HTTP parser used by the H2O web server. Demonstrates state-machine parsing with SIMD optimization. What production parsing looks like at the byte level. |
| **[llhttp](https://llhttp.org/)** ([source](https://github.com/nodejs/llhttp)) | C (generated) | Node.js's HTTP parser, generated from a TypeScript state machine description. Shows how to formally specify a parser as a state machine graph. |
| **[smoltcp](https://github.com/smoltcp-rs/smoltcp)** | Rust | Complete TCP/IP stack for embedded systems. No heap allocation. The TCP implementation (~2000 lines) is more approachable than the Linux kernel's. |
| **[level-ip](https://github.com/saminiir/level-ip)** | C | Companion code to saminiir's "Let's Code a TCP/IP Stack" blog series. Linux userspace TCP/IP stack using TUN/TAP. |
| **[Undertow](https://github.com/undertow-io/undertow)** | Java | JBoss's high-performance non-blocking HTTP server. Compositional handler chain architecture. Under 1MB, embeddable. |

### Interactive & Hands-On Resources

| Resource | What It Offers |
|---|---|
| **[Low-Level Academy](https://lowlvl.org/)** | Interactive, browser-based exercises for UDP and TCP. Write real protocol code that runs against test servers in your browser. Exceptional for building intuition. |
| **[CodeCrafters: Build Your Own HTTP Server](https://app.codecrafters.io/courses/http-server/overview)** | Progressive challenge with automated tests at each stage: TCP setup, request parsing, response formatting, file serving, concurrency. Available in Java. |
| **[Coding Challenges: Build Your Own Web Server](https://codingchallenges.fyi/challenges/challenge-webserver/)** | Structured weekly challenge by John Crickett. Part of a broader series (Redis, DNS, etc.). |
| **[Protohackers](https://protohackers.com/)** ([tips](https://ellen.dev/protohackers-tips.html)) | Challenges 0-3 for protocol implementation practice with automated verification. |
| **[Kurose/Ross Interactive Exercises](https://gaia.cs.umass.edu/kurose_ross/interactive/)** | Browser-based problems: subnetting, routing, TCP window management. Auto-graded, infinite variations. |
| **[Kurose/Ross Wireshark Labs](https://gaia.cs.umass.edu/kurose_ross/wireshark.php)** | Structured labs for analyzing HTTP, TCP, DNS with real packet captures. Run these against your own server. |
| **[Build Your Own X](https://github.com/codecrafters-io/build-your-own-x)** | Curated mega-list of "build from scratch" tutorials: web servers, TCP stacks, HTTP clients, across many languages. |

### University Courses (Open Access)

| Course | University | What It Offers |
|---|---|---|
| **CS144: Introduction to Computer Networking** | Stanford | Build a working TCP implementation across 6 labs. [cs144.github.io](https://cs144.github.io/) |
| **6.829: Computer Networks** | MIT | Graduate-level: congestion control, routing, QoS. The reading list alone is a goldmine. [OCW](https://ocw.mit.edu/courses/6-829-computer-networks-fall-2002/) |
| **6.033: Computer System Engineering** (Networking weeks) | MIT | Networking within a broader systems context: end-to-end arguments, layering, protocol design philosophy. [OCW](https://ocw.mit.edu/courses/6-033-computer-system-engineering-spring-2018/pages/week-5/) |

---

## Conceptual Foundations

Before diving into code, internalize these principles. They recur throughout every module.

### 1. TCP Is a Byte Stream, Not a Message Protocol

TCP provides a pair of ordered byte streams between two endpoints. There are no "messages," no "packets" visible to the application, and no boundaries. If the sender writes 100 bytes followed by 200 bytes, the receiver may read 80, then 120, then 100. The application must impose its own structure on the stream.

> **The Analogy**: TCP is a water pipe. You pour water in one end; it comes out the other. You cannot send "messages" through a pipe -- only a continuous stream. If you need discrete messages, add your own structure: colored markers (delimiters like `\r\n`) or labels on containers ("the next N liters are one batch" -- `Content-Length`).

> **The Localhost Trap**: On localhost, `write(100 bytes)` often corresponds to `read(100 bytes)` because the loopback is fast. This creates a false sense of message boundaries. It breaks immediately over a real network or under load.

**Reference**: Stevens, *TCP/IP Illustrated Vol. 1*, Chapters 12-14. Also: [How to Read from a TCP Socket](https://incoherency.co.uk/blog/stories/reading-tcp-sockets.html) (James Stanley).

### 2. Message Framing

Since TCP has no message boundaries, application protocols must define their own. There are two fundamental strategies:

- **Delimiter-based**: Use a special byte sequence to mark the end of a logical unit. HTTP uses `\r\n` for header lines and `\r\n\r\n` to mark the end of headers.
- **Length-prefix**: Prepend each message with its byte length. HTTP uses `Content-Length` for fixed-size bodies.
- **Hybrid**: HTTP/1.1 uses *both* -- delimiters for headers, length-prefix (or chunked encoding) for bodies.

> **The Analogy**: Without framing, TCP is a fax machine sending a continuous roll of paper. Length-prefixing is putting each document in an envelope with the page count on the outside. Delimiter-based framing is using a bright red separator page between documents.

**Reference**: [Message Framing](https://blog.stephencleary.com/2009/04/message-framing.html) (Stephen Cleary).

### 3. The Parser as a Finite State Machine

An HTTP/1.1 parser maps naturally to a finite state machine (FSM):

```
READING_METHOD --(space)--> READING_URI --(space)--> READING_VERSION --(CRLF)-->
READING_HEADER_NAME --(:)--> READING_HEADER_VALUE --(CRLF)-->
  [loop back to READING_HEADER_NAME, or if empty line:]
READING_BODY --(Content-Length bytes consumed)--> DONE
```

Each incoming byte triggers a state transition. This is how production parsers work -- Node.js's `llhttp`, nginx's parser, and Apache's all use explicit state machines.

> **The Analogy**: An HTTP parser is a vending machine. `IDLE` -> (insert coin) -> `HAS_CREDIT` -> (press button) -> `DISPENSING` -> (take item) -> `IDLE`. Each input causes exactly one transition to exactly one next state.

**Reference**: W3C Architecture Documentation on [Protocol Modules as State Machines](https://www.w3.org/Library/User/Architecture/HTTPFeatures.html). Also: node.js `llhttp` source code.

### 4. The Berkeley Sockets API

Java's `ServerSocket`/`Socket` is a thin wrapper over the Berkeley sockets API, created at UC Berkeley in 1983 for 4.2BSD Unix. The method names map 1:1:

| Java | BSD C | Purpose |
|------|-------|---------|
| `new ServerSocket(port)` | `socket()` + `bind()` + `listen()` | Create listening endpoint |
| `serverSocket.accept()` | `accept()` | Block until client connects; return new socket |
| `socket.getInputStream().read()` | `recv()` / `read()` | Read bytes from peer |
| `socket.getOutputStream().write()` | `send()` / `write()` | Write bytes to peer |
| `socket.close()` | `close()` | Tear down connection |

Understanding this lineage explains API quirks: why `bind()` and `listen()` are conceptually separate, why `accept()` returns a *new* socket instead of reusing the listener, and why `Socket` and `FileInputStream` share the same `InputStream` contract -- because Unix treats everything as a file descriptor.

**Reference**: Beej's Guide to Network Programming, Chapter 2-5. Also: Stevens, *UNIX Network Programming Vol. 1*.

### 5. Endianness and Network Byte Order

Multi-byte integers are stored differently on different architectures. **Big-endian** stores the most significant byte first; **little-endian** stores the least significant byte first. Network byte order is standardized as big-endian (RFC 1700).

In Java, `DataOutputStream.writeInt()` uses big-endian (network byte order) by default, and `ByteBuffer` defaults to big-endian. This is convenient but important to understand when reading binary protocol specifications or debugging with hex viewers.

> **The Origin Story**: The terms come from Danny Cohen's 1980 paper, borrowing from Swift's *Gulliver's Travels* -- the Lilliputians' war over which end of an egg to crack. The choice is arbitrary, but agreement is essential.
>
> **The NUXI Problem**: The string "UNIX" stored in big-endian may appear as "NUXI" on a little-endian machine if bytes aren't converted. A memorable reminder of what goes wrong.

**Reference**: [Understanding Big and Little Endian Byte Order](https://betterexplained.com/articles/understanding-big-and-little-endian-byte-order/) (BetterExplained).

### 6. Blocking I/O, the C10K Problem, and Virtual Threads

How does a single server handle 10,000 concurrent connections? This was the "C10K problem" (Dan Kegel, ~1999), and the answer depends on your I/O model:

1. **Thread-per-connection** (blocking): Simple but each idle connection consumes a platform thread (~1MB stack).
2. **`select()`** (1983): First I/O multiplexing. Limited to 1024 FDs. Linear scan cost.
3. **`epoll()`/`kqueue`**: Event-driven. O(1) per ready event. Enables millions of connections on one thread.
4. **Java Virtual Threads (Project Loom, Java 21+)**: Write blocking-style code, but the JVM multiplexes millions of lightweight threads onto a small carrier pool. Combines the simplicity of model 1 with the scalability of model 3.

We start with blocking I/O (model 1), graduate to virtual threads (model 4), and discuss NIO/selectors (model 3) as context for why frameworks like Netty exist.

**Reference**: [The C10K Problem](https://www.kegel.com/c10k.html) (Dan Kegel). Also: JEP 444 (Virtual Threads).

### 7. Connection Lifecycle and TIME_WAIT

TCP connections follow a state machine: `CLOSED` -> `LISTEN` -> `SYN_SENT` / `SYN_RECEIVED` -> `ESTABLISHED` -> `FIN_WAIT_1/2` or `CLOSE_WAIT` -> `TIME_WAIT` -> `CLOSED`.

**TIME_WAIT**: Entered by whichever side initiates the close. Lasts ~60 seconds. Exists to prevent stale packets from a previous connection being misinterpreted on a new connection using the same port pair.

> **The Analogy**: After hanging up a phone call, you keep the line reserved for 60 seconds in case a delayed voicemail arrives. If you give the line to a new caller immediately, they might hear fragments of the old conversation.

**Design Rule**: Servers should generally NOT initiate active close (to avoid accumulating TIME_WAIT sockets). Design protocols so the client closes.

**Reference**: Stevens, *TCP/IP Illustrated Vol. 1*, Chapter 13. Also: RFC 9293, Section 3.6.

### 8. Nagle's Algorithm and TCP_NODELAY

Nagle's algorithm (RFC 896, 1984) batches small outgoing writes: hold data until either all outstanding data is ACKed or enough accumulates to fill a segment. Prevents the "small-packet problem" (1-byte payloads creating 41-byte packets).

**The Deadly Interaction**: Nagle's algorithm + Delayed ACKs = latency spikes. Nagle waits for an ACK; Delayed ACK waits ~40ms hoping to piggyback the ACK. Both sides wait for each other. Even Nagle himself called the combination "awful."

**Modern Guidance**: Marc Brooker (AWS) argues `TCP_NODELAY` should always be enabled for latency-sensitive systems. The original 1984 problem (1-byte payloads on slow networks) is irrelevant today.

**Reference**: [It's Always TCP_NODELAY](https://brooker.co.za/blog/2024/05/09/nagle.html) (Marc Brooker). RFC 896.

### 9. Common Misconceptions to Destroy

| Misconception | Reality |
|---|---|
| "TCP sends messages" | TCP is a byte stream. No boundaries. Your application must frame messages. |
| "One `write()` = one `read()`" | TCP coalesces and fragments freely. Works on localhost by coincidence. |
| "HTTP is always text" | Headers are ASCII text. Bodies are arbitrary bytes (images, video, protobuf). |
| "TCP is reliable, so no error handling" | TCP guarantees delivery *while the connection is up*. Sockets reset, half-close, and time out. |
| "Closing a socket is instant" | Active closer enters TIME_WAIT for ~60s. Can exhaust ephemeral ports under load. |
| "Headers are simple key-value pairs" | Names are case-insensitive. Multiple same-name headers are valid and must be preserved as a list. `Set-Cookie` cannot be combined. |
| "`Content-Length` is always present" | Absent for chunked encoding, HEAD responses, 1xx/204/304, and bodyless requests. |
| "HTTP is stateless at the TCP level" | HTTP is stateless as a protocol; but persistent connections multiplex multiple requests over one TCP connection. |

---

## Module 0: The Stream Abstraction -- Files as Networks
**Goal**: Internalize that a network connection is just a stream of ordered bytes. The interface is identical to a file.

### Concepts to Master
- **Java Stream Interfaces**: `InputStream`, `OutputStream`, `Reader`, `Writer`. The contract: `read(byte[])` returns `-1` on EOF.
- **Reading 8 bytes at a time**: Using `FileInputStream` with a small `byte[8]` buffer. Observe how arbitrary chunking breaks text lines.
- **The Line Problem**: Building a line iterator over raw bytes. Detecting `\n` and `\r\n`.
- **Buffering logic**: Why we need a buffer that persists across `read()` calls. Partial lines, leftover bytes, and state management.
- **Java NIO Preview**: `ByteBuffer`, `ReadableByteChannel` -- how they map to the same stream concept but with explicit buffer control.

### Key Concept: Bytes vs Characters

HTTP is a byte-level protocol. Do not casually use `Scanner` or `BufferedReader` -- they assume character encoding and may mangle binary data. Your `LineReader` operates on `byte[]` and only converts to `String` after isolating a complete line. This distinction is critical when the body is binary (images, video) but the headers are ASCII.

> **Reading**: Kurose & Ross, Chapter 2 (Application Layer) -- the concept of sockets as application-process interfaces. Also: Java SE docs for `InputStream` contract.

### Exercises
1. **E0.1**: Write a `LineReader` class that takes any `InputStream` and yields one `String` line at a time. **Do not use `BufferedReader` or `Scanner`.** Implement your own byte buffer and newline detection.
2. **E0.2**: Test your `LineReader` against a text file. Then test it against `System.in` (type lines in terminal). Observe: the interface is identical whether the bytes come from disk or a human.
3. **E0.3** (new): Draw the state machine for your `LineReader`: states `READING_CHARS`, `SAW_CR`, `SAW_CRLF`. Label transitions with the byte values that trigger them. This is your first protocol parser.

### Test Validation
Run `./gradlew testStreams` to validate your implementation. All tests must pass before proceeding to Module 1.
- **LineReaderCorrectnessTest**: Basic delimiter detection (LF, CRLF, mixed), empty streams, EOF handling
- **LineReaderEnforcementTest**: Verifies chunk-based reading (not byte-by-byte), no BufferedReader/Scanner, no O(n^2) string concatenation
- **LineReaderEdgeCaseTest**: Lone CR, null bytes, empty lines, trailing CR at EOF
- **LineReaderFSMTest**: State machine transitions (READING_CHARS, SAW_CR) via crafted chunk boundaries

### Dig Deeper (Module 0)
- Kurose & Ross, Chapter 2 (Application Layer) -- sockets as application-process interfaces
- Java SE docs for `InputStream` contract -- what `read(byte[])` actually guarantees
- [Efficient Java I/O: byte[], ByteBuffers, and OutputStreams](https://www.evanjones.ca/software/java-bytebuffers.html) (Evan Jones) -- performance characteristics of Java I/O paths
- [Java Sequential I/O Performance](https://mechanical-sympathy.blogspot.com/2011/12/java-sequential-io-performance.html) (Martin Thompson) -- benchmarks of every Java I/O mechanism

---

## Module 1: TCP -- The Reliable Pipe
**Goal**: Replace the file stream with a TCP socket. Observe that the *reading code does not change*.

### Concepts to Master
- **ServerSocket & Socket**: `ServerSocket.bind()`, the blocking `accept()` loop, and the resulting `Socket` instance.
- **TCP Mechanics** (deep dive):
  - **Three-Way Handshake**: SYN -> SYN-ACK -> ACK. Why `ServerSocket` throws if no one is listening.
  - **Sliding Window**: How TCP sends packets 1-4 before waiting for ACK of packet 1.
  - **Reliability**: ACKs, retransmission timeouts, duplicate detection.
  - **Ordering**: Sequence numbers and reconstruction.
  - **Flow Control**: The receiver advertises its receive window (`rwnd`) -- spare room in the receive buffer. Sender must obey: `LastByteSent - LastByteAcked <= rwnd`.
  - **Congestion Control**: Slow start, congestion avoidance, fast retransmit, fast recovery (Van Jacobson, 1988).
- **TCP vs UDP**:
  - UDP: Datagrams, stateless, no handshake, no ordering.
  - Packet loss: Why UDP is faster (1% loss rate means 99% of the time TCP's reliability is overhead).
  - **QUIC & HTTP/3**: TCP semantics reimplemented over UDP, eliminating TCP's head-of-line blocking.
- **Connection Lifecycle**: The TCP state machine -- `ESTABLISHED`, `FIN_WAIT`, `CLOSE_WAIT`, `TIME_WAIT`. Why `TIME_WAIT` exists and why the active closer pays the cost.
- **`SO_REUSEADDR`**: Why your server throws "Address already in use" after restart and how to fix it.

### Key Concept: The Three-Way Handshake

> **The Analogy**: "Hello, I'd like to talk" (SYN) -> "Hello, I hear you and I'd like to talk too" (SYN-ACK) -> "Great, I hear you too, let's begin" (ACK). The third step is necessary because without it, the server can't confirm the client received the SYN-ACK.

### Key Concept: Backpressure and Flow Control

> **The Analogy**: The receiver's buffer is a warehouse with limited shelf space. The advertised window is the warehouse telling the supplier: "I have room for X more pallets." When shelves fill up, the warehouse says "stop sending." Workers process inventory and free space; they update the supplier. The sender's persist timer periodically asks: "Got any room yet?"

> **Reading**: Stevens, *TCP/IP Illustrated Vol. 1*, Chapters 12-14 (TCP connection management, data flow). Also: Cerf & Kahn (1974) for the original TCP design. Clark (1988) for the design philosophy behind TCP/IP.

### Exercises
1. **E1.1**: Build a TCP echo server on port `42069`. Accept a connection, read lines using your `LineReader` from Module 0, and echo them back. Test with `telnet localhost 42069` or `nc localhost 42069`.
2. **E1.2**: Kill the server. Run `nc` again. Observe `Connection refused`. Feel TCP's requirement for a live listener.
3. **E1.3**: Build a UDP echo server using `DatagramSocket`. Send packets with `nc -u`. Notice: no connection, no refusal, packets may vanish silently.
4. **E1.4** (new): Run your echo server and connect with `nc`. While connected, run `netstat -an | grep 42069` in another terminal. Identify which sockets are in `LISTEN` vs `ESTABLISHED` state. Close the client and observe `TIME_WAIT`. Enable `SO_REUSEADDR` on your `ServerSocket` and understand why.
5. **E1.5** (new): Use `tcpdump -i lo0 port 42069` (or Wireshark) to capture the three-way handshake. Identify SYN, SYN-ACK, ACK packets. Observe sequence numbers incrementing.

### Test Validation
Run `./gradlew testTcp` to validate your implementation.
- **TcpEchoServerCorrectnessTest**: Echo behavior, multi-line, long lines, client disconnect handling
- **TcpEchoServerEnforcementTest**: SO_REUSEADDR, socket cleanup, fragmented input handling
- **TcpEchoServerConcurrencyTest**: 10 concurrent clients, slow client isolation
- **UdpEchoServerTest**: UDP echo, connectionless behavior (E1.3)

### Dig Deeper (Module 1)
- Stevens, *TCP/IP Illustrated Vol. 1*, Chapters 12-14 -- TCP connection management, data flow
- Cerf & Kahn (1974) -- the paper that invented TCP
- Clark (1988) -- the design philosophy behind TCP/IP
- [Let's Code a TCP/IP Stack](https://www.saminiir.com/lets-code-tcp-ip-stack-1-ethernet-arp/) (saminiir) -- build TCP from Ethernet frames up
- [Why You Should Understand (a Little) About TCP](https://jvns.ca/blog/2015/11/21/why-you-should-understand-a-little-about-tcp/) (Julia Evans)
- [The Internet is Cool. Thank you, TCP](https://cefboud.com/posts/tcp-deep-dive-internals/) (Moncef Abboud) -- modern TCP internals illustrated
- [TCP Flow Control](https://www.brianstorti.com/tcp-flow-control/) (Brian Storti) -- the sliding window explained visually
- Beej's Guide, Chapters 2-5 -- the C sockets API that Java wraps
- [Low-Level Academy](https://lowlvl.org/) -- interactive TCP exercises in the browser

### Milestone Checkpoint
> **M1**: You can explain to a junior engineer why `Socket.getInputStream()` and `FileInputStream` are interchangeable from the parser's perspective, but differ in who controls the timing. You can identify TCP states in `netstat` output.

---

## Module 2: HTTP Semantics & The RFC
**Goal**: Understand HTTP/1.1 as a text protocol specification, not as magic performed by frameworks.

### Concepts to Master
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
  - **Start-Line**: For requests, `Request-Line` = `METHOD SP REQUEST-URI SP HTTP-VERSION`.
  - **Field-Lines (Headers)**: `Field-Name: Field-Value`. Case-insensitive names. Repeatable.
  - **The Empty Line**: `\r\n` with nothing before it. The only delimiter between headers and body.
  - **Body strategies**: `Content-Length` (fixed) vs `Transfer-Encoding: chunked` (streaming).
- **Methods & Semantics**:
  - **Safe methods** (GET, HEAD, OPTIONS, TRACE): Read-only. Do not alter server state.
  - **Idempotent methods** (GET, HEAD, PUT, DELETE): Same result whether called once or N times.
  - **POST**: Neither safe nor idempotent. This is why pipelining POST is dangerous and why browsers warn on re-submit.
- **URI Encoding**: Characters outside the restricted set are percent-encoded (`%20` for space, `%2F` for `/`). Your parser must decode URIs to serve the correct resource. Beware double-encoding attacks and directory traversal via `%2F`.
- **Persistent Connections**: HTTP/1.0 closed after each response. HTTP/1.1 defaults to `keep-alive` -- multiple requests share one TCP connection. This creates the message framing challenge: where does one request end and the next begin?

### Key Concept: The Evolution of HTTP

| Version | Year | Key Innovation | Wire Format |
|---|---|---|---|
| HTTP/0.9 | 1991 | Single-line request, HTML-only response | `GET /page\r\n` |
| HTTP/1.0 | 1996 | Headers, methods, status codes, MIME types | Text, connection-per-request |
| HTTP/1.1 | 1997 | Persistent connections, chunked encoding, Host header | Text, keep-alive default |
| HTTP/2 | 2015 | Binary framing, multiplexing, header compression | Binary frames over TLS |
| HTTP/3 | 2022 | QUIC (UDP), eliminates TCP head-of-line blocking | Binary frames over QUIC |

> **Reading**: RFC 9112, Section 2 (Message) and Section 3 (Request Line). Fielding's dissertation Chapter 5 (REST). Berners-Lee's 1989 proposal for historical context.

### Exercises
1. **E2.1**: Use `curl -v` against any public site. Save the raw request using `--trace-ascii /dev/stdout`. Identify every `\r\n`. Count them.
2. **E2.2**: Manually type an HTTP request into `nc`:
   ```
   GET / HTTP/1.1\r\n
   Host: localhost\r\n
   \r\n
   ```
   Observe the server response (or lack thereof if you miss the empty line).
3. **E2.3**: Read RFC 9112 Section 3 (Request Line). Write a one-page summary in your own words.
4. **E2.4** (new): Send `GET /hello%20world HTTP/1.1` via `nc`. Then send `GET /hello world HTTP/1.1`. What should the server do with each? What does the RFC say about spaces in URIs?
5. **E2.5** (new): Read Fielding's REST dissertation, Chapter 5. In 200 words, explain how the constraints of REST map to what you've seen in HTTP so far.

### Dig Deeper (Module 2)
- RFC 9112, Sections 2-3 (Message, Request Line) at [httpwg.org](https://httpwg.org/specs/rfc9112.html)
- RFC 9110, Sections 8-9 (Representations, Methods) at [httpwg.org](https://httpwg.org/specs/rfc9110.html)
- [How to Read an RFC](https://www.mnot.net/blog/2018/07/31/read_rfc) (Mark Nottingham) -- by the former HTTP WG chair
- [HTTP/1.0 From Scratch](https://kmcd.dev/posts/http1.0-from-scratch/) (Kevin McDonald) -- warm-up before HTTP/1.1
- [HTTP Made Really Easy](https://www.jmarshall.com/easy/http/) (James Marshall) -- 15-page practical wire format guide
- [HTTP/1.1 Explained](https://http.dev/1.1) -- accessible companion to the RFC
- Fielding's REST dissertation, Chapter 5 -- design rationale behind HTTP semantics
- Berners-Lee, "Information Management: A Proposal" (1989) -- how it all started
- [HTTP Keep-Alive, Pipelining, Multiplexing & Connection Pooling](https://www.haproxy.com/blog/http-keep-alive-pipelining-multiplexing-and-connection-pooling) (HAProxy) -- persistent connections explained by practitioners

### Milestone Checkpoint
> **M2**: You can draw an HTTP request on paper, labeling every CRLF, and explain why the empty line is the most important byte sequence in HTTP/1.1. You can explain the difference between safe and idempotent methods with examples.

---

## Module 3: The Request Parser (Test-Driven Protocol Implementation)
**Goal**: Convert raw TCP bytes into a structured `HttpRequest` Java object using TDD.

### Concepts to Master
- **Testing Philosophy** (ThePrimeagen's approach):
  - Tests should be **declarative**, not clever. Avoid table-driven tests while learning.
  - Write tests for things you *cannot* get right first try. Text parsing always deserves tests.
  - Use JUnit 5 with explicit, named test methods: `void shouldParseSimpleGetRequestLine()`.
- **Parsing the Request-Line**:
  - `METHOD`: Token parsing (uppercase letters, but be tolerant).
  - `REQUEST-URI`: Path parsing. Absolute vs origin-form. Percent-decoding.
  - `HTTP-VERSION`: `HTTP/1.1` -> `HTTP` + `/` + `1` + `.` + `1`.
- **Parsing Headers**:
  - Multi-value headers: `Accept: text/html, application/json`.
  - Multiple same-name headers: must be preserved as a list, not overwritten.
  - Header name normalization: case-insensitive storage (lowercase keys).
- **Body Awareness**:
  - When to read the body: only after headers are fully parsed.
  - How many bytes: `Content-Length` header value.
  - What if missing and not chunked? In HTTP/1.1, assume no body.

### Key Concept: The Parser State Machine

Draw this state machine before writing a single line of code:

```
START --> READING_METHOD --> (SP) --> READING_URI --> (SP) --> READING_VERSION --> (CRLF)
  --> READING_HEADER_NAME --> (:) --> READING_HEADER_VALUE --> (CRLF)
       ^                                                        |
       |____________________ (CRLF, non-empty) _________________|
                             (CRLF, empty line)
                                    |
                                    v
                             READING_BODY --> DONE
```

Each state reads bytes until a transition condition is met. Invalid bytes in any state trigger `ProtocolException` (400 Bad Request).

> **Reading**: Harold, *Java Network Programming*, Chapters 9-10 (ServerSocket, Socket). For TDD methodology: Kent Beck, *Test-Driven Development: By Example* (2002).

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
1. **E3.1**: Write `RequestLineParserTest`. Start with the simplest possible GET request. Red -> Green -> Refactor.
2. **E3.2**: Add a test for malformed request line (e.g., missing HTTP version). Your parser should throw a `ProtocolException`.
3. **E3.3**: Integrate with your TCP server from Module 1. When a client connects, parse the first line. Print the method and URI to stdout.
4. **E3.4** (new): Write tests for header parsing edge cases: duplicate header names, empty header values, very long header lines (what's the limit?), and the `Host` header (required in HTTP/1.1).
5. **E3.5** (new): Write a fuzz test: generate random byte sequences and feed them to your parser. It must never crash with an unhandled exception -- only throw `ProtocolException` for invalid input.

### Test Validation
Run `./gradlew testHttp` to validate your implementation.
- **HttpRequestTest**: Immutability, defensive copies, null rejection
- **RequestLineParserCorrectnessTest**: Method/URI/version extraction, headers, duplicate headers, Content-Length body
- **RequestLineParserMalformedTest**: ProtocolException for 12 categories of invalid input
- **HeaderParserEdgeCaseTest**: Colons in values, empty values, Host header requirement
- **BodyParserTest**: Exact Content-Length body reading, chunked delivery handling
- **RequestParserEnforcementTest**: No single-byte read(), handles TCP fragmentation
- **RequestParserFuzzTest**: 1000 random inputs, truncation, null bytes -- never crashes

### Dig Deeper (Module 3)
- Harold, *Java Network Programming*, Chapters 9-10 (ServerSocket, Socket)
- [HTTP Desync Attacks: Request Smuggling Reborn](https://portswigger.net/research/http-desync-attacks-request-smuggling-reborn) (James Kettle) -- every edge case where HTTP parsers disagree; the adversarial spec your parser must survive
- [picohttpparser source](https://github.com/h2o/picohttpparser) -- production C parser with state machine and SIMD; study the byte-level parsing approach
- [llhttp source](https://github.com/nodejs/llhttp) -- Node.js HTTP parser generated from a formal state machine description
- [NanoHTTPD source](https://github.com/NanoHttpd/nanohttpd) -- single-file Java HTTP server (~1500 lines); compare your parser to theirs
- [Message Framing](https://blog.stephencleary.com/2009/04/message-framing.html) (Stephen Cleary) -- delimiter vs length-prefix strategies
- [Connection Management in HTTP/1.x](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Connection_management_in_HTTP_1.x) (MDN) -- persistent connections with diagrams

### Milestone Checkpoint
> **M3**: Your parser passes explicit JUnit tests for: (a) simple GET, (b) POST with headers, (c) malformed request line rejection, (d) duplicate headers preserved as a list.

---

## Module 4: Server Architecture & Concurrency
**Goal**: Handle multiple simultaneous TCP connections and route HTTP requests to handlers.

### Concepts to Master
- **The I/O Model Progression** (historical context):
  1. **Single-threaded**: Handle one connection at a time. All others wait. Useless for production.
  2. **Thread-per-connection**: `new Thread(() -> handle(socket)).start()`. Simple but heavy (~1MB per thread).
  3. **Thread pools**: `ExecutorService` bounds resource usage. But still limited by platform thread count.
  4. **Non-blocking I/O**: `Selector` + `ServerSocketChannel`. One thread handles thousands of connections via event loop. Complex to program correctly.
  5. **Virtual threads (Java 21+)**: `Thread.startVirtualThread(() -> handle(socket))`. Lightweight, managed by JVM. Millions of concurrent tasks. This is Java's answer to Go goroutines.
- **Connection State**:
  - HTTP/1.1 persistent connections (`Connection: keep-alive` is default).
  - Detecting connection close: `read()` returns `-1`.
  - **Nagle's Algorithm**: Small writes may be coalesced, adding latency. Consider `Socket.setTcpNoDelay(true)` for latency-sensitive responses.
- **Routing**: A `Map<String, HttpHandler>` or pattern-based routing.

### Key Concept: Why Virtual Threads Exist

| Model | Threads for 10K connections | Memory overhead | Code complexity |
|---|---|---|---|
| Thread-per-connection (platform) | 10,000 platform threads | ~10 GB | Low |
| Thread pool (platform) | ~200 threads | ~200 MB | Medium (queueing, backpressure) |
| NIO Selector | 1 thread | ~10 MB | Very High (callback hell) |
| Virtual threads | 10,000 virtual threads | ~10 MB | Low (blocking style) |

Virtual threads give you the simplicity of blocking I/O with the scalability of NIO. This is why Go was popular for network servers -- goroutines had this property from day one. Java caught up with Project Loom.

> **Reading**: Goetz, *Java Concurrency in Practice*, Chapters 6-8 (Task Execution, Cancellation, Thread Pools). JEP 444 (Virtual Threads). The C10K Problem essay.

### Handler Interface
```java
public interface HttpHandler {
    void handle(HttpRequest request, OutputStream responseBody) throws IOException;
}
```

### Exercises
1. **E4.1**: Refactor your server to handle each connection in a virtual thread. Load-test with `wrk` or `oha`. Observe memory usage.
2. **E4.2**: Implement a router. `GET /` -> "Hello World". `GET /health` -> `{"status":"up"}`. `POST /echo` -> echoes body.
3. **E4.3**: Add `Connection: close` support. After serving one request, close the socket. Then implement keep-alive (serve multiple requests on one connection until client closes).
4. **E4.4** (new): Open 100 concurrent connections using a script or `wrk`. Monitor with `jcmd <pid> Thread.dump_to_file threads.txt` and observe the virtual threads. Compare memory usage vs the same test with platform threads.
5. **E4.5** (new): Implement a graceful shutdown: when the server receives SIGINT, it stops accepting new connections, finishes in-flight requests, then exits. Use `Runtime.addShutdownHook()`.

### Test Validation
Run `./gradlew testServer` to validate your implementation.
- **HttpServerBasicTest**: Accepts connections, routes requests, returns 200/404/400
- **RouterTest**: Exact match, method-aware dispatch, multiple routes
- **HttpServerConcurrencyTest**: 100 concurrent connections, slow handler isolation
- **HttpServerKeepAliveTest**: Persistent connections, Connection: close support
- **HttpServerGracefulShutdownTest**: Clean shutdown behavior
- **HttpServerEnforcementTest**: Virtual threads, socket cleanup, fragmented requests

### Dig Deeper (Module 4)
- Goetz, *Java Concurrency in Practice*, Chapters 6-8 (Task Execution, Cancellation, Thread Pools)
- [The C10K Problem](https://www.kegel.com/c10k.html) (Dan Kegel) -- the original essay
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) -- the specification itself, surprisingly readable
- [Virtual Threads Deep Dive](https://www.happycoders.eu/java/virtual-threads/) (HappyCoders) -- mounting/unmounting, carrier threads, why pooling is an anti-pattern
- [Java 21 Virtual Threads -- Dude, Where's My Lock?](https://netflixtechblog.com/java-21-virtual-threads-dude-wheres-my-lock-3052540e231d) (Netflix) -- production deadlock from pinned carrier threads
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491) -- fixes the Netflix problem
- [Beyond Loom: Weaving New Concurrency Patterns](https://developers.redhat.com/articles/2023/10/03/beyond-loom-weaving-new-concurrency-patterns) (Red Hat) -- structured concurrency for servers
- [Concurrent Servers](https://eli.thegreenplace.net/2017/concurrent-servers-part-1-introduction/) (Eli Bendersky, 6-part series) -- from sequential to event-driven servers with state machine parsers
- [Deep Dive into Java IO Models: BIO, NIO, AIO](https://gfish.online/en/blog/java-bio-nio-aio-io-models/) -- BIO vs NIO vs AIO with the restaurant analogy
- [Asynchronous Non-blocking I/O Under the Hood](https://tuhuynh.com/en/posts/nio-under-the-hood/) (Tu Huynh) -- poll, select, epoll, kqueue beneath Java NIO

### Milestone Checkpoint
> **M4**: Your server serves a simple HTML page to a real browser (Chrome/Firefox) using `ServerSocket` and virtual threads. It handles 100+ concurrent connections without crashing.

---

## Module 5: HTTP Responses & Advanced Messaging
**Goal**: Generate valid HTTP responses and serve non-text content over raw TCP.

### Concepts to Master
- **Status-Line**: `HTTP/1.1 200 OK`. Parsing and generating.
- **Response Headers**:
  - `Content-Type`: MIME types. `text/html`, `application/json`, `video/mp4`.
  - `Content-Length`: Mandatory for responses with body (unless chunked).
  - `Connection`: `keep-alive` vs `close`.
- **Content Negotiation**: The `Accept` header in requests declares what the client wants. `Content-Type` in responses declares what you're sending. Quality factors (`q=0.9`) express preference ordering.
- **Serving Static Files**:
  - Mapping URI path to filesystem path securely.
  - **Security**: Prevent directory traversal. `GET /../../../etc/passwd` must fail. Canonicalize paths and verify they stay within the document root.
  - Reading files and streaming directly to `Socket.getOutputStream()`.
- **Chunked Transfer Encoding**:
  - Why: streaming data when total size is unknown.
  - Format: `size_in_hex\r\n` + `data` + `\r\n`. Final chunk: `0\r\n\r\n`.
- **Binary Data & MP4 Serving**: HTTP does not care about content. Bytes are bytes. Set the `Content-Type` header and stream.
- **Nagle's Algorithm in Practice**: When sending small response headers followed by a large body in separate `write()` calls, Nagle may introduce latency. Consider `setTcpNoDelay(true)` or buffering the entire response before flushing.

> **Reading**: RFC 9110, Sections 8 (Representations), 12 (Content Negotiation), 15 (Status Codes). Grigorik, *High Performance Browser Networking*, Chapters 1-2 (latency, TCP optimization).

### Exercises
1. **E5.1**: Serve a directory of files. Implement safe path resolution with canonicalization.
2. **E5.2**: Implement chunked encoding. Create an endpoint that generates infinite `Hello\n` chunks every second until client disconnects.
3. **E5.3**: Serve a 50MB MP4 file. Open it in a browser `<video>` tag. Verify the browser sends a `Range` request (optional bonus: handle `206 Partial Content`).
4. **E5.4** (new): Implement basic content negotiation: if the client sends `Accept: application/json`, respond with JSON. If `Accept: text/html`, respond with HTML. Test with `curl -H "Accept: application/json"`.
5. **E5.5** (new): Enable and disable `TCP_NODELAY` on your server socket. Measure response latency for small responses using `curl -w "%{time_total}"`. Observe the difference.

### Test Validation
Run `./gradlew testResponse` to validate your implementation.
- **HttpResponseBuilderTest**: Status line format, headers, Content-Length accuracy, binary body
- **ChunkedEncoderCorrectnessTest**: Chunked format per RFC 9112 Section 7.1
- **ChunkedEncoderEdgeCaseTest**: Single byte, CRLF in data, empty chunk
- **StaticFileServerSecurityTest**: Directory traversal prevention (6 attack vectors)
- **ContentNegotiationTest**: Accept header parsing, quality factors, 406 responses

### Dig Deeper (Module 5)
- RFC 9110, Sections 8, 12, 15 (Representations, Content Negotiation, Status Codes)
- RFC 9112, Section 7.1 (Chunked Transfer Coding)
- Grigorik, *High Performance Browser Networking*, Chapters 1-2 (latency, TCP optimization) -- free at [hpbn.co](https://hpbn.co/)
- [It's Always TCP_NODELAY](https://brooker.co.za/blog/2024/05/09/nagle.html) (Marc Brooker, AWS) -- why Nagle's algorithm hurts your server
- [Why You Should Understand (a Little) About TCP](https://jvns.ca/blog/2015/11/21/why-you-should-understand-a-little-about-tcp/) (Julia Evans) -- the delayed ACK + Nagle 40ms latency trap
- [Optimizing TCP for High WAN Throughput](https://blog.cloudflare.com/optimizing-tcp-for-high-throughput-and-low-latency/) (Cloudflare) -- kernel-level tuning for large file transfers
- [Efficient Java I/O: byte[], ByteBuffers, and OutputStreams](https://www.evanjones.ca/software/java-bytebuffers.html) (Evan Jones) -- NIO write path secrets
- [HTTP Desync Attacks](https://portswigger.net/research/http-desync-attacks-request-smuggling-reborn) (James Kettle) -- why strict Content-Length and chunked parsing matters for security
- [HTTP persistent connections, pipelining and chunked encoding](https://sharovatov.github.io/posts/2009-11-http-pipeline.html) (Vitaly Sharovatov) -- why chunked encoding exists

### Milestone Checkpoint
> **M5**: A browser can play an MP4 video served by your Java server over a raw TCP socket with no framework. Your server correctly handles `Content-Type` and `Content-Length`.

---

## Module 6: Testing, Debugging, and Protocol Compliance
**Goal**: Harden your server against real-world HTTP clients.

### Concepts to Master
- **Integration Testing with Java's `HttpClient`** (Java 11+):
  - Write tests that start your server on a random port, send a request, and assert on the response.
- **Wireshark/tcpdump**:
  - Filter: `tcp.port == 42069`.
  - Inspect TCP segments: SYN, ACK, PSH, FIN.
  - Inspect HTTP layer: confirm your bytes are exactly right.
- **Common Bugs**:
  - Off-by-one `Content-Length`.
  - Missing `\r\n` after headers -- browser hangs forever waiting for body start.
  - Blocking on body read when client sent no body.
  - Not handling `-1` from `read()` -- infinite loops.
  - Not handling `Connection: close` -- socket leaks.
- **Fuzzing**: Send garbage bytes to your server. Does it crash or gracefully return `400 Bad Request`?
- **Load Testing**: Use `wrk -t12 -c400 -d30s` or `oha`. Profile with `async-profiler` or `jcmd`. Is the bottleneck parsing, I/O, or thread scheduling?

> **Reading**: Stevens, *TCP/IP Illustrated Vol. 1*, Chapter 18 (TCP connection management with tcpdump). For fuzzing methodology: OWASP Testing Guide, Section on HTTP Protocol Testing.

### Capstone Project
Build a server that:
1. Passes a custom RFC-compliance test suite (provided by your tutor agent).
2. Serves a single-page website with HTML, CSS, and an MP4 video.
3. Handles at least 100 concurrent connections without crashing.
4. Logs every request line to stdout.
5. Returns `400 Bad Request` for any malformed input without crashing.

### Test Validation
Run `./gradlew test` to validate ALL modules, including capstone-specific tests:
- **RfcComplianceTest**: Status line format, Content-Length accuracy, blank line, missing Host -> 400, unknown method, URI/header size limits
- **BrowserCompatibilityTest**: Java HttpClient GET/POST/keep-alive (real HTTP client)
- **LoadValidationTest**: 100 concurrent clients, 500 sequential connections with no socket leaks

### Milestone Checkpoint
> **M6**: Your server is demonstrably compliant, concurrent, and capable of serving rich media.

---

## Module 7: The OSI Model in Practice -- Closing the Loop
**Goal**: Map your daily software engineering work back to the layers you built.

### Concepts to Master
- **Layer 4 (Transport)**: You built this. TCP segments, ports, flow control, kernel socket buffers.
- **Layer 5 (Session)**: HTTP is stateless, so cookies/JWT are a poor man's session layer. TLS handshakes live here too.
- **Layer 6 (Presentation)**: JSON, Protobuf, encryption. HTTP doesn't care; it carries bytes.
- **Layer 7 (Application)**: HTTP methods, status codes, REST semantics. The layer you *thought* you knew.
- **The Full Path of an HTTP POST**:
  - Browser -> OS socket buffer (`sendbuf`) -> TCP segmentation -> IP routing -> NIC driver -> Ethernet -> Wire -> ...reverse on server side... -> `Socket.getInputStream().read()` -> your parser -> your handler -> response -> reverse path back.
  - Where does the JVM end and the kernel begin?
- **The End-to-End Argument**: Functions should be implemented at endpoints, not in the network. TCP does retransmission at endpoints. HTTP does authentication at endpoints. This is the most influential network design principle (Saltzer, Reed, Clark, 1984).

> **Reading**: Clark, "The Design Philosophy of the DARPA Internet Protocols" (1988). Saltzer, Reed & Clark, "End-to-End Arguments in System Design" (1984). Peterson & Davie, *Computer Networks: A Systems Approach*, Chapter 1 (free at [book.systemsapproach.org](https://book.systemsapproach.org/)).

### Reflection Essay
Write 500 words on: *"Every time I call `restTemplate.getForObject()` at work, these are the layers I am implicitly trusting. Now that I have built them, which abstractions do I respect more, and which do I distrust?"*

### Dig Deeper (Module 7)
- Clark, "The Design Philosophy of the DARPA Internet Protocols" (1988) -- the priority ordering of goals
- Saltzer, Reed & Clark, "End-to-End Arguments in System Design" (1984) -- the most influential network design principle
- Fielding's REST dissertation, Chapter 5 -- HTTP's architectural constraints and why they exist
- Peterson & Davie, *Computer Networks: A Systems Approach*, Chapter 1 -- free at [book.systemsapproach.org](https://book.systemsapproach.org/)
- [Why Do We Use the Linux Kernel's TCP Stack?](https://jvns.ca/blog/2016/06/30/why-do-we-use-the-linux-kernels-tcp-stack/) (Julia Evans) -- what sits beneath your Java Socket
- [The Road to QUIC](https://blog.cloudflare.com/the-road-to-quic/) (Cloudflare) -- why HTTP/3 abandons TCP entirely
- [HTTP/3 Explained](https://http3-explained.haxx.se/en) (Daniel Stenberg) -- QUIC and the future of HTTP transport

### Milestone Checkpoint
> **M7**: You have written the reflection and can whiteboard the full path of an HTTP POST from browser -> your Java server -> response, labeling OSI layers and Java classes at each step.

---

## Creative Extensions (Beyond the Video)
1. **The NIO Refactor**: Rebuild with `ServerSocketChannel`, `Selector`, and non-blocking I/O. Understand why Netty exists. Reference: Ron Hitchens, *Java NIO* (O'Reilly, 2002). Also: Norman Maurer, *Netty in Action* (Manning, 2015).
2. **HTTP/2 Frame Parser**: Read RFC 9113. Implement a toy HPACK decoder. Appreciate why HTTP/1.1's text format is beautiful for learning. Reference: Stenberg, *http2 explained*.
3. **Load Balancer Simulation**: Write a TCP proxy that accepts connections and forwards to your server. Inspect how `X-Forwarded-For` is a Layer 7 hack for Layer 3 information.
4. **Benchmark & Optimize**: Use `wrk -t12 -c400 -d30s`. Profile with `async-profiler`. Is the bottleneck parsing, I/O, or thread scheduling? Reference: Oaks, *Java Performance: The Definitive Guide*, 2nd Ed.
5. **Protohackers Challenges**: Complete challenges 0-3 at [protohackers.com](https://protohackers.com/) for additional protocol implementation practice with automated verification.
6. **TLS from Scratch**: Implement a minimal TLS 1.3 handshake (or use `SSLServerSocket`) to serve HTTPS. Reference: RFC 8446.

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

## Suggested Reading Order

For optimal learning alongside this course (see each module's **Dig Deeper** section for the full list):

1. **Before Module 0**: Kurose & Ross, Chapter 2 (Application Layer). [How to Read an RFC](https://www.mnot.net/blog/2018/07/31/read_rfc) (Nottingham).
2. **Module 0**: Java SE `InputStream` contract docs. [Efficient Java I/O](https://www.evanjones.ca/software/java-bytebuffers.html) (Evan Jones).
3. **Module 1**: Stevens, *TCP/IP Illustrated Vol. 1*, Ch. 12-14. [Why You Should Understand TCP](https://jvns.ca/blog/2015/11/21/why-you-should-understand-a-little-about-tcp/) (Julia Evans). Beej's Guide Ch. 2-5.
4. **Module 2**: RFC 9112 Sections 2-3 at httpwg.org. [HTTP Made Really Easy](https://www.jmarshall.com/easy/http/). Berners-Lee (1989).
5. **Module 3**: Harold, *Java Network Programming*, Ch. 9-10. [HTTP Desync Attacks](https://portswigger.net/research/http-desync-attacks-request-smuggling-reborn) (Kettle). Study [NanoHTTPD source](https://github.com/NanoHttpd/nanohttpd).
6. **Module 4**: Goetz, *Java Concurrency in Practice*, Ch. 6-8. [JEP 444](https://openjdk.org/jeps/444). [Netflix virtual threads war story](https://netflixtechblog.com/java-21-virtual-threads-dude-wheres-my-lock-3052540e231d). [Concurrent Servers series](https://eli.thegreenplace.net/2017/concurrent-servers-part-1-introduction/) (Bendersky).
7. **Module 5**: Grigorik, *HPBN*, Ch. 1-2 (free at hpbn.co). [TCP_NODELAY](https://brooker.co.za/blog/2024/05/09/nagle.html) (Brooker).
8. **Module 7**: Clark (1988) + Saltzer (1984) papers. Fielding dissertation Ch. 5.

---

## Resources Quick Reference
- **RFCs**: [RFC 9110](https://httpwg.org/specs/rfc9110.html) (Semantics), [RFC 9112](https://httpwg.org/specs/rfc9112.html) (HTTP/1.1), [RFC 9293](https://www.rfc-editor.org/rfc/rfc9293) (TCP)
- **How to Read RFCs**: [mnot.net/blog](https://www.mnot.net/blog/2018/07/31/read_rfc) (Mark Nottingham, former HTTP WG chair)
- **Free Books**: [hpbn.co](https://hpbn.co/), [beej.us/guide/bgnet](https://beej.us/guide/bgnet/), [book.systemsapproach.org](https://book.systemsapproach.org/), [http3-explained.haxx.se](https://http3-explained.haxx.se/en)
- **Free Zines**: [Networking! ACK!](https://jvns.ca/networking-zine.pdf) (Julia Evans)
- **Java Virtual Threads**: [JEP 444](https://openjdk.org/jeps/444), [JEP 491](https://openjdk.org/jeps/491), [HappyCoders deep dive](https://www.happycoders.eu/java/virtual-threads/)
- **Source Code to Study**: [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), [picohttpparser](https://github.com/h2o/picohttpparser), [llhttp](https://github.com/nodejs/llhttp), [smoltcp](https://github.com/smoltcp-rs/smoltcp)
- **Interactive**: [Low-Level Academy](https://lowlvl.org/), [Protohackers](https://protohackers.com/), [CodeCrafters](https://app.codecrafters.io/courses/http-server/overview), [Coding Challenges](https://codingchallenges.fyi/challenges/challenge-webserver/)
- **Tools**: `curl`, `ncat`, Wireshark, `tcpdump`, `wrk`, `oha`, `hexdump -C`, `async-profiler`

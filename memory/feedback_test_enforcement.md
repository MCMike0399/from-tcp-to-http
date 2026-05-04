---
name: Test enforcement pattern for learning
description: User discovered that correctness-only tests are insufficient for learning — enforcement tests that catch naive implementations are essential
type: feedback
---

When building educational test suites, correctness tests alone are insufficient. The user's byte-by-byte LineReader implementation passed all basic tests but would fail on real TCP streams.

**Why:** A naive implementation can produce correct output via an incorrect approach. On localhost, `read()` one byte at a time works because the loopback is fast. Over a real network, it breaks. Tests that only check output give false confidence.

**How to apply:** Always include enforcement tests alongside correctness tests:
- `ChunkedOnlyInputStream` — throws on `read()`, forces `read(byte[])` with chunk-based reading
- `PartialInputStream` — random-sized chunks to simulate TCP fragmentation
- Reflection checks — forbid wrapper classes like BufferedReader/Scanner
- Timing gates — catch O(n^2) string concatenation via performance assertions
- Resource leak detection — rapid connect/disconnect cycles
- Thread count checks — verify virtual threads, not platform threads

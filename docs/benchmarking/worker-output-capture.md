# Complete-stream identity with bounded worker previews

## User output and callback compatibility

The worker retains at most 8,000 bytes per stdout/stderr preview. Every byte nevertheless contributes to a separate running SHA-256 per stream, even with a zero preview limit. Measured iterations compare both complete-stream digests, rather than truncated previews. Non-deterministic output after the retained prefix is therefore rejected without retaining unbounded data.

`artifactChecksum` remains the existing 64-character SHA-256 string field, but its semantics are now versioned complete-stream identity. It is SHA-256 of the UTF-8 text `benchlab-output-v1\nstdout-sha256=<64 lowercase hex>\nstderr-sha256=<64 lowercase hex>\n`. This explicit domain and separate, fixed-width digests distinguish stdout=ab/stderr=c from stdout=a/stderr=bc. For measured results it describes the final attempted iteration, including an iteration that failed or differed. Compile/warmup failures use the corresponding complete process streams. Earlier stored checksums were hashes of concatenated retained previews and must not be compared across these semantics as if identical. No public DTO, callback route or new field is introduced.

`outputSizeBytes` counts observed user-stream bytes across attempted measured iterations; a valid helper frame no longer inflates the count. Compile/warmup failures retain the existing single-attempt accounting. Truncation flags describe user previews alone. Equality assumes SHA-256 collision resistance, not a byte-by-byte retained proof. Existing journals replay already persisted results/checksums unchanged.

## Helper framing independent from stderr preview

Each measured invocation receives a newly generated 128-bit lowercase-hex token as a private helper argument. The bundled helper emits a final frame after the child exits:

```text
\n__BENCHLAB_METRICS_V1__ <32-hex-token> <wall-microseconds> <cpu-microseconds>\n
```

The worker streams stderr through a bounded 256-byte tail buffer, forwarding older bytes into the normal preview/count/digest. At process completion only the final complete line, with the exact expected token, one wall and one CPU field, is eligible as metadata. The separator newline is helper-owned; existing user newlines remain intact. Wall must be a nonnegative int64; CPU accepts nonnegative int64 or exactly -1 (unavailable). Digits are decimal only, with no signs/fractions/whitespace extras. Conversion to ceiling milliseconds avoids integer addition overflow. Zero wall duration is reported as at least one millisecond, retaining the existing behavior.

Earlier frame-like text, legacy marker names, duplicated frame pairs, and marker-like user tails remain user bytes if followed by a genuine final frame. In a duplicated valid-pair suffix only the final pair is stripped; prior pairs are user data, not a second measurement. Chunk boundaries do not matter. An absent, wrong-token, malformed, incomplete or non-final frame is not stripped or used as a fallback; the received bytes remain opaque stderr and a successful process becomes the existing missing-measurement runtime failure. A failed process retains its original error/exit/timeout classification. Do not interpret a failed record's opaque stderr as authenticated user-only content.

The random token and final framing distinguish invocations and accidental marker-like output. They are NOT cryptographic authentication against hostile code that can inspect processes, nor proof of trustworthy timings from a compromised container/helper. The protocol relies on the bundled helper emitting the final frame after the child and on existing process isolation. It does not accept a generic LastIndex match from any arbitrary user line. The internal helper invocation changes atomically with the worker binary copied into each runner workspace; old standalone `--measure-execution <command>` invocation is not a supported public interface. Current invocation is `--measure-execution <token> <command> [args...]`.

## Bounds and verification

Retained data is O(preview limit) per stream plus a 256-byte deferred tail and SHA-256 state; arbitrarily large Write chunks are not copied into that tail. Full output is still read/hashed, so this change does not bound total emitted bytes, CPU or I/O. Existing Docker timeouts, named cleanup, memory/process limits and error classification remain. No new privileges, dependencies, services, settings or storage fields.

Tests exercise executeRun with a compiled Docker substitute, tail differences, long stderr, variable timings, marker-like user content, malformed/absent footer, original nonzero exit status, timeout and same-container cleanup. Unit cases include one-byte/chunked writes, large chunks, zero preview capacity, bounded tail storage, double-pair semantics and domain-separated stream digests. The real measurement helper and real child test processes verify long stderr and exit 7 without Docker. These are local Windows process tests, not Linux/Docker/RabbitMQ integration. Go suite/vet and Linux cross-compilation are separate evidence; the existing full integration gate stays pending.

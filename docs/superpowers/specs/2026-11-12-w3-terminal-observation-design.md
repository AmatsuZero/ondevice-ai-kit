# W3 future terminal observation handshake

The consumed physical heap run remains rejected (19/20). Its frozen sources,
APKs, reports and non-null terminalObservationGap are immutable. The observed
host race is concrete: after a running report, instrumentation finishes before
the next /proc/PID/status read. Reading a later completed report cannot recover
the missing resource observation.

This change prepares a future independent probe. It does not authorize replay,
new microphone/AiAi work, model downloads, production edits or a relaxed gate.
The user requested autonomous W3 improvements; the exact handshake is the
primary agent's engineering choice, not a user-selected product budget.

## Approach

Reading terminal reports earlier only narrows the race. Accepting ENOENT after
exit would discard the required evidence. Instead, hold instrumentation alive
for a bounded acknowledgement after all existing release/teardown operations
and immutable terminal-report persistence have completed.

The first deliverable is a pure Kotlin handshake component and host Python
validator. Both must be usable from a separately named future wrapper/driver;
neither imports, rewrites or executes the consumed frozen driver. Integration,
Android build/package review and a new frozen device contract follow separately.

## Contract

- Device binding: runId matches `[A-Za-z0-9-]{1,80}`, source fingerprint and
  terminal report SHA256 match `[a-f0-9]{64}`, PID is a positive integer,
  startTicks is canonical positive decimal fitting signed 64-bit.
- ACK payload is exact UTF-8 ASCII (at most 1024 bytes), with six newline-terminated lines:
  `ODAI-TERMINAL-ACK-v1`, runId, sourceFingerprint, decimal PID, startTicks,
  terminalReportSHA256. No JSON conversion or newline normalization.
- Wait budget: monotonic milliseconds, 1..30000 inclusive; poll at most every
  50 ms and never sleep past the remaining budget. A callback must bound each
  filesystem/transport read; callback execution is not preemptible by this pure
  component. The future wrapper supplies local bounded reads, not ADB calls.
- Before polling/after every callback, reject negative or regressing clocks,
  elapsed budget exhaustion, invalid/oversize/mismatching ACK, or callback errors.
  ACK at exactly the deadline is too late. All failures remain typed failures;
  no retries, success defaults, or swallowing interruptions.
- Host ACK requires two identical nonempty raw terminal-report reads; matching
  runId, sourceFingerprint and positive integer PID; literal terminal=true;
  a completed or failed status; a final resource sample with the same positive
  PID and startTicks before and after the two report reads; positive integer
  RSS and available bytes within existing 1.5 GiB / 512 MiB inclusive limits.
  The final sample's monotonic uptime interval must contain both report reads.
  Validation of schema/counters/releases remains the future evaluator's job;
  ACK acknowledges observation, never quality or successful inference.
- Host observations must be strictly typed (bool is not int), bounded JSON
  bytes (<=1 MiB), finite nonnegative monotonic nanoseconds fitting signed
  64-bit, and represent an actual sampled interval, not inferred continuity.
- A future host writes its final observed bytes/sample durably before publishing
  ACK, continues enforcing stops until then, then waits for bounded process exit
  without another /proc read. Early process exit or any pre-ACK observation
  failure remains a gap. Failure results do not clear a previous gap.
- Future filesystem integration must reject stale ACK before any native work,
  symlinks/path escapes and oversized/growing reads. ACK publication must be
  exclusive and bound to the exact run. These IO checks are outside the pure
  components and cannot be claimed from pure host tests.

## Local file integration boundary

The first IO step exposes only `read_stable(root, relative, max_bytes)` and
`write_new_durable(root, relative, payload)`. It does not hide fresh report reads
behind observation timestamps collected by its caller. The future coordinator
must collect and record its actual before/read1/read2/after events, validate them,
and durably save the report and observation receipt before publishing an ACK.

Reads use a canonical private root, no-follow directory traversal, a bounded
max+1 read, before/after FD metadata and final path identity checks. Writes stage
complete bytes in the same directory, fsync the file, publish atomically without
overwrite, and fsync the directory. The final target remains absent while data
is being written. Error cleanup checks ownership and preserves existing or
observably replaced entries.

This local primitive assumes a private single-writer directory. Inode checks do
not provide atomic compare-and-unlink against malicious same-UID writers; the
Python standard library offers no such operation. These tests therefore prove
the observed fixture races and failure paths, not that adversarial guarantee or
the separate Android filesystem/transport boundary.

If directory fsync or staging cleanup fails after publication, propagate the
filesystem error as `PublishedButNotDurableError` (an `OSError` subclass), with
`published=True`, `durability_verified=False` and the original cause retained.
Set `staging_may_remain=True` for a staging-unlink failure. The complete final file may already be
visible and its directory durability is unverified. The future coordinator must
stop, preserve same-operation evidence and never automatically retry, clear an
observation gap, or equate file visibility with durable publication.

## Local coordinator before device transport

The next local coordinator takes source-report and three distinct new output
paths, binding, a process-sample callback and one monotonic-nanosecond callback.
Its order is clock/sample-before/read1/clock/read2/clock/sample-after/clock,
followed by validation and durable report/receipt/ACK writes in that order.
The four times share the host monotonic clock domain; they are not device
`/proc/uptime` or a proven cross-clock mapping.

Each sample independently supplies exact PID/startTicks/RSS/available values and
must pass identity/type/resource gates. The observation uses the greater RSS
and lower available value across the two samples, without implying a peak.
The deterministic UTF-8 JSON receipt preserves the original binding, report
SHA256, observation and both sample snapshots. Later mutation of callback-owned
dicts must not rewrite earlier evidence. All output paths are preflighted new;
publication failures retain prior durable outputs and are never retried.

This layer performs actual local file reads and callback invocations. Its tests
cannot prove that an injected callback samples the real device or that a local
report was freshly transported from Android. Real same-process collection,
transport bounds, Android ACK IO and the independently frozen run remain
separate integration gates.

## Actual process-sampling adapter

The next host adapter uses the selected ADB executable/serial/package and fixed
PID/startTicks. Each sample reads `/proc/PID/stat`, `/proc/PID/status`,
`/proc/meminfo`, then `/proc/PID/stat` again as four sequential `exec-out run-as`
commands. Both stat reads must match the expected identity. Parse field 22 after
the last parenthesized comm delimiter; names may contain spaces and parentheses.
Status PID must match, and RSS/MemAvailable kB values convert exactly to bytes.
Missing, duplicate, malformed or overflowing required values and transport
failures stop immediately. This adapter reports measurements; the coordinator
continues enforcing the existing resource gates.

Each remote command is one `shlex.join` string, with validated application ID,
serial and numeric PID, and `head -c 65537` to detect data over the 65536-byte
response bound. Each invocation has a timeout of at most two seconds, no retries,
no shell compound commands and no mutation. Constructor rejects invalid config
before any ADB call. Injected-runner tests prove parsing and invocation behavior;
actual ADB results remain a separate device integration check. This adapter alone
does not refresh terminal reports, publish a remote ACK or prove gap recovery.

## Exit criteria

Fault regressions prove wait-until-ACK, bounded timeout, strict identity/bytes,
callback/clock failure propagation and host observation ordering. Actual Kotlin
compilation and Python tests pass; a separate supplied-source review assesses
their scope. The old frozen hashes and old failed verdict remain unchanged.
This component milestone is not Android packaging, device acceptance, continuous
sampling, true peak, allocator root cause, W3/R4/R5 completion or release approval.

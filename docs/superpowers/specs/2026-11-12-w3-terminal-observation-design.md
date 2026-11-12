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

## Device ACK file adapter

The next device filesystem adapter is `TerminalAckFile(privateRoot: File,
runId: String)` in the existing terminal package. It exposes the generated
`file: File` (`ack-<runId>.txt`), `requireAbsent()` and `readOrNull(): ByteArray?`.
It reads raw bytes for the existing barrier to validate; it never normalizes,
creates, deletes or acknowledges files itself. `requireAbsent()` rejects both
the final path and its fixed `.stage` sibling, including dangling symlinks,
before the future wrapper begins Activity/native work. During polling, an absent
final path returns null even while the host is writing its separate stage file.

The root must already be a canonical absolute nonsymlink directory. Checks
compare the observable supplied `File` path with the canonical path;
the adapter cannot recover separators already normalized by the `File`
constructor before invocation. Dot/parent components and symlink ancestry that
remain in the supplied path are refused. Symlinks/nonregular final files are
refused, including before channel open;
reads use no-follow options, a fixed at-most-1025-byte buffer, closed channels,
and reject zero or over1024-byte finals and observed size changes. A zero-progress
read is a failure. Missing final is the only null result; other IO errors
propagate without retry or cleanup. This is an alias-private single-writer
boundary, not atomic race immunity against malicious same-UID mutation or a
preemptible filesystem syscall guarantee. Existing cached Java NIO patterns are
reused without changing the consumed allocator-policy source. JVM tests precede
implementation; Android API compilation/packaging and real IO remain later gates.

## Fresh remote report reader

The new wrapper namespace is `files/w3-qwen-terminal`; no consumed driver or
report path is reused. `AdbTerminalReportReader(adb, serial, package, run_id,
timeout_seconds=2.0, runner=None).read()` performs a fresh subprocess call on
every read and returns exact nonempty raw bytes, at most 1 MiB. Its generated
report path is `files/w3-qwen-terminal/report-<run_id>.json`. Constructor config
uses the sampler's exact-string and ASCII identity bounds; timeout is finite,
positive and at most two seconds. Invalid config fails before transport.

One safely quoted `exec-out run-as <package> sh -c <fixed script>` checks the
`files` directory, new report directory and final file for their required type
and absence of symlinks, then invokes `head -c 1048577` on that exact final.
Transport errors/timeouts, non-exact bytes and empty/overbound output propagate
or fail without retry, fallback or cached data. The reader does not parse JSON,
normalize bytes, mutate the device or clear an observation gap. The existing
validator must still compare two actual reads between same-process samples.
This private-alias script is not descriptor-level remote metadata stability or
an atomic defense against malicious same-UID path replacement. Fault doubles
and local fake subprocesses precede actual Android transport acceptance.

## Remote atomic ACK publication

The next adapter is `AdbTerminalAckPublisher(adb, serial, package, run_id,
timeout_seconds=2.0, runner=None)`, with `publish(payload, *, before_link)`.
Config uses the report reader's exact-string/ASCII/timeout bounds. Payload must
be exact bytes of length 1..1024; the existing validator supplies protocol-valid
ACK bytes. A callable `before_link` is required. Invalid arguments cause no
transport; the first actual attempt consumes this publisher instance, including
failure. No retry or cleanup occurs.

Five bounded transports use safely quoted fixed paths in `files/w3-qwen-terminal`:
write a new `ack-<run_id>.txt.stage` via exec-in with nonsymlink-parent/stale-entry
guards and `(umask 077 && set -C && cat > stage)`; read back the regular nonsymlink
stage with `head -c 1025` and compare exact bytes; `toybox fsync stage` without
`-d`; call `before_link`; `toybox ln -T stage final` without force/symlink flags;
then `toybox fsync directory` without `-d`. Stage is retained until restoration.
An exec-in exit alone never establishes complete remote bytes. All command
responses except the exact stage readback must be empty bytes.

The caller durably saves the report, observation receipt and ACK locally before
invoking this adapter. Its before-link callback must make the final same-process
resource check and durably record that check before allowing publication. This
integration remains a separate gate. Any failure stops immediately with
`RemoteAckPublicationError`: original cause, exact phase, `stage_may_remain=True`,
`durability_verified=False`, and `publication_possible=True` from the start of
the link invocation onward. A timeout cannot prove the remote command did not
act. Success means all required commands, including file/directory fsync,
returned successfully; it does not prove hardware crash durability.

Official AOSP Android15 toybox fsync/ln/lib sources confirm fsync error signals,
read-only directory opens and exclusive `ln -T` behavior. The mksh `set -C`
source uses `O_CREAT|O_EXCL` for an absent file; final/stage entry guards remain
required. Captured source bytes and the source-only review are under
`.omx/artifacts/w3-terminal-observation-20261112/remote-ack-*`. An exact
`android/device.config` lookup returned 404; upstream source does not establish
OEM binary configuration. Before real use, the separately reviewed frozen driver
must verify the actual run-as shell/toybox commands and hardlink/file+directory
fsync behavior in its owned private directory. Host stand-ins cannot satisfy
that gate. No device channel is currently present.

## Remote observation coordinator

The next transport-aware coordinator is a distinct probe module,
`adb_terminal_observation.py`, exposing
`publish_remote_terminal_observation(root, observed_report_relative,
receipt_relative, ack_relative, before_link_relative, binding, *, adb, serial,
package, timeout_seconds=2.0, monotonic_ns=time.monotonic_ns, runner=None)`.
It composes the three existing ADB adapters, rather than accepting previously
collected measurements. A supplied runner has the publisher's three-argument
signature `(argv, timeout_seconds, stdin_bytes)`; reader/sampler calls adapt it
with `None` stdin. The normal path uses the adapters' actual subprocess runners.

Binding is an exact dict with exactly `runId`, `sourceFingerprint`, `pid` and
`startTicks`, validated with the existing identity bounds and snapshotted before
callbacks. The terminal report hash is derived from the actual first read, never
provided or guessed by the caller. Preflight validates config, callable clock
and optional runner, an existing canonical private root and four distinct safe
relative output paths with existing nonsymlink parents and absent final entries,
before any transport or clock callback. It does not create directories.

Ordering is host monotonic t0, process sample1, fresh report1, t1, fresh report2,
t2, process sample2, t3. Validate exact bounded nondecreasing clocks immediately,
same PID/startTicks, RSS <=1.5 GiB and available >=512 MiB at each sample. Reuse
the existing validator to compare exact terminal bytes, strict JSON, binding and
the enclosing observation; its existing max-RSS/min-available summary applies.
Persist observed report bytes, deterministic strict JSON receipt and local ACK
in that order with `write_new_durable`, then invoke the actual remote publisher.
The receipt has schemaVersion1, binding including derived hash, terminalReportSha256,
observation, sampleBefore, sampleAfter and clockDomain `host-monotonic-ns`.

The publisher's before-link callback takes t4, a third same-process/resource
sample, then t5, checking t3<=t4<=t5 immediately. It durably writes the distinct
before-link journal before returning. Journal fields are schemaVersion1, the
same full binding, terminalReportSha256, sampleBeforeLink, sampleStartedNs,
sampleFinishedNs and clockDomain `host-monotonic-ns`. Deterministic strict JSON
uses sorted keys, no NaN, compact separators and UTF-8. Receipt/sample snapshots
cannot be changed by later callbacks. The return dict has `observation` and
`publication`, the latter the actual publisher result.

Every failure stops without retry, rollback or deleting already persisted
evidence. A third-sample/journal failure must prevent link publication and retain
the publisher's typed phase/cause/uncertainty signal. This is a single terminal
handshake, not a continuous polling/launch/restore driver, full release verifier,
true peak measurement or device acceptance. Full wrapper/host-loop/build/frozen
run gates remain mandatory.

## Independent Android wrapper

The new wrapper is `probes/android-qwen-terminal/QwenTerminalInstrumentation.kt`,
class `io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation`, in a new
alias `io.ondeviceai.acceptance.qwenterminal`. It derives from the unchanged heap
wrapper in a distinct file, retains the selected public index25/1776 input and
all existing model/PCM/allocator/foreground/release/teardown/error/heap safeguards,
and uses only `files/w3-qwen-terminal`. The old source, APK, run and failed
acceptance remain immutable. This repeats the public diagnostic input in a new
terminal-observation protocol, not the consumed old run or a quality experiment.

For platform dependency injection the constructor has defaulted parameters
`readProcessStat: (Int) -> ByteArray` and
`waitForTerminalAck: (TerminalAckFile, TerminalObservationBinding) -> Unit`.
The generated class must retain an actual JVM no-argument constructor for Android.
Normal stat IO reads `/proc/<own-pid>/stat` with a fixed65537-byte cap and closed
stream; the wrapper validates1..65536 ASCII bytes, exact PID prefix, last `) `
boundary and field22 positive canonical decimal startTicks<=Long.MAX_VALUE before
the first report write or native call. Test doubles may provide malformed bytes,
and cannot bypass that wrapper validation. The normal wait uses the existing
barrier with exact ACK file reads, SystemClock.elapsedRealtime, Thread.sleep and
the unchanged30s deadline. These dependencies do not alter production App APIs.

Before reserving/writing a report or starting Activity/native work, construct
`TerminalAckFile` in the canonical owned directory and call `requireAbsent()`.
Any existing final/stage entry is rejected. Snapshots add `processStartTicks`
and protocol metadata; the new probeVariant is
`cpu-arena-off-heap-terminal-ack-v1`. The terminal report is persisted only after
all actual release/teardown attempts as in the heap wrapper. Its writer returns
the exact serialized bytes successfully handed to AtomicFile.finishWrite;
the terminal SHA256 is computed from those bytes. Never reserialize to compute
the binding hash or rewrite the terminal report after starting the ACK wait.

Only a successfully persisted terminal report starts the wait, including a
truthful failed terminal. Pass the same runId/fingerprint/PID/startTicks and exact
terminal SHA to the existing binding/barrier. ACK failure or timeout makes the
instrumentation result canceled without changing prior recognition/release/
teardown evidence or the immutable terminal bytes. The finish Bundle carries
boolean `terminalAckAttempted`/`terminalAckReceived`, optional string
`terminalAckErrorClass`/`terminalAckErrorMessage`, and optional string
`terminalReportSha256`. Successful recognition returns RESULT_OK only if the
ACK was received; an ACK for a failed report still returns RESULT_CANCELED.
There are no retries, extra native calls, capture, network, model changes or
automatic cleanup. Host Android/native doubles verify ordering and fault paths;
the normal platform IO, APK and device contract remain separate gates.

## Running-process polling integration

The next bounded driver slice is `terminal_polling.observe_until_terminal(root,
binding, *, adb, serial, package, process_poll, timeout_seconds=2.0,
monotonic_ns=time.monotonic_ns, sleep=time.sleep, runner=None)`. The launcher
remains responsible for freezing/consuming the new run, checking installed APKs,
starting instrumentation and establishing its first report's exact PID/startTicks.
The polling slice starts only after that report exists, never launches or stops
a process and never restores packages. These outer responsibilities remain open
until the complete driver is reviewed and frozen.

The loop preflights the four-key binding, adapter configuration, callbacks and
canonical private output root, then durably creates a new-only `poll-start.json`
before process/clock/transport callbacks. It checks `process_poll()` returns None
for a running process; an exact int means an early exit and bool is invalid.
Each iteration takes an actual `AdbProcessSampler` sample with unchanged identity
and resource budgets, then a fresh `AdbTerminalReportReader` read. Strict JSON
rejects duplicates/nonfinite values; its runId/fingerprint/PID/processStartTicks,
protocol, variant and exact integer 30000-ms metadata must match. `terminal` is an
exact bool, status is running when false and completed/failed when true, phase
is a nonempty string and activeCaseIndex is null or exact zero.

Each sample and report SHA are durably recorded as `poll-sample-NNNNNN.json`.
For a running report, phase/index progress must stay within the inclusive120s
host window and total polling within inclusive240s. One monotonic host clock
is validated across every call, including the remote coordinator's calls; any
regression or callback/IO/resource/identity failure stops immediately. Sleeps
request0.25s and remain a bounded callback responsibility. Host timing is not
device uptime or a true peak measurement.

A valid terminal report enters the actual existing remote coordinator exactly
once, with local paths `terminal-observed.json`, `terminal-receipt.json`,
`terminal-ack.txt`, `terminal-before-link.json`. That coordinator takes two new
reads and all three samples, writes durable evidence and publishes ACK. After
successful publication the loop returns immediately without another process poll
or resource read, avoiding an artificial post-ACK exit race. It returns the
coordinator result plus pollSamples and explicit false qualityAccepted and
truePeakMeasured. A failed terminal is still only observed. Exceptions retain
the actual typed publication uncertainty and partial evidence; no retry/rollback
or report rewrite is permitted. The outer driver must still handle errors,
bounded instrumentation completion, collection and exact restoration.

## Exit criteria

Fault regressions prove wait-until-ACK, bounded timeout, strict identity/bytes,
callback/clock failure propagation and host observation ordering. Actual Kotlin
compilation and Python tests pass; a separate supplied-source review assesses
their scope. The old frozen hashes and old failed verdict remain unchanged.
This component milestone is not Android packaging, device acceptance, continuous
sampling, true peak, allocator root cause, W3/R4/R5 completion or release approval.

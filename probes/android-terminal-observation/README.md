# Terminal observation contract components

This isolated probe supplies a bounded Kotlin ACK barrier, a pure Python
validator, local file primitives, local and transport-aware observation
coordinators, and three ADB adapters for a future independent W3 run. Android runner
integration and device acceptance remain pending. No consumed run is changed.

`TerminalAckFile` reads the generated alias-private ACK file without writing or
normalizing bytes. Its constructor accepts an existing final so it can be read;
`requireAbsent()` separately refuses stale final/stage entries before future
native work. Reads use no-follow file attributes/open, a fixed 1025-byte buffer
and closed channels, rejecting empty/oversized/nonregular files, zero-progress
reads and observed size changes. The existing barrier validates exact protocol
bytes and the deadline. The portable acceptance runner covers 13 external file
checks, 6 real-file/barrier checks and 4 parent boundary checks on macOS fixtures:

```sh
rtk proxy python3 probes/android-terminal-observation/check_ack_file.py /private/tmp/w3-ack-file-check-new
```

Pass `--android-jar /absolute/cached/platform/android.jar` for a separate
`-no-jdk` Android API classpath compilation. This does not run on Android or
establish the APK's minimum API. A `File` argument can already have normalized
duplicate separators before construction; validation compares its observable
path and rejects retained dot/parent components and symlink ancestry. The private
single-writer scope does not promise atomic protection from malicious same-UID
path replacement or preemptible filesystem calls.

`TerminalObservationBinding.ackBytes()` and Python `acknowledgement()` share six
ASCII lines terminated by LF: protocol, run ID, fingerprint, PID, process start
ticks, and terminal-report SHA256. The host validator requires identical terminal
report bytes and an enclosing same-process resource observation. A failed report
may be observed; an ACK never accepts recognition quality or release readiness.

From the foundation worktree, using existing cached compiler dependencies:

```sh
rtk proxy python3 probes/android-terminal-observation/check_barrier.py /private/tmp/w3-terminal-kotlin-check-new
rtk proxy python3 -B -m unittest discover -s probes/android-terminal-observation -p 'test_*.py' -v
```

The Kotlin runner requires a new output directory and writes compilation output,
test output and a receipt containing the Java/classpath for `AckFixtureKt`.
It compiles with `-Werror`, runs 45 checks and the cross-iteration clock regression.
The Python suite contains 171 test methods: 22 validator checks, 45 local file
checks, 35 coordinator checks, 26 ADB sampler checks, 11 report-reader checks
18 ACK-publisher checks and 14 remote-coordinator checks,
with additional boundary
subtests. Sampler tests use injected responses and real local subprocesses that
stand in for ADB; the suite does not connect to a phone.
`AckFixture.kt` emits actual Kotlin bytes for cross-language comparison.

The callbacks used by the Kotlin barrier must themselves have bounded execution;
the pure component cannot interrupt an unbounded filesystem or transport call.
`terminal_file_io.py` exposes `read_stable(root, relative, max_bytes)` and
`write_new_durable(root, relative, payload)`. Supply an existing canonical private
directory, such as a newly created directory under `/private/tmp`; aliases such
as macOS `/tmp` are deliberately rejected. Relative file paths cannot contain
empty, dot or dot-dot components or symlinks. Reads are bounded and revalidate
file metadata, the named root and the directory chain against retained FDs.
Writes stage complete bytes, fsync the file, publish without overwriting an
existing target, and fsync the directory. These checks assume a private
single-writer root; inode comparisons do not provide atomic conditional unlink
against malicious same-UID writers.

`PublishedButNotDurableError`, an `OSError` subclass, marks postpublication
directory-fsync or staging-unlink errors with `published=True` and
`durability_verified=False`, retaining the original error as its cause.
`staging_may_remain=True` additionally marks a staging-unlink failure. The
complete final file may be visible; visibility does not prove directory
durability. The caller must stop, retain the failure and inspect the same
operation's evidence; it must not automatically retry or treat the exception as
proof that no bytes were published.

`publish_terminal_observation()` preflights four distinct paths and takes two
actual local report reads between injected process samples. Four timestamps use
one host monotonic clock domain. It validates each sample, snapshots callback
data and durably publishes the observed report, deterministic strict JSON receipt
and ACK in that order. Its resource summary is the greater RSS and lower available
value of two samples. Errors stop the operation without retry or rollback of
earlier durable outputs.

`AdbProcessSampler` supplies a subprocess-capable sample callback. It reads
stat/status/meminfo/stat for one configured PID/startTicks, validating identity
and required values after each read. Remote commands use one safely quoted
string and `head -c 65537`; responses must be 1..65536 bytes of ASCII without NUL.
Each call has a configured timeout of at most two seconds. Errors propagate
without retry. The adapter returns measurements, including zero; resource
acceptance remains the coordinator's responsibility. The normal subprocess
boundary is tested with a private fake ADB executable. Actual Android process
sampling remains unverified.

`AdbTerminalReportReader.read()` performs a new subprocess call on every read of
the distinct `files/w3-qwen-terminal/report-<run_id>.json` path. A safely quoted
remote script checks static directory/file types and symlinks, then reads at most
1 MiB +1 byte. It returns exact 1..1 MiB bytes without JSON parsing, caching,
normalization or retries. Four external tests and seven parent methods verify
the adapter; the latter execute a real local fake ADB and host shell, including
changing bytes, static symlinks, the head bound, errors/stderr and timeouts.
Actual Android command behavior and fresh-read ordering inside the full host
loop remain unverified.

`AdbTerminalAckPublisher.publish(payload, before_link=...)` uses five bounded
transports: guarded noclobber stage write with exact stdin, bounded exact stage
readback, file fsync, exclusive hardlink publication, and directory fsync. Its
required callback runs after stage fsync and before link publication. The caller
must use that callback to check the same process and persist the final resource
journal; the adapter alone does not establish that full ordering. Stage remains
until explicit restoration. Each publisher permits one actual attempt; invalid
arguments do not consume it. There are no retries or automatic cleanup.

`RemoteAckPublicationError` retains the original cause and phase, with
`stage_may_remain=True` and `durability_verified=False`. `publication_possible`
becomes true as soon as the link command is invoked: even a timeout can leave a
complete final visible. Success records successful file/directory fsync command
returns, not hardware crash durability. Tests cover actual local shell/files,
hardlink and fsync stand-ins, including short writes, stale entries, callback
failure, directory-sync failure and real subprocess timeout. AOSP source review
supports the command semantics; actual OEM shell/toybox and filesystem behavior
remain a separate device gate.

`publish_remote_terminal_observation()` composes all three actual adapters. It
preflights the binding/configuration/output paths, encloses two fresh report
reads in same-process resource samples, validates the exact report bytes and
persists the local observed report, receipt and ACK. After remote stage readback
and file fsync, its before-link callback takes a third same-process resource
sample and durably writes a separate journal before allowing publication.
Six timestamps use one host monotonic clock domain. Any error stops without
retry or rollback; publication uncertainty remains typed and evidence is retained.

Four external methods and ten parent methods cover composition, binding
snapshots, resource rejection and clock regression. Six parent methods execute
the real adapters through a local fake ADB, host shell, hardlink and fsync,
including the old process-exit race, journal failure preventing publication and
directory-fsync failure leaving a complete final. This is one terminal handshake;
it is not the complete polling/launch/restore driver or Android device evidence.

These tests verify local IO and callback ordering. Real device sample collection,
fresh report transport, Android ACK IO, Android build/package review and a fresh
frozen device contract remain separate work. See the [pure-component scope](../../docs/validation/w3-terminal-observation-components-20261112.md),
[local file validation](../../docs/validation/w3-terminal-file-primitives-20261112.md)
[coordinator validation](../../docs/validation/w3-terminal-coordinator-20261112.md)
[sampler validation](../../docs/validation/w3-adb-process-sampler-20261112.md)
and [remote-coordinator validation](../../docs/validation/w3-adb-terminal-observation-20261112.md).

# Terminal observation contract components

This isolated probe supplies a bounded Kotlin ACK barrier, a pure Python
validator, local file primitives, a local observation coordinator and an ADB
process-sampling adapter for a future independent W3 run. Android runner
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
The Python suite contains 128 test methods: 22 validator checks, 45 local file
checks, 35 coordinator checks and 26 ADB sampler checks, with additional boundary
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

These tests verify local IO and callback ordering. Real device sample collection,
fresh report transport, Android ACK IO, Android build/package review and a fresh
frozen device contract remain separate work. See the [pure-component scope](../../docs/validation/w3-terminal-observation-components-20261112.md),
[local file validation](../../docs/validation/w3-terminal-file-primitives-20261112.md)
[coordinator validation](../../docs/validation/w3-terminal-coordinator-20261112.md)
and [sampler validation](../../docs/validation/w3-adb-process-sampler-20261112.md).

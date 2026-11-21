# W3 terminal ACK file adapter

`TerminalAckFile` is integrated from the free hy3 candidate and two reviewed
corrections. Cached Kotlin 2.2.20 compilation with `-Werror` and all **23 real-file
checks pass**: 13 external checks, 6 reader/barrier integration checks and 4
independent parent boundary checks. A separate compile against cached Android
API33 `android.jar` with `-no-jdk` passes. No Android runtime, APK or real-device IO
acceptance is implied.

The constructor validates an existing canonical absolute private root and ASCII
run ID while allowing an existing ACK to be read. `requireAbsent()` separately
refuses any stale final/stage entry. Reads preserve raw bytes, use no-follow
attributes/open and a fixed 1025-byte buffer, close the channel, and reject empty,
oversized or nonregular files, zero-progress reads and observed size changes.
Protocol and deadline validation remain in the existing barrier.

The tests first produced direct missing-type compilation failures. The initial
source then failed with `missing return statement`. Its correction compiled and
passed 13+6 checks, but the independent dot/parent alias check failed because it
compared a normalized input path. The exact external block correction now
compares the original observable path and also checks post-read type/length.
All failed candidates and original outputs remain under
`.omx/artifacts/w3-terminal-observation-20261112/ack-file-*`.

The final supplied-context review initially returned `revise`. A JDK21 execution
using the actual compiled reader showed that duplicate separators disappear in
the Java `File` constructor before the adapter receives its argument; comparing
`File.path` cannot recover the earlier String. The review also quoted a non-local
`return out` absent from the current source. Both findings were withdrawn after
this exact evidence was supplied; adjudication returned **accept, no findings**.
The original review and reproduction remain alongside the adjudication. The
contract concerns the observable supplied `File`; retained dot/parent components
and symlink ancestry are rejected.

Reproduce from foundation using the existing compiler cache and a new output
directory:

```sh
rtk proxy python3 probes/android-terminal-observation/check_ack_file.py /private/tmp/w3-ack-file-check-new
```

Use `--android-jar /absolute/cached/platform/android.jar` for the separate API
classpath compilation. This is not minimum-API or Android-runtime verification.
The tests do not inject a zero-progress channel or a size-changing disk syscall;
those guards were inspected in source. The alias-private single-writer contract
does not promise malicious same-UID atomic-race immunity or preemptible disk IO.

Fresh checks preserve all 120 old heap frozen hashes, the original report SHA
`e80a4678468e736fdf20049fa2f5e4cee49a5a32c437da83b6fcb1db570b8a0e`
and failed-verdict SHA
`3575b70f0531560aeecc94935cf41f4415c15e1a5bef7e494b8f57c2cb31681d`.
A read-only ADB enumeration returned an empty list; no installation, microphone,
AiAi request, model/runtime download or inference occurred.

W3/R4/R5 remain open. The fresh report-reader candidate has seven passing parent
methods, including actual local fake-ADB subprocesses, but its independent
external test request is still live and has not delivered. It is not integrated
or accepted. Actual report transport, remote atomic ACK publication, the new
wrapper/driver, Android build/lint/APK verification, a separately frozen run,
same-run terminal evidence and restoration remain unfinished. Complete phone
quality/resources, binary composition/source mapping, production notices and
release review remain separate gates. Source branch push is separate.

# W3 fresh remote report reader

`AdbTerminalReportReader` is integrated from the free hy3 source and exact
strict-type correction. All **139 Python methods pass**, including 4 external
reader checks and 7 independent parent methods. Source-only final review accepts
with no findings. All 120 old heap frozen files, the original report and failed
verdict hashes remain unchanged.

Each read invokes a fresh subprocess for the distinct
`files/w3-qwen-terminal/report-<run_id>.json` path. One safely quoted remote script
checks static nonsymlink directory/file types before `head -c 1048577`. Results
must be exact bytes of length 1..1048576 and are returned unchanged. The adapter
has no cache, retry, JSON parsing or device mutation; errors and timeouts propagate.

The parent methods actually execute a local fake-ADB binary whose path contains
spaces. Its host shell runs the generated script against private fixtures. They
verify changing bytes, symlink rejection, missing-after-success failure, retained
stderr, a real 0.25-second timeout, the head bound and config rejection before
any process. This is host subprocess evidence, not actual Android transport or
remote descriptor-level stability.

The first test request naturally ended after 53 minutes with CLI exit0, empty
stdout and stderr `400 prompt is too long: 100001 tokens > 100000 maximum`. Its
submitted prompt was 3696 bytes; the origin of the effective context size is
unknown. There was no usable result or observed quota error. The complete failed
request is retained; it was not treated as delivery or restarted while live.
A distinct reduced four-method test candidate delivered in 87.465 seconds.

Both external and parent tests directly failed with missing-module RED before
repository integration. Expanded parent tests then rejected the first source
with 3 failures and 9 errors: serial/package/run ID lacked exact-string guards.
An exact external block correction fixes that boundary. One external assertion
copied the adjective `generated` from an ambiguous parent instruction into the
literal report path; its 3-pass/1-fail output and the exact external assertion
correction are retained. Implementation was not changed to satisfy that bad
expectation. Both groups then passed, followed by the complete 139-method suite
and AST syntax checks.

Evidence: `.omx/artifacts/w3-terminal-observation-20261112/report-reader-*` and
`docs/validation/w3-adb-terminal-report-reader-20261112.json`. Reproduce from
foundation:

```sh
rtk proxy python3 -B -m unittest discover -s probes/android-terminal-observation -p 'test_*.py' -v
```

W3/R4/R5 remain open. The next remote ACK publisher is tests-first, with exact
stage readback required after exec-in, file sync, a before-link resource/journal
callback, exclusive hardlink publication and directory sync. Official AOSP
toybox/mksh source supports the proposed command semantics; actual OEM commands,
private-filesystem capabilities, coordinator/wrapper/driver ordering, Android
build/lint/APK, a new frozen run, same-run terminal evidence and restoration remain
unverified. Complete phone quality/resources, recursive binary composition/source
mapping, production notices and release review are separate unfinished gates.
No new device operation, inference, microphone/AiAi request or model/runtime
download occurred. The source branch has not been pushed.

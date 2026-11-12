# W3 ADB process sampler

The subprocess-capable host sampler is integrated. Full Python **128/128** passed
in 2.919s, including 26 sampler methods (17 external, nine primary) and the
previous 102. All probe Python files pass AST syntax checks. This is host adapter
acceptance through injected responses and a private fake ADB executable; actual
Android sampling and W3/R4/R5 remain unverified/open.

`AdbProcessSampler.sample()` reads `/proc/PID/stat`, `/proc/PID/status`,
`/proc/meminfo`, then stat again. Both stat reads and status must match the fixed
identity. Field 22 is parsed after the last comm delimiter, including process
names with spaces and parentheses. Required keys, types, units, signed64 bounds
and duplicates are checked immediately. Each remote command is one safely quoted
`exec-out run-as` string using `head -c 65537`; responses must be exact bytes,
1..65536, ASCII without NUL. Each subprocess call uses a timeout of at most two
seconds and checked exit status, capturing both streams. Exceptions propagate
without a sampler retry or another read. Zero remains a measured value; the
coordinator separately enforces resource limits. No clock mapping or peak is
claimed.

The primary tests use actual local child processes to verify argv construction,
successful reads, error stderr/exit propagation at all four positions, timeout,
identity drift and malformed responses. They do not call the real ADB. The sole
read-only ADB device enumeration in this milestone returned an empty list; no
phone was installed, sampled, decoded, recorded or changed.

Delivery and rejection evidence is preserved:

- Both external and primary test groups produced real missing-module RED with
  direct imports and no skips. Source generation overlapped the already-running
  independent external test request after the primary RED; integration waited for
  both reviewed groups and their GREEN results.
- The external test CLI exited 0 with a truncated 262144-byte root JSON. Four
  complete earlier events were decoded without completing the missing tail.
  Event 3 contained the complete JSON source of all 17 methods; its independent
  parse, syntax and SHA256 checks succeeded. This is complete-message recovery,
  not a complete final CLI result or a second test invocation.
- Event 1 reports an internal CLI connection retry. No primary test reinvocation
  occurred; do not describe the CLI as having made no retry or infer quota/auth
  failure from that message. Local help exposes no retry option, and no global
  configuration was changed.
- First implementation (196.066s) returned pseudo-Write text and created no
  actual source file. It was rejected as requested delivery. Text was extracted
  only into an isolated acceptance directory; 23 methods first reproduced one
  failure, then expanded 26 methods reproduced six failures and three errors:
  constructor startTicks overflow, string/bytes subclasses and empty required
  values causing AttributeError.
- An exact helper edit set (48.107s) introduced an IndentationError by replacing
  only an if line and displacing its body. It was rejected before execution.
  A separate one-block helper edit (13.993s) repaired that block. All 26 methods
  then passed in 3.349s; final source and tests were integrated unchanged from
  those candidate bytes/edits. The primary authored acceptance tests, not sampler
  implementation.

Final free hy3 supplied-context source review completed in 123.215s with `accept`
and no critical/important finding. One minor is deferred: the external test
module header still describes its original missing-implementation stage. Current
README and this record describe the implemented adapter. Production source and
test bytes are unchanged since review. This review is static supplied-context
evidence, not independent device or release approval.

The original 120 frozen files, both physical heap report reads and the rejected
verdict match their fixed hashes. The consumed physical run remains 19/20
rejected; its terminal gap is not repaired by these tests. Existing dirty files
are preserved, with this task's shared prefixes staged separately. Source push
and remote synchronization have separate status/receipts.

Reproduce from foundation:

```sh
rtk proxy python3 -B -m unittest discover -s probes/android-terminal-observation -p 'test_*.py' -v
```

[Machine receipt](w3-adb-process-sampler-20261112.json). Original artifacts are
local under `.omx/artifacts/w3-terminal-observation-20261112/sampler-*`, including
raw events, complete-message recovery, rejected candidates, exact edits, RED and
GREEN logs, review, read-only device enumeration and preservation checks.

Next: Android bounded local ACK reads/stale refusal, fresh terminal-report
transport and remote publication, actual host-loop/wrapper race/fault checks,
independent wrapper/driver names, build/lint/APK review and a new frozen unconsumed
device contract. Full physical quality/resources, source-to-AAR/recursive binary
composition, production notices and release review remain open. No consumed run
is replayed or accepted retroactively.

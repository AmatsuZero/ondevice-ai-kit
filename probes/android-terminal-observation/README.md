# Terminal observation contract components

This isolated probe supplies a bounded Kotlin ACK barrier and a pure Python
validator for a future independent W3 run. It does not run Android, read `/proc`,
publish ACK files, perform inference, or change the consumed heap run.

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
The Python suite contains 22 test methods with additional boundary subtests.
`AckFixture.kt` emits actual Kotlin bytes for cross-language comparison.

The callbacks used by the Kotlin barrier must themselves have bounded execution;
the pure component cannot interrupt an unbounded filesystem or transport call.
Filesystem exclusivity, stale/symlink/growing-file rejection, durable host
observation, Android integration/build/package review and a fresh frozen device
contract remain separate work. See the [validated scope](../../docs/validation/w3-terminal-observation-components-20261112.md).

# Independent terminal-observation wrapper

`QwenTerminalInstrumentation` uses the unchanged heap diagnostic core, Activity,
inputs, report and allocator policy, with a distinct `qwenterminal` alias and
`files/w3-qwen-terminal` directory. It deliberately repeats public index25/1776
only to diagnose the new terminal protocol. Old consumed runs stay immutable.

Before report reservation or native work it validates bounded own-process stat
bytes and refuses existing final/stage ACK entries. After actual native release
and Activity teardown it persists the terminal report, hashes those exact bytes
and waits for the existing 30-second ACK barrier. ACK failure cancels the
instrumentation result and leaves terminal evidence unchanged. Bundle ACK fields
are separate from recognition and cleanup errors; observing a failed report does
not accept recognition quality.

Parent host checks compile the real core/Activity/report/policy and ACK components
with Android/native doubles. They cover 58 groups and 783 assertions. Two compile
warnings come from inherited host-double parameter names. Build-tool receipt and
path checks cover ten Python methods.

```sh
rtk proxy python3 probes/android-qwen-terminal/check_wrapper.py /absolute/path/QwenTerminalInstrumentation.kt /absolute/new-host-output
rtk proxy python3 -B probes/android-qwen-terminal/check_preparation.py
```

`prepare_build.py` accepts a successful root-source host receipt, rechecks all
source and adapter hashes, then copies the original immutable FILE source into a
new canonical output. `build_acceptance.py build` uses cached offline Gradle to
assemble and lint; `verify` checks actual APK aliases/runner/classes, all 65 public
assets, exactly four byte-identical arm64 runtime members and the existing debug
certificate pair. Outputs are new-only; failed/partial evidence is retained.

The 2026-11-12 package passed 75 build tasks and actual package checks. This is
host/build/package evidence. Full launch/polling/restore integration, a new frozen
contract and actual Android/OEM terminal observation remain separate gates.
See [validation](../../docs/validation/w3-qwen-terminal-wrapper-20261112.md).

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

`terminal_device_contract.verify_frozen_contract(root, out)` is the pure local
guard for the future outer driver. It checks strict bounded JSON, fixed identities
and budgets, all mandatory and copied-source hashes, package/plan/protection/host
receipts, root-to-copy bindings and both current Python-driver review receipts.
The fixed package profile requires 19 unique nonempty class-name strings. It
rejects observed symlinks including the contract itself, but assumes immutable
private input files rather than an adversarial concurrent writer. It performs no
ADB, process launch, writes or real freeze. The 17 methods below use synthetic
APKs and unexecuted lifecycle placeholders; they do not prove device readiness.

```sh
rtk proxy python3 -B probes/android-qwen-terminal/check_device_contract.py
```

`terminal_device_lifecycle.py` supplies the one-shot startup/run bridge. It shares
the clock created before launch with polling, binds the first report to an actual
PID/startTicks sample, and checks the instrumentation's final ACK flags, result
code and exact terminal report hash. A consumed launch cannot be retried. Failure,
including interruption, retains the original exception and secondary recovery
errors. Host recovery uses at most two waits of five seconds each; callback time
is additional. Successful ACK is followed only by a 15-second wait and local
evidence reads.

```sh
rtk proxy python3 -B -m unittest discover -s probes/android-qwen-terminal -p 'test_*.py' -v
```

These tests use local fake-ADB subprocesses. The injected launch/frozen/stop
callbacks still require the complete device adapter and ownership checks.
Allocator-policy collection, restoration callback wiring, a new complete freeze and same-run
Android acceptance remain pending. See
[lifecycle validation](../../docs/validation/w3-terminal-lifecycle-20261113.md).
See [guard validation](../../docs/validation/w3-terminal-device-contract-20261112.md).

`terminal_source_adapter.adapt_source(source)` pins the immutable FILE driver
bytes and returns a pure adapted source: fixed terminal identity, a frozen-guard
callback before each inherited ADB subprocess, and a dispatcher in place of the
old prepare/run/collect/restore branches. It preserves the existing transfer,
model/hash and ordinary-state helpers. Its guard and dispatcher callbacks remain
the future device driver's responsibility; evaluation does not execute an action.
The frozen-contract guard requires this dependency in both source review maps.
See [source adapter validation](../../docs/validation/w3-terminal-source-adapter-20261113.md).

`terminal_device_prepare.prepare(root, ctx, *, serial, cleanup, nonce_factory)`
records durable stage/all-chunk and install claims before their device effects.
It verifies local inputs, exact ownership marker/stat, installed APKs, runner,
private models and preserved ordinary APK/model bytes. Failure calls the injected
zero-argument cleanup once and preserves the original BaseException. This is a
restricted FILE-helper API: root must equal its actual OUT; the outer dispatcher
must pass actual module-level digest/parse_kb along with its locals.

Its 29 tests use actual local fake-ADB children. The cleanup callback still needs
to invoke ownership-proven restoration and check its result; the complete
dispatcher, new freeze and same-run Android verification remain pending. See
[prepare validation](../../docs/validation/w3-terminal-prepare-20261113.md).

`terminal_device_restore.restore(root, ctx, *, serial)` independently reads
durable ownership claims, without requiring successful preparation or collection.
It checks ordinary package paths, APK bytes and models before and after recovery.
Only aliases matching frozen APK claims may be removed; stage deletion requires
exact marker and directory identity, the preclaimed child set, and regular files
without links. It removes explicit files, the marker last, then the empty stage.
Unknown content or uncertain ownership is retained with `allRestored=false`.
Returning to Demo requires a current resumed record with user0 and exact Activity.

The 20 restore tests use local fake-ADB subprocesses, including partial effects,
malformed/null claims, directory replacement, foreground identity and journal
interruptions. Restore is single-use: an existing recovered journal or outcome
blocks another attempt. The future zero-argument cleanup closure must inspect
`allRestored` and propagate uncertainty while preserving the original failure.
See [restore validation](../../docs/validation/w3-terminal-restore-20261113.md).

`terminal_device_collect.collect(root, ctx, *, serial)` validates local terminal,
ACK, observation, before-link and instrumentation-finish evidence before any
transport or output writes. It then preserves two fresh bounded report reads,
binds their exact bytes and SHA to that evidence, and checks ordinary packages,
APK bytes and models before and after collection. Local invalid proof leaves no
outcome; operational failure records a false outcome and preserves the original
exception. A final publication failure is propagated without rewriting or retry.

The 12 parent tests use actual local fake-ADB subprocesses. A failed terminal can
be collected as observation evidence; quality, true peak and native-policy-effect
acceptance remain false. Full dispatcher/policy/cleanup integration, current full
source reviews, a new freeze and same-run Android verification remain pending.
See [collection validation](../../docs/validation/w3-terminal-collect-20261113.md).

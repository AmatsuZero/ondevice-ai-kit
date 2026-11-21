# W8 ASR observation window: emulator function accepted

The optional resource-observation window is accepted as a test-runner engineering
slice. W8 physical resource budgets remain open.

Free CBC hy3 supplied the runner and a five-line row-duration disclosure
correction. The parent integrated the candidate byte-for-byte; the final Android
test APK built offline in 9.549 seconds. The same nine production Kotlin files
match the accepted ASR source fingerprint. Independent source rereview approved
the bounded correction. No microphone or private audio was used.

## Accepted emulator execution

Evidence: `artifacts/verification/w8-emulator-asr-20261109-0015/`.

- API 36, ARM64, AVD `ODAI_ASYNC_0120`, explicit `emulator-5582`.
- One reserved instrumentation invocation, three public clips, four real Sherpa
  requests, completed terminal, successful backend close, caller inputs unchanged.
- Requested holds 45,000 ms; measured holds 45,007 ms. The row-level
  `durationIncludesResourceHold` disclosure is present. Per-segment inference
  durations exclude the holds: 430.59, 326.79, 451.10 and 360.73 ms.
- The existing explicit-PID observer completed a 30-second window with 28 valid
  identity-bracketed samples. Sampled RSS ranged from 132.68 to 325.33 MiB;
  threads from 14 to 23; raw CPU-tick delta was 243. These are sampled emulator
  process observations, not true peak, GPU allocation, energy, or budget approval.
- All 18 parent checks passed. Four model digests remained unchanged. Only the
  previously absent test aliases and exact transfer staging were removed;
  ordinary App paths and the AVD's prior work were preserved.

## Unaccepted physical attempt

Evidence: `artifacts/verification/w8-physical-asr-20261108-2345/`.

The phone accepted the two verified APKs and four fixed models through 8 MiB
chunks with individual and reassembled digests. Its one reserved workload
published a nonterminal bind receipt, then all 16 threads were observed in
`do_freezer_trap`. The initial 24 samples were static and do not measure ASR.
The host instrumentation command separately exited 255 during a transient ADB
authorization loss; that did not prove device termination.

After reconnection, the same PID and starttime remained active with zero
completed requests. The device's documented per-process `am unfreeze --sticky`
command was issued, but subsequent wait-channel and report reads proved it did
not restore progress. The second observer retained eight partial samples and an
access-failure receipt. No inference was replayed. The parent saved the original
nonterminal report and model digests, stopped only that frozen disposable alias,
then removed both previously absent aliases and the exact staging directory.
Ordinary App paths remained unchanged. Normal SDK close and physical inference
were not proved; no physical resource result is accepted.

## Remaining W8 scope

The opt-in runner and external observer work together on the emulator. A physical
batch still needs a runnable process and a complete terminal/observation pair.
GPU, energy, sustained-workload and cross-capability budgets, other capability
measurements and whole-release approval are separate remaining gates. The
observed window contains loading, real requests and explicit idle holds; it is
not continuous inference.

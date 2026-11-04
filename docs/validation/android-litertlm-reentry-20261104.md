# Latest combined Android LM cancel and reentry

2026-11-04. This closes the ordinary cancel/reentry gap for the frozen
**644bbde40d9904fe8632ffea7e0781df00683c257b4489e53abbb8fcea36df30**
three-backend candidate with the ASR resource controls. No production source,
dependency, model, signing setting or system network configuration changed.

## Candidate and input binding

The existing `ODAI_LM_UI_20261104` / `emulator-5582` API36 AVD was updated
with `install -r` from the retained ASR-resource candidate. Its installed APK
hash matches the retained file; the prior bd89 APK remains available locally
for rollback. The ordinary Activity was launched without validation arguments.
PID **5174** stayed unchanged through both requests and final cleanup.

The candidate's 255 recorded build inputs have **254 current matches**. The
only later difference is `SpeechDemoInstrumentation.kt` moving its headless
ASR download branch before Activity startup; the ordinary APK inputs match.
This is a frozen-candidate test, not a build of current HEAD. Source binding
reuses the exact manifest from the [physical ZIP run](android-litertlm-ui.md).

The four existing Gemma import-file hashes are unchanged across the APK update
and every observation. Weight SHA-256 is
`2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`.
The ordinary DocumentsUI picker selected the existing `ODAI-solid-red-256.png`;
its private picker copy and each observed native `input.image` match
`991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa`.
Prompt remained `Describe this image.`, budget **32 tokens**. No new media,
model import, download, microphone or camera operation occurred. Both capture
permissions remained denied.

## Observed ordinary flow

1. The first request reached `RUNNING` and actual native inference initialization.
   After native vision initialization, ordinary **取消生成** produced
   `CANCELLING / 等待原生任务释放…`; generate and cancel were disabled.
2. The same request reached `CANCELLED`, with no delivered result and generate
   enabled again. Native request-cache files were absent; the selected picker
   copy remained for explicit retry.
3. Ordinary **生成** on the same input reached `RUNNING → SUCCEEDED` in the same
   PID. The output described a `solid, vibrant red rectangle`; the displayed
   identity was `litertlm.android`, `gemma-3n-e2b-it`, revision
   `c03b6f60b8da6c5400b6838a2cf26420f80c0a01`, runtime
   `litertlm-android:0.18.1`.
4. Ordinary **清除输入** showed `尚未选择输入`, disabled generate, and removed all
   remaining input/cache files. The APK, four model hashes and permissions
   remained unchanged. The updated candidate and authorized models remain in
   this dedicated AVD for subsequent work.

PID-filtered native logs are bounded by the device epoch observed before the
first request. They show two SwiftShader/Vulkan **CPU / Software** vision
adapters, two complete `1448/1448` WebGPU delegations and two CPU decode
delegations. Two CPU/GPU destruction pairs appear; the first pair precedes the
second request's adapter initialization. This supports the synchronous
cancel/reentry observations, not complete native allocation accounting or the
separate asynchronous-drain contract. Initial-load registration can emit more
than once, so registration-message counts are not request counts. OpenCL,
initial native-loader checks and UI-frame warnings remain in the raw log.

The 32-token output is truncated. It is not a natural-image quality,
hardware-GPU, timing, memory, energy, sustained-operation or release acceptance.

## Evidence and checks

Ignored local evidence: `artifacts/verification/android-lm-latest-reentry-20261104/`.
It contains the source binding, install receipt, six device-state receipts,
ordinary UI observations, output screenshot, native logs and hash inventory.

```sh
rtk proxy python3 artifacts/verification/android-lm-latest-reentry-20261104/verify.py
```

Fresh result: **320/320 evidence checks passed**, including the 254 source
matches, nine protected files and unchanged production MLX lock. These are
evidence checks for actual UI runs, not SDK unit tests or a new build.

An independent bounded verifier reran the same command (**320/320**) and
returned **PASS for this 644 software-GPU UI scope** after reading the source
binding, device receipts, UI states and native logs. It made no code or device
changes. This is not a whole-branch review or release approval.

An earlier run in this continuation exercised the retained **bd89** APK in
PID3723 before the package-age mismatch was identified. Its four later ASR
source differences are explicitly recorded in
`artifacts/verification/android-combined-lm-reentry-20261104/source-differences.json`
and `.patch`; its **315/315** checks cover only that old candidate. It does not
substitute for the 644 run. Initial verifier assumptions about source equality
and CPU registration-message counts failed; exact source differences and
per-request execution/destruction evidence replaced those assumptions, without
changing production behavior.

ASR real transcription still requires network/resource readiness and separately
authorized audio. PAD protocol/data/calibration and F09 compatibility adoption
remain separate prerequisites. P4/P11 and release remain `open/not-ready`;
the roadmap goal remains active. No push, merge, tag or publication occurred.

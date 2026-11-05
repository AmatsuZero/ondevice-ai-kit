# Android offline ASR — 2026-11-05

## Latest noise correction — supersedes the earlier candidate below

**Latest: corrected-candidate microphone is basically usable by user confirmation;
minor omissions/errors remain. The first two attempts failed content acceptance.** The user read the complete
sentence but received words they had not spoken, then a much shorter incorrect
result. The user reported substantial surrounding noise. Both separate single
attempts were consumed; debugging used synthetic input until the later, separately
authorized corrected-candidate attempt described below.

On the initial exact App, native SDK replay reproduced false transcripts for
three seconds of pure silence and seeded noise. Clean, SNR10 and silence-padded
English matched the expected sentence; SNR0 did not. This demonstrates a real
noise/silence defect, without proving the sole cause of the user's two failures.

The updated backend adds Silero VAD through the existing sherpa runtime, with no
new library. Model size643854, SHA256
`9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`;
the official release digest was checked and the upstream MIT notice is included.
512-sample windows must contain at least256ms of classified speech;200ms context
is retained around speech, with the existing ten-second capture cap and about
one-second silence endpoint. `SpeechNotDetectedException` maps to
`INVALID_INPUT/NO_SPEECH`, with a clear UI message instead of fabricated text.
Microphone capture also requests Android session noise suppression when available;
effect enablement was best effort; the later user-confirmed live attempt below
does not isolate the effect from VAD or environmental differences. VAD is not denoising and cannot separate nearby people speaking.

The actual VAD candidate (`6b6320d3…94baec4`) cold-copy/native replay passed:
silence and pure noise both rejected with `SpeechNotDetectedException`; clean,
SNR10 and padded English still matched. SNR0 still mismatched; Chinese returned
Han text but not an exact match. No live microphone acceptance is inferred.
The final candidate App is
`afed156b40bcd8be0bcf0c5b373a0b30dd5a3daa4c7bd392b3655c7b6926c461`
(457009240 bytes); test APK is
`29b221b02aef05fe96997934daa5f084766312ea538f59545bc489fa9c690760`.
Its seven-case native replay confirms the same passes/gaps above, with
`noiseSuppressorAvailable=true`. Actual effect attachment/quality is not inferred
from that availability flag. SDK12/12, speech26/26 and enabled Demo64/64 passed;
lint0 errors (module1 warning, enabled Demo15). Exact build/source/report evidence
is retained in `final-candidate.json`, `reports-final-enabled/` and
`final-samples.log`; default56 tests/build/lint and shared iOS compile also passed.

The user then explicitly authorized one corrected-candidate microphone attempt.
Ordinary UI again returned `SUCCEEDED`/actual sherpa/nonempty text. Normalized
output length21 vs expected26, edit distance5 (previous attempt: length9,
distance19). Exact match still failed; the user confirmed the result was basically correct
with minor omissions/errors. Record basic functional acceptance, with accuracy
remaining open. This is one
uncontrolled comparison, not a proven denoising improvement. No recording/text
or result screenshot was saved. All three new microphone authorizations are now
consumed. Evidence: `microphone-fixed-ui-result.json`.

Original test APK/Identity runner and exact permission strings were restored;
the final App and four new model hashes plus original SR hash were read back in
`device-final-restored.json`. The final optional App remains installed, with the
original and intermediate APKs preserved for rollback.

A first VAD test crashed on main because a failed optional factory left its
default backend unregistered. The code also allowed Activity and probe to copy
the same model `.tmp` files concurrently. Model preparation is now guarded by a
Mutex, and missing-default initialization closes created backends/reports failure
without fallback or an uncaught registry exception. A new unit regression and a
cold model-directory replay passed. The failed directory was preserved by an
exact rename; original unrelated model/data paths were untouched.

SDK12/12 and speech26/26 passed after VAD; the missing-default regression adds
one Demo case (enabled64/default56). Exact final XML and build results are kept
with the candidate evidence. The first no-speech mapping incorrectly violated
the existing `PLATFORM.reason == null` contract; its real failed test is retained,
and the corrected mapping passes. No independent review verdict is available.

## Independent published consumer — 2026-11-05

`examples/android-speech` now has an opt-in `sherpaAsr` flavor and a standalone
settings entry with no SDK projects. Five SDK modules were published only into
`artifacts/verification/sherpa-asr-consumer-20261105/m2`; the global Maven cache
and remote repositories were not published to. Sherpa Debug/Release and
contract/system ASR/system TTS Debug AAR builds passed. Sherpa and contract
Debug lint report zero errors/warnings. A second standalone build omitting both
sherpa properties passed contract/system ASR with system ASR lint clean.

Resolved artifacts prove sherpa consumes published sdk-core/speech/backend-sherpa-asr
and sherpa-onnx2.0.1; contract/system ASR contain neither sherpa backend nor runtime.
All SDK artifact paths point into the isolated repository; no project components
are consumed. The initial standalone compile failed because AGP defaulted to
Kotlin2.2 against SDK2.4 metadata. Loading the existing pinned Kotlin plugin before
AGP fixed the compiler alignment; metadata checks remain enabled. The first
root-project check omitted the existing Demo assets property and failed during
configuration; the corrected project-dependency build passed (72 tasks,28 executed).

This is compile/link/dependency validation only: the consumer was not run on a
device and no microphone was opened. Build logs, resolved JSON and frozen artifact
hashes live in the directory above. See the [consumer README](../../examples/android-speech/README.md)
for commands. Fresh independent review returned429 with no verdict; author
inspection does not replace it.

## Earlier candidate and historical evidence

The optional `sherpa.onnx.whisper` SDK runs fixed samples on TECNO KM9/API35,
but the earlier ordinary microphone candidate was NOT accepted: two user attempts
returned incorrect content. The corrected-candidate result above supersedes it. The user reported a noisy environment; cause is not yet proven.
Models/runtime are downloaded on the Mac, bundled in the optional APK and
installed over USB. Google connectivity and Android system language resources
are not required. System recognition remains separately selectable; explicit
backend IDs never fall back. Build and SDK usage: [backend README](../../android/backend-sherpa-asr/README.md).

### Earlier device results

| Input / path | Result | Measured time |
| --- | --- | --- |
| Synthetic English PCM → SDK/JNI | Nonempty, normalized expected sentence matched | 1848 ms; repeat 2068 ms |
| Synthetic Chinese PCM → SDK/JNI | Nonempty Chinese text, exact expected sentence did not match | 1747 ms; repeat 1803 ms |
| Newly authorized single microphone attempt → ordinary Demo | `SUCCEEDED`, actual backend `sherpa.onnx.whisper`, nonempty result; normalized expected sentence did not match | No instrumented capture/inference timing |

The two synthetic runs used host-generated 16 kHz mono PCM; neither opened the
microphone. The user then explicitly authorized a new single microphone test
while beside the phone. The ordinary UI's start button was clicked once; no
second microphone request was made. The success/backend/nonempty/match flags
were saved, without saving raw microphone audio, text or a result screenshot.
The first UI query clipped the transcript below the viewport; a subsequent scroll
confirmed nonempty text. The user explicitly reported words they had not spoken. A separately authorized
second attempt also returned incorrect, much shorter content despite a complete
spoken sentence. Both attempts are failed content acceptance. This is a completed
capture/decode path only,
not an accuracy, WER/CER, latency or broader lifecycle acceptance result.

An earlier background-only synthetic attempt timed out after 120 seconds.
Target PID21637 used 0% CPU and every inspected thread waited in
`do_freezer_trap`, with Settings foreground. The test now starts MainActivity
before inference; the foreground attempt completed both languages. No production
power/battery settings were changed. The original failed-attempt marker is kept.

The first streaming App installation also timed out after 180 seconds, leaving
the original App installed. USB push took 7.253 seconds at about 60 MB/s; the
remote SHA256 matched before separate `pm install -r` returned `Success`.

### Earlier candidate and restoration

- Installed ordinary App: `b67c39e99015c18a5d6b270dc8d1eaf9ef47102e100426f3de1567d6c62d805f`
  (456566510 bytes; LM/SR/OpenCV/sherpa enabled).
- Foreground sample test: `a9c99d4fefef81de6c73a1f18127246962dbb1c66c85b8940561918482b8dedf`.
  Final Han-flag test: `15e64d680a66a8af13872a013a301d828ae5d8428393456bc0a36ef1d5aa9b90`.
- Original test APK restored:
  `7b9e47ce9a9b9fe51c673c002697a16c3fe4dd6ee382f70c3a32c3c9493a621d`;
  `IdentityModelInstrumentation` restored and permission strings unchanged.
- Original App rollback APK remains backed up and verified:
  `405d64e1bf67d1879ff96db386282066e7cda612a2b77134377e070feec082c0`.
  The new optional ordinary Demo is intentionally left installed.
- Installed encoder/decoder/tokens hashes match the pinned model; the original
  `no_backup/video-sr/ESRGAN.tflite` hash remains
  `1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b`.
  Only these exact paths were read; no identity templates or private media inspected.
- Ordinary Speech UI shows both backend IDs, `sherpa.onnx.whisper · available`,
  offline instructions and enabled microphone button. `IDLE` was observed before
  the newly authorized attempt; final state is the successful result.

### Earlier local verification

- Sherpa unit tests 9/9; Android speech 25/25; enabled combined Demo 63/63,
  all with zero failures/errors/skips. Enabled APK/androidTest/Release AAR built.
- Disabled Demo 55/55 and APK/lint built without sherpa/model properties;
  shared iOS arm64 compile passed.
- Lint: sherpa 0 errors/1 dependency-version warning; enabled Demo 0 errors/15
  warnings. Warnings are not described as clean lint.
- Preparation tool download/size/hash checks executed, CLI help and Python compile
  passed. No dedicated preparation-tool unit suite was added.
- Nine original protected paths and production MLX lock hashes checked unchanged.
  A 50-file relevant source snapshot matches the App input set; it is not a full
  build-input provenance claim. Test-source hashes are recorded separately.
- Author inspection only. Prior independent agent calls returned 429; there is no
  independent review verdict and those failed agents were not retried.

Raw evidence is ignored under `artifacts/verification/sherpa-asr-20261105/`:
`foreground-samples.log`, `han-samples.log`, `microphone-ui-result.json`,
`device-installed.json`, `device-test-restored.json`, `device-models-after.json`,
build logs, XML reports, frozen APKs and pre-microphone UI captures. Synthetic
sample files are not recordings of the user.

## Remaining scope

Strong-noise SNR0 and Chinese exact-match quality remain unsuccessful. Real
cancellation/reentry, long-running resource use and API26 physical acceptance
remain unverified. Synthetic silence and seeded pure noise were rejected by the
final candidate; that does not prove robustness for every acoustic environment.
Basic microphone functionality is user-confirmed with minor recognition errors;
all three new single-attempt authorizations are consumed. No further recording
is implied.

The Google system backend's language-resource problem remains open. PAD and
identity calibration, F09 distribution, P4/P11 and release remain open/not-ready;
this work does not change the foundation milestone count of 10/12.

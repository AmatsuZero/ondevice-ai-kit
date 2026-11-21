# Optional Android offline ASR

`sherpa.onnx.whisper` implements `SpeechRecognitionBackend` using CPU-only
sherpa-onnx 2.0.1 and multilingual Whisper tiny int8. Android API 26 or newer;
`en`/`en-US` and `zh`/`zh-CN` locales are supported. Other languages are rejected.
There is no network download or fallback to Android system recognition.

Prepare the pinned runtime, model files and license notices on the computer:

```sh
python3 tools/prepare_sherpa_asr.py --output artifacts/sherpa-asr
./gradlew :demo:androidApp:assembleDebug \
  -PenableSherpaAsr=true \
  -PsherpaMavenRepo="$PWD/artifacts/sherpa-asr/m2" \
  -PsherpaAsrAssetsDir="$PWD/artifacts/sherpa-asr/assets"
```

The optional Demo APK bundles about 104 MB of model files and makes this backend
the default. Install it by USB; the phone needs no Google access or account.
The existing Android system backend remains separately selectable by its explicit
ID. Omit these properties to build the original Demo without sherpa dependencies
or model assets. Runtime/model files are generated inputs, not committed sources.

SDK callers can provide a directory containing `tiny-encoder.int8.onnx`,
`tiny-decoder.int8.onnx`, `tiny-tokens.txt`, and the pinned `silero_vad.onnx`:

```kotlin
val backend = SherpaAsrBackend(context, SherpaWhisperModel(modelDirectory))
val result = backend.recognize(SpeechRecognitionRequest(locale = "en-US"))
// Use result.transcript; close the backend when its owner is finished.
backend.close()
```

The host owns `RECORD_AUDIO` permission. Model hashes are verified off Main before
capture/native loading. Microphone audio stays in memory: mono PCM16 at 16 kHz,
up to ten seconds, ending after detected speech and about one second of silence.
Silero VAD gates speech before Whisper: at least256ms must be classified as speech,
with200ms context retained. No speech throws `SpeechNotDetectedException`; the
Demo shows a retry hint. Available Android noise suppression is enabled per capture
session and released afterward. This cannot guarantee accuracy under strong noise.
Cancel
stops capture; synchronous native inference drains before admission is released.
Close cancels/drains the active request and permanently rejects new requests.

`transcribe(samples, locale)` accepts caller-owned mono 16 kHz float samples,
copies them, and never opens the microphone. The temporary copy is cleared after
the request. It does not add file-picker support to the Demo bridge.

The operator-only `SherpaAsrInstrumentation` consumes synthetic PCM assets from
`-PsherpaAsrFixtureDir=...`; it reports result/match flags without saving text or
opening the microphone. See [device validation](../../docs/validation/android-sherpa-asr.md)
for the exact measured coverage and remaining checks.

Runtime: Apache-2.0. Whisper and Silero weights: MIT. The preparation tool includes
notices in the APK assets and pins the AAR and four model artifacts. Model source:
`csukuangfj/sherpa-onnx-whisper-tiny`, revision
`65176e2deb88badc814a94058666cadccc29b61c`.

JVM notice assets under android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/jvm/ cover a known subset for the JVM slice only. Original upstream texts and attribution excerpts are preserved byte-for-byte without edits. They do not prove final DEX content or complete composition, and P2/P3/P4/W3 applicability reviews remain open.

Native notices for this module are declared in android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/native/index.json, with selected raw notice files' raw bytes preserved unchanged under that native/ directory for the bounded known native subset only. This is not a complete final-composition notice set. Known upstream modifications are tracked separately in UPSTREAM-MODIFICATIONS.md, and broader verification remains open. No complete legal approval is implied.

## ORT nested notices supplement

The module-relative index location is `src/main/assets/ondeviceai/sherpa-asr/notices/native/ort-nested/index.json`. It records a known incomplete subset, original selected copies, and explicit unselections; it is documentation for that subset only.

mp11 README/header excerpts are copyright/license declarations, not full BSL; the existing full BSL is sibling `native/asio-asio-1-24-0-observed-build/LICENSE_1_0.txt`. The JSON embedded attributions document retains CC0.

JSON test/tool/generic GPL/Apache/BSD texts are not automatically selected merely because they are present in the archive; dep5 mapping and JSON tests OFF are the recorded basis. KleidiAI compiled-source heads are Apache; generic BSD root text alone does not prove retained BSD code. The nested Eigen commit differs from the Sherpa Eigen version and stays separate.

## NDK C++ notice supplement

`android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/native/ndk-cxx/` carries component-root `LICENSE.TXT` files for `compiler-rt`, `libcxx`, `libcxxabi`, and `libunwind` from the recorded NDK r27d/r30 LLVM-base source identities. This is an intentionally incomplete supplement to the existing notices for those selected component roots only. It does not establish final binary presence, patch/body coverage, sanitizer or architecture inclusion, final artifact composition, legal release acceptance, or complete P2/P3/P4 acceptance, and it makes no license-compliance conclusion.

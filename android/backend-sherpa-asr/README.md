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

# Offline Android ASR without Google connectivity

## Noise correction addendum

Two separately authorized live attempts returned incorrect content. The user
completed the sentence and reported a noisy environment. Native synthetic replay
also produced false text from silence/noise. Add a fixed MIT Silero VAD model
through the existing runtime:643854bytes, SHA256
`9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`.
No new library. Process512-sample windows; require256ms speech, preserve200ms
context and reject no-speech before creating Whisper. Replace amplitude
endpointing for capture and apply the same gate to caller-supplied samples.
Surface a typed no-speech error through the bridge. Best-effort Android session
NoiseSuppressor retains VOICE_RECOGNITION; always release the effect and recorder.
Strong-noise accuracy and live effect benefit remain separate acceptance gaps.
Model preparation is serialized; missing default initialization must report a
failure without fallback or crash. No new recording follows implicitly from this
addendum. See validation for failed and current exact candidates.

The user selected a new optional sherpa-onnx backend after the TECNO system
recognizer rejected en-US with error13 and the device could not reach Google.
The user approved proceeding; their standing autonomous-execution instruction
applies to the reversible implementation and validation below.

## Approach and scope

System ASR remains available under its existing ID. A new optional
`sherpa.onnx.whisper` backend implements `SpeechRecognitionBackend`; the Demo's
existing Default/Explicit selector registers both. With `enableSherpaAsr=true`,
the explicitly enabled offline backend is the default. No automatic fallback.
Disabled builds do not depend on sherpa or include models.

Use official sherpa-onnx2.0.1 Android AAR (Apache-2.0), SHA256
`a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`.
Use MIT-licensed multilingual Whisper tiny int8 exported by the upstream
maintainer, Hugging Face `csukuangfj/sherpa-onnx-whisper-tiny` revision
`65176e2deb88badc814a94058666cadccc29b61c` (encoder/decoder/tokens about104MB).
CPU inference, English and Chinese initially supported with explicit locale
mapping. Quality/performance are measured separately, not assumed from model size.

The host preparation tool downloads and verifies pinned artifacts on the Mac,
prepares a worktree-local Maven repository and a model assets directory. The
optional Demo APK includes these assets, installed by USB; the phone requires no
Google account or network. The SDK also accepts a caller-owned model directory.
Include runtime/model license notices. APK installation is not model-runtime proof.

## Runtime and lifecycle

Load/verify models and run native inference off Main. Microphone input is PCM16,
mono,16kHz, held only in memory. End a short utterance after detected speech and
silence, or at a10second maximum. Do not start capture on availability, startup,
asset copying or UI navigation. Permission remains owned by the existing host.
Do not persist microphone audio or log transcripts. Recognition text is returned
normally to the requesting UI. Cancellation stops recording promptly, suppresses
results and waits for synchronous JNI inference/release before allowing reentry;
close drains the active call. No unsafe native interruption or release during decode.

A separate samples API permits an explicitly provided synthetic fixed waveform
to validate real JNI/model execution without another microphone authorization.
This does not add Android file-picker support to the shared bridge in this slice.

## Alternatives

The Google system model has no documented import-from-file API; downloading an
APK alone does not install its separately managed model. Retaining it would keep
this device blocked. Vosk is another offline option, but sherpa was selected by
the user for the optional backend. Whisper tiny is a builder choice for explicit
MIT weights and straightforward multilingual model files; SenseVoice was not
selected because its current official model card refers to separate model terms.

## Verification and boundaries

Test registry selection/default isolation, locale/model validation, concurrency,
cancellation/drain and closed admission. Build/lint both enabled and disabled Demo
configurations. On TECNO validate a fixed synthetic sample through the real SDK,
then ordinary UI/backend visibility without capturing microphone. Real microphone
validation needs a new user-present start cue/authorization; the previous single
system attempt has been consumed. Preserve nine protected paths and production
MLX lock. This new backend does not close the system-ASR resource issue, PAD,
F09, P4/P11 or release acceptance.

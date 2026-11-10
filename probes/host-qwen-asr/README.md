# W3 Qwen3-ASR host screening probe

Isolated evaluation only. This does not register a backend, change the Demo,
download model assets, or request microphone access.

The design and prospective gates are in
`docs/superpowers/specs/2026-11-10-w3-asr-reselection-design.md`.
Use an isolated environment with Python3.14, `sherpa-onnx==2.0.1` and
`numpy==2.4.2`. Protocol paths are absolute and the model root must be canonical.
The protocol lists every model artifact with byte count/SHA256, and every
16kHz mono s16LE PCM case with sample count/SHA256, locale and unchanged reference.

```sh
rtk proxy <isolated-python> probes/host-qwen-asr/check_runner.py probes/host-qwen-asr/host_qwen_asr.py
rtk proxy <isolated-python> probes/host-qwen-asr/host_qwen_asr.py --protocol <frozen.json> --output <new-report.json>
```

The contract check uses a fake ASR runtime and temporary fake model bytes; it
does not establish actual model quality. The parent owns the 120-second per-call
and 4GiB process-RSS guards. Reports are reserved exclusively, retain all planned
rows before native construction, and persist running/recognized/error states.
The parent independently checks actual status, model/input/source binding and
scores with the existing `tools/speech_error_metrics.py`.

Python reference clearing/GC is reported without claiming native destruction.
Mac RSS/timing cannot establish Android performance, system-service memory,
true peak, GPU or energy acceptance. A failed candidate is retained without
parameter sweeps or automatic recognition retries.

Runner source was supplied by free CBC hy3 and integrated using its exact final
patch. Primary review/fault validation is separate; native review returned429
without a review result. The local original prompts, failures, candidate bytes,
raw events and receipts are retained under `.omx/artifacts/w3-model-reselection-20261110/`
and the named `/private/tmp/odai-hy3-w3-qwen-host*` task directories. These local
artifacts are not guaranteed available from a source checkout.

`host_admission_vad.py` is a separate Silero v5 classification probe. It never
calls ASR and does not crop recognition input. `check_admission.py` uses a fake
VAD runtime to check input prevalidation, mutation detection, durable progress,
error persistence and output preservation. Run it with the same isolated Python:

```sh
rtk proxy <isolated-python> probes/host-qwen-asr/check_admission.py probes/host-qwen-asr/host_admission_vad.py
```

The real fixed diagnostic admitted 8/9 speech cases and rejected 4/4 nonspeech
cases, so this admission candidate was rejected. See
`docs/validation/w3-qwen-admission-observed-20261110.md` for the boundary and hashes.

`languageMode` defaults to `forced`; `auto` explicitly sets an empty language
option. The isolated `admit-auto-then-forced` mode runs a fresh automatic stream
first, skips the second decode only for exactly empty raw text, and otherwise
runs a fresh stream with the request language on the same whole waveform.
`check_language.py` and `check_two_pass.py` use fake runtimes to check the actual
options, first-stage report visibility, waveform reuse, dispatch counts and
original exceptions. This is an evaluation probe, not an SDK backend.

The two-stage development screen passed its frozen gates (9 speech and 4
nonspeech inputs, 22 decode calls). Independent quality confirmation, model
redistribution notices and Android performance/quality acceptance remain open.
See `docs/validation/w3-qwen-two-pass-observed-20261110.md`.

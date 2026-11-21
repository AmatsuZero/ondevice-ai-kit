# Fixed FLEURS 1906 prefix diagnostic

These three Kotlin files are the exact sources accepted on 2026-11-10, retained
from `artifacts/verification/w3-prefix-control-20261110-local/`. The source hashes
and observed run identity are recorded in `accepted-source.json`.

The diagnostic compares the second slice `[83040,166080)` of the fixed public
FLEURS clip 1906 using two slice-local crops: `[2432,36480)` and `[0,36480)`.
Both share the same suffix and use the same Whisper tiny model/runtime settings.
It does not invoke production VAD, change models, use a microphone or evaluate
crop CER. Dataset provenance is in
[the Mandarin fixture notice](../android-natural-file-asr/mandarin-SOURCE-NOTICE.txt).

- `Fleurs1906PrefixControlPlan.kt` pins the canonical reference, input and model
  hashes, validates actual disk bytes before native construction, and implements
  the shared two-arm orchestration and strict JSON reporting.
- `Fleurs1906PrefixControlInstrumentation.kt` binds that plan to the isolated
  Android foreground host and the real Sherpa native runtime. Build it with the
  existing foreground host and Sherpa dependency; it is not an ordinary App entry.
- `PrefixControlChecks.kt` is the host acceptance entry point. Compile it together
  with the plan using Kotlin/JVM, then invoke
  `io.ondeviceai.demo.PrefixControlChecksKt <output-directory> <fixed-1906.pcm> <model-directory>`
  with assertions enabled. It performs no JNI inference. The fixed PCM and four
  model files must match the pinned sizes and hashes; they are not duplicated here.

The original acceptance had 62 host assertions, Android build/lint and 97 parent
evidence checks. A first run failed before any decode and was restored. The second
run completed the two frozen arms and restored the ordinary App and models.
See [the bounded acceptance report](../../docs/validation/w3-prefix-control-accepted-20261110.md).

Restoring 152 ms changed this local output. There is no independent crop reference,
so this does not establish a CER improvement. W3 quality remains unverified. The
completed device run must not be replayed merely to verify this archive.

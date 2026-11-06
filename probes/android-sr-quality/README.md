# Controlled Android SR quality probe

Local test-only package `io.ondeviceai.probe.srquality`, with its own
instrumentation and private files. No Activity, permissions, network, capture,
or model download. It does not replace the ordinary Demo or its test package.

`prepare.py` snapshots five production files. Four remain byte-identical;
`AndroidSrMedia.kt` gains only a codec-name observer. Its decoder, color
conversion, encoder settings, timestamps and muxer remain identical across
nearest-neighbor, Android filtered Bitmap (bilinear), and ESRGAN runs. Controls
run first. Decoder/encoder selection must match across all three paths.
Production sources, model, dependencies, lockfiles and tile halo are unchanged.

Prepare into a fresh directory; retain the source inventory and build receipts.
Do not reuse generated sources after their production inputs change.

```sh
rtk proxy python3 probes/android-sr-quality/prepare.py . /absolute/fresh/generated
rtk proxy env JAVA_HOME=/path/to/existing/jdk21 ./gradlew \
  --offline --no-daemon --console=plain --max-workers=2 \
  -PenableSrQualityProbe=true \
  -PsrQualityGeneratedDir=/absolute/fresh/generated \
  :probes:android-sr-quality:assembleDebug :probes:android-sr-quality:lintDebug
```

The module is excluded unless explicitly enabled. LiteRT 1.4.2 is the existing
production dependency. Generated sources/build products are not source files
to commit. Check the APK SHA, merged manifest and packaged source inventory
before installing. The current build has four lint warnings, including the
production `KEY_MAX_B_FRAMES` constant introduced at API29; this probe has not
established API26 compatibility.

On the authorized physical device, first inventory active instrumentation and
packages. If this probe package already exists, inspect it and preserve its
reports before replacing anything. Install the verified test-only APK with
`adb -s <serial> install -t <apk>`. Provision only existing approved inputs into
this package's private `files/controlled-sr-inputs/` directory, using `run-as`:

- `input.mp4`: existing CRF18 natural clip, SHA256
  `22b3e924ad5717a8cd278f92fdad3b39b1ed1886106a941e1d9b0287f6b026d8`.
- `ESRGAN.tflite`: existing 4,993,712-byte model, SHA256
  `1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b`.

Use uniquely named shell staging paths if needed, verify copied hashes, then
remove only those exact staged files. Never clear or copy over the Demo data.
Before the real comparison, verify a fresh run with missing inputs writes a
terminal failed report; that check is not a media-processing pass.

```sh
rtk proxy adb -s <serial> shell am instrument -w \
  -e runId <fresh-alphanumeric-hyphen-id> \
  io.ondeviceai.probe.srquality/io.ondeviceai.litertsr.ControlledSrInstrumentation
rtk proxy adb -s <serial> exec-out run-as io.ondeviceai.probe.srquality \
  cat files/sr-quality/<id>/report.json
```

Reports and outputs live at `files/sr-quality/<id>/`. Existing run IDs are
rejected without replacing evidence. `AtomicFile` persists progress and the
terminal report before `finish`. Output files are `nearest.mp4`, `bilinear.mp4`
and `esrgan.mp4`, each required to contain 48 frames at 1280×720 without audio.
A failed current output is removed; previously completed controls remain.
Create `files/sr-quality/<id>/cancel` via `run-as` to cancel at the next codec,
frame or tile checkpoint. A missing/nonterminal report is not a pass.

If ADB disconnects, inspect the same report, recorded PID and active
instrumentation after reconnecting. Do not start a second run while the first
may be active. Collect terminal report and all completed outputs, matching
host/device SHA256 values. A transport exit code or `completed` report alone
does not prove quality improvement. Confirm codec equality, input/model/source
identity, dimensions, frame count and PTS, then measure every output against
the same existing reference with `tools/probes/video_sr_quality.py` and inspect
the same frames. Original source color interpretation remains a limitation.

`qualityVerdict` stays `unverified`. One fixed clip does not establish natural
quality, resource budgets or release readiness. After collecting evidence and
confirming the instrumentation has stopped, remove only this probe's verified
temporary paths/package if appropriate; preserve original App/models and host
evidence. Runtime execution, cancellation and failure-report behavior still
require physical-device verification.

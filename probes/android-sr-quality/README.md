# Controlled Android SR quality probe

Local test-only package `io.ondeviceai.probe.srquality`, with its own
instrumentation and private files. A minimal foreground test Activity keeps
the screen on while visible. No permissions, network, capture or model
download. It does not replace the ordinary Demo or its test package.

The first headless physical-device run stopped progressing at frame 4:
TECNO Hiber logged that it froze the test UID, and every inspected thread was
in `do_freezer_trap`. A cancel sentinel cannot execute while the whole process
is frozen. That nonterminal report and partial output were retained before
stopping only the dedicated probe. The foreground Activity addresses this
test-host boundary without changing media/model code or global power settings.

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
before installing. The initial build has four lint warnings, including the
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

The default fixture is `common-swifts` (320×180 →1280×720). For the already
licensed local Kawaida Waterfall clip, pass `-e fixture kawaida-waterfall` and
provision its fixed 120×180 CRF18 derivative, SHA256
`0903963764dc26c24c051ea1ab8e1e1974e4287bebfa9c61024784e24c139cc7`.
The 480×720 reference is cropped from the native 480×848 source, not upscaled.
Both fixtures require 48 frames/24fps and discard audio. Input hashes and expected
dimensions are fixed for each fixture; unknown fixture names or mismatched
inputs fail. Neither fixture is newly downloaded or captured.

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
and `esrgan.mp4`, each required to contain 48 frames at the selected output
dimensions without audio.
A failed current output is removed; previously completed controls remain.
Create `files/sr-quality/<id>/cancel` via `run-as` to cancel at the next codec,
frame or tile checkpoint. A missing/nonterminal report is not a pass.

The instrumentation starts the foreground Activity before processing and
finishes it after persisting the terminal report. Keep that Activity visible
for the experiment; do not interpret an OEM background freeze as codec/model
execution time or as a tested cancellation outcome.

If ADB disconnects, inspect the same report, recorded PID and active
instrumentation after reconnecting. Do not start a second run while the first
may be active. Collect terminal report and all completed outputs, matching
host/device SHA256 values. A transport exit code or `completed` report alone
does not prove quality improvement. Confirm codec equality, input/model/source
identity, dimensions, frame count and PTS, then measure every output against
the same existing reference with `tools/probes/video_sr_quality.py` and inspect
the same frames. Original source color interpretation remains a limitation.

`qualityVerdict` stays `unverified`. Two fixed clips do not establish natural
quality, resource budgets or release readiness. After collecting evidence and
confirming the instrumentation has stopped, remove only this probe's verified
temporary paths/package if appropriate; preserve original App/models and host
evidence. The foreground physical run completed all three48-frame paths;
the headless missing-input run persisted its terminal failure. An additional
cancel check did not produce a report during USB interruptions; cancellation
remains unverified. Results and limits are in
[the controlled comparison](../../docs/validation/f11-android-sr-controlled-20261106.md).

The second fixed scene completed all three paths; see the
[waterfall comparison](../../docs/validation/f11-android-sr-waterfall-20261106.md).
Its build has 0 lint errors and 7 warnings.

# Android Qwen load-only acceptance probe

Temporary isolated acceptance sources. No production backend integration, audio streams, decoding,
microphone, network requests, or system resource provisioning. The two APKs have no requested permissions.

The external helpers delivered the three Kotlin files; the parent integrated exact accepted bytes.
`QwenLoadCore.kt` verifies six fixed model/tokenizer files and controls one constructor/release.
`QwenLoadInstrumentation.kt` handles exclusive reports, foreground gates and acknowledged teardown.
`QwenLoadActivity.kt` shows static public probe status only.

Parent acceptance scripts:

- `check_core.py SOURCE LABEL`: compile the exact core and run real-file/fake-native fault checks.
- `check_wrapper.py LABEL INSTRUMENTATION ACTIVITY`: compile the exact wrapper and activity with host
  Android/native/model-verifier doubles and the separately accepted coordinator. This does not exercise JNI.
- `build_acceptance.py build|verify`: build the prepared local isolated Gradle project, lint, and inspect
  actual APK classes, permissions, ABI, fixed native-library hashes and common signing certificate.
- `device_acceptance.py preflight|prepare|run|collect|restore`: parent-owned single physical-device run.
  It reads the device identity from ignored local evidence, never from a committed serial.
- `emulator_acceptance.py preflight|prepare|run|collect|restore`: separate user-requested emulator supplement.
  It requires exact API36 ARM64/qemu/AVD identity and an independent run/evidence root. Its executed parent
  differs only in the portable root locator; this is verified in the emulator acceptance receipt.

Local project/evidence: `artifacts/verification/w3-qwen-load-20261110/` and
`.omx/artifacts/w3-model-reselection-20261110/`. These paths are ignored and are not a portable build setup.
They contain the prepared Gradle shell, existing toolchain/AAR references and original failed candidates.
Run each mutating action once after its predecessor succeeds. Inspect a failed/partial action before any
recovery; do not automatically resubmit a model run. Do not stage, install or run while the device gate fails.

The fixed contract is [the Android load protocol](../../docs/validation/w3-qwen-android-load-protocol-20261110.md).
RSS is sampled with a 1.5 GiB target; it is not a true peak. `prepare` installs only the unique aliases and
stages the public fixed model, `run` attempts one construction/release with parent resource protection,
`collect` reads the same result twice, and `restore` removes only manifest-bound artifacts. Native crash
or protection stop may leave a nonterminal report; retain it as failure evidence rather than inventing release.

The unique emulator load-only run has completed and its temporary AVD has been stopped/cleaned. Do not
resubmit it. See [the accepted supplement](../../docs/validation/w3-qwen-emulator-load-accepted-20261110.md).
It does not satisfy the pending physical TECNO resource/quality gates or exercise any ASR decoding.

# Android Demo page space

2026-11-04. The ordinary Android host always allocated its face-preview Box,
including on speech, video and image-understanding pages. Controls were also
capped at380dp. A current-commit combined candidate reproduced the large empty
black region; the no-capture regression measured the speech title at y1444 on
a2400px-high MainFlow/API36 emulator.

The Android host now shows the preview only on face detection/identity pages
and lets other pages use its available height, respecting system drawing insets.
`FaceScreen` accepts a modifier and optional face-page visibility callback;
existing callers retain the380dp default. Page visibility is read **during
composition**, then reported in `SideEffect`. An earlier diagnostic read only
inside the effect failed to notify on return; the regression caught that failure.
The final code restores the preview on return without starting camera capture.
No backend, model, calibration or microphone behavior changed.

`DemoLayoutInstrumentation` navigates the ordinary UI with no camera/microphone,
picker, download or inference actions. The same final test APK fails on the
immutable pre-fix candidate (`speech-tab-titleTop=1444`) and passes on the fixed
App: speech/video/multimodal titleTop178, returning face titleTop1476 and visible
face controls. It also rejects a face-preview accessibility node on non-face
pages. Empty noninteractive preview Boxes are not reliably exposed to AX, so
the restored space is verified by actual title bounds rather than tag presence.

Combined Demo tests62/62, default54/54 and shared iOS simulator80/80 passed;
Android APK/test APK/lint and shared iOS arm64 compilation passed. Compiler
warnings for existing CameraX/delicate API and Kotlin/Native cache object lookup
remain; they did not fail the build. The change is source-compatible with
existing iOS callers; no new physical iOS UI run was performed.

Ignored evidence: `artifacts/verification/android-demo-layout-20261104/`.
`same-test-red.log` and `same-test-green.log` are the final paired device runs;
earlier failed test setup/AX connection, observation timing and callback
diagnostics are retained separately. A tool-created emulator accessibility
endpoint was stopped to let instrumentation attach exclusively. These were
automation failures or intermediate candidates, not omitted passing evidence.

Only the existing isolated MainFlow emulator was updated; its test package was
originally absent and is removed after checks. Prior App bytes remain backed up.
The physical TECNO App/test runner and permissions remain at their restored
checkpoint. No actual ASR attempt has occurred; user-assisted verification is
deferred to2026-11-05 09:00 Asia/Shanghai as described in
[the device-session notes](android-asr-next-device-session.md).

This fixes page-space usability. It does not re-run the three model inference
flows, prove physical touch/camera geometry, or close P4/P11/release gates.

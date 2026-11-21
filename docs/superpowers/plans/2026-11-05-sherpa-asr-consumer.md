# Offline ASR published consumer

Goal: compile an independent Android consumer of the optional offline ASR AAR,
without project dependencies or changes to microphone/device state.

Design: extend the existing speech example with an opt-in `sherpaAsr` flavor.
A standalone settings file reuses the pinned plugin catalog, requires published
SDK mode and reads an explicitly isolated Maven local directory. The host owns
model preparation and microphone permission; the example owns client cleanup.
No new runtime, model or dependency version is introduced.

1. Add the flavor, standalone build entry and example using explicit backend
   selection and cancellation-safe close. Compile using local published artifacts.
2. Inspect resolved runtime artifacts: sherpa flavor includes the three SDK AARs
   and pinned native runtime; contract/system flavors contain no sherpa runtime.
   Build Debug/Release and run relevant lint. Do not record audio.
3. Update ASR/P11/handoff with the user-confirmed basic functionality and exact
   consumer evidence. Preserve quality and distribution gaps; verify protected
   hashes and commit only owned files locally.

Validation uses real Gradle resolution/compile/package checks; no mirrored unit
tests for the sample wiring. Published mode must contain no project components.
Review focus: standalone isolation, opt-in dependency boundary, explicit backend
selection, cleanup and no claim of new consumer/device runtime acceptance.

Execution: all three tasks completed. Standalone enabled140/140 tasks executed,
disabled59tasks (13executed), original project72tasks (28executed). Three relevant
lint variants have zero errors/warnings. P11 tools36/36 pass;240 evidence paths,
255 source hashes and all8 benchmark groups unchanged. First standalone metadata
failure and missing-assets configuration failure retained, corrected builds pass.
Fresh reviewer returned429, no independent verdict; bounded author inspection only.
No device or microphone operations. Nine protected paths and production lock match.

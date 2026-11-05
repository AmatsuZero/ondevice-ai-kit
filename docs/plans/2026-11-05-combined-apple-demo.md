# Combined Apple Demo delivery

Continue the approved independent-capability SDK/Demo design and F12 delivery.
The current generators produce either Speech/Video/MLXVLM or OpenCV Identity;
deliver an optional combined App using the existing implementations.

Ruling: extend `generate-speech-demo.rb` with `ODAI_OPENCV_IDENTITY_DEMO=1`.
Reuse the Identity generator's three host/bridge sources and local package.
Keep this opt-in independent of MLX; preserve existing default outputs and
the separate Identity probe. No new model, dependency version, threshold,
signing asset or runtime backend is introduced. Uncalibrated policies still
return their existing unavailable result.

1. Add generator tests for the combined products/sources/flags, strict package
   ownership, deduplicated ModelStore, default isolation and missing-input
   rejection before output mutation. Observe the missing-feature failures.
2. Add the optional local package/Identity host sources to the existing
   generator and a distinct combined bundle ID. OpenCV's existing binary
   has no arm64 simulator slice: declare this variant device-only rather
   than implying a simulator build works. Keep MLX's iOS18 deployment floor.
3. Build the new combined device App unsigned in a new isolated source/output
   area using the existing pinned package cache, KMP framework and OpenCV
   binary. Never modify the protected checked-in Xcode project, production
   lock, frozen source or old artifacts. Record the generator overlay identity.
4. Verify the generated project, actual compile/link and full tools regression;
   document the reusable command and local commit. Runtime/UI acceptance stays
   separate; do not retry the unchanged failed iPhone automation tools.

Stop this slice when the combined App can be generated and built from the
reusable entry point. P4/P11 completion still requires their original gates;
this slice adds a concrete combined deliverable, not a release approval.

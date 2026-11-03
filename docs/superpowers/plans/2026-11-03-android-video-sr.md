# Android video SR main flow — 2026-11-03

Continue the accepted video SDK design and roadmap, functionality first. User explicitly authorized an optional official inference runtime and publicly licensed official SR example weights for local validation. Default Demo must remain free of the runtime/weights. Preserve nine protected files and ignored evidence.

1. Select fixed official CPU SR model/runtime and record model license, tensor contract and hashes; no invented weights or interpolation relabeled SR.
2. Implement optional real frame inference and Android decode → SR → encode/mux backend. Preserve frame PTS and requested audio policy; reject unsupported profiles explicitly. Output lease owns private output.
3. Wire private bounded video import, AndroidVideoBridge, local model setup, and open/share using independent exported copies so background cleanup cannot invalidate a viewer.
4. Run targeted RED→GREEN behavior tests, default/opt-in builds/lint, and real device inference/media smoke when device unlocked. Record external gaps separately; do not mark roadmap/release complete.

Ruling: defer exhaustive race/resource/quality matrices as requested; retain necessary main-flow ownership/cancellation cleanup. No push/merge/release.

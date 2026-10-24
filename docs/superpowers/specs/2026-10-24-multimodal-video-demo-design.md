# Typed video Demo input design

2026-10-24. User approved continuation: independent image/video stores, one shared state machine, device inference in a later package.

## Contract

- Add Image/Video media kind to shared state without removing existing image selection or state fields. Same opaque token with a different kind invalidates the active request. Raw prompt/selection/budget remain unchanged.
- Dispatch video only through optional MultimodalVideoBridge. Legacy image-only bridge rejects video with BACKEND_UNAVAILABLE; never falls back to image. Reuse the current pending/cancel/terminal/close machinery.
- Optional MultimodalVideoDemoActions extends existing actions; old image actions conformers remain valid. UI displays the selected media kind and experimental video caveat. No playback or frame controls.
- Platform hosts own independent fresh image/video stores and picker entrypoints. Tokens must not cross provider registries. One selected token and one picker ticket across both kinds. Clear/replace/close retires selection; operation lease survives native drain.
- Staging must reject empty/nonregular/symlink and oversized input, remove partial copies on error, and never interpret opaque references as paths. Reuse existing ownership helpers where possible. Picker cancellation leaves prior selection unchanged; stale callbacks do nothing. No automatic inference on selection/foreground.
- Keep old host initializers/image entrypoints usable; optional video configuration defaults absent. Factories wire the video provider only when configured.

## Alternatives

Independent stores are selected over a typed single registry: less disturbance to proven lease ownership and natural cross-kind isolation. A second video VM is rejected because it would create competing lifecycle slots.

## Boundaries

Work only in foundation feat/sdk-foundation. No downloads, new dependencies, model/media reads, signing changes, remote pushes or main merge. Preserve original dirty project.pbxproj and untracked face-identity.md byte-for-byte and un-staged. No inference capability/profile claim from backend availability. Controlled fixtures and lifecycle tests are not model device/picker evidence. Roadmap remains 5/12; P9 active; independent review pending.

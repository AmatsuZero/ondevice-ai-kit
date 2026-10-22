# Single-image Demo Screen/UI Design

## Intent and boundary

Add a shared Compose screen for the existing `MultimodalViewModel` and an opt-in navigation entry. The screen renders opaque input display name, raw prompt, token budget, strict backend selection, availability, phase, cumulative update, final identity and typed failures. The default Face/Speech/Video surface remains unchanged because its optional multimodal model is null unless an opt-in caller supplies one.

This package does not read media, own input leases, create a model, add a native host, or change the protected iOS project. `UnavailableMultimodalBridge` remains the default and is rendered as unavailable. iOS15/macOS12/API26 and existing exported entry labels remain unchanged.

## Design

`MultimodalScreen(model: MultimodalViewModel)` follows the existing `SpeechScreen` and `VideoScreen` Compose patterns. It refreshes backends once, exposes default/explicit selection without trimming the model's raw selection input, and binds text fields directly to `setPrompt`/`setTokenBudget`. The image control is display-only in this package: an opt-in host may call `selectImage(reference, displayName)`; no picker callback is introduced.

`FaceScreen` gains an optional `multimodal` model and `multimodalVisible` state. A `multimodal-tab` is rendered only when the model is supplied. Selecting it leaves Speech/Video and shows `MultimodalScreen`; leaving the page calls `leavePage`. Existing callers omit the argument. iOS gets a separate `multimodalMainViewController` entry that is not used by the default app. Android keeps its default call unchanged and can opt in later by passing the model.

Controls use stable test tags: `multimodal-tab`, `multimodal-phase`, `multimodal-input`, `multimodal-prompt`, `multimodal-budget`, `multimodal-generate`, `multimodal-cancel`, `multimodal-update`, `multimodal-output`, and `multimodal-failure`. Output renders backend/model/runtime identity; failure renders code and optional reason without exposing input paths.

## Validation

Add an Android instrumentation harness that renders the actual shared Compose screen and routing with a real `MultimodalViewModel`, both the real unavailable bridge and a controlled bridge callback boundary. Assert accessibility-visible controls, edits, cumulative updates, final identity, cancellation/drain, leaving and reentry; fake output is UI fixture evidence only. No Compose test dependency is added. Compile the shared simulator framework and Android app. Run the existing shared tests and tools suite. Generated iOS source/header compilation checks the new Kotlin entry without modifying `project.pbxproj`; physical UI and real model inference remain unverified.

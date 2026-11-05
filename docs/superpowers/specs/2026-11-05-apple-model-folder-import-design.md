# Fixed Apple model folder import

Intent: complete the enhanced Demo's ordinary setup flow. A person with the already
authorized fixed SmolVLM2 folder can select it through Files and retry preparation
after a missing/invalid folder, without a computer copying files into the sandbox.

Approach: reuse `MultimodalFilePicker` for a single `.folder` selection and the
existing `MultimodalHostPreparation` / ModelStore / MLXVLM factory for preparation.
Expose one native SwiftUI setup button only in the compile-time MLX variant.
The importer accepts the canonical 14-file model; it is not a model-descriptor chooser.
Installed valid models remain reusable without the original import directory.

Alternatives considered: extend the shared ZIP importer (requires a new Apple ZIP
pipeline and shared ABI work), or retain computer-only staging (leaves the ordinary
setup gap). The existing folder pattern is the agent's choice within the requested
Demo/provider work. Standing instructions request autonomous solo implementation.

Preparation never starts inference or downloads. Security-scoped folder access lasts
through the asynchronous install/build attempt and is released on every exit. Failure
retains the unavailable host and permits an explicit retry; cancellation of the picker
does not prepare anything. Preparation remains serialized; after successful publication,
further starts are ignored and the import button is disabled. Closing the owner cancels
late publication and closes any late host. Existing generation and lifecycle owners stay.

Base/non-MLX variants, production lock, protected paths, model identities and policy
rejection remain unchanged. No new assets, Photos writes, recording or UI-tool retries.
Validation: real host-preparation retry/once-only tests, real UIKit picker callback tests,
existing ModelStore integrity/lease tests, full bridge suite, generator/tools suite and
combined device-target typecheck/build as needed. Physical picker acceptance remains
unverified while UI automation is unavailable.

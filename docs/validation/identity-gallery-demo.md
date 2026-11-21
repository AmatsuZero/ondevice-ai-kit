# Identity Demo candidate and gallery controls

2026-11-04 current-candidate update: frozen three-opt-in APK `bd89bdbf…16bd7`
also completed ordinary system NASA-image selection, one-template enrollment,
uncalibrated verification/gallery rejection, candidate deletion to zero and
whole-session disable in MainFlow emulator PID5514. No calibrated recognition
or PAD result was supplied. The same APK separately completed LM generation;
joint evidence checks297/297 passed, independent review returned429 without a
conclusion. See [current-package scope and cleanup evidence](android-mainflow-emulator.md#current-candidate-lm-and-identity-execution).

2026-11-04; work started 2026-11-03. This functional slice continues the
[Android real Identity prototype](android-opencv-identity-demo.md) and
[exact index/query](android-exact-identity-index.md).

The shared Identity page now supports session-local numbered candidates,
multiple enrolled templates per candidate, candidate selection/deletion and
gallery publication/deletion. Switching candidates clears the selected probe
and restores only that candidate's latest template for verification. The same
bridge stays open. UI state contains opaque references/counts, not images,
vectors, personal names or business IDs. These controls appear only when the
host supplies ownership and gallery options; existing iOS/unconfigured hosts
keep their prior behavior.

Gallery publication serializes a fresh builder, deduplicated template additions,
publication and best-effort builder close. Immediate and asynchronous callbacks
use the bridge's existing serial UI callback contract. A trampoline prevents
recursive synchronous callback growth; duplicate and late callbacks cannot
deliver another terminal result. Cancel closes an admitted builder, including
one delivered late. A published gallery must be explicitly deleted before
publishing another, avoiding hidden earlier lineages.

Deleting a candidate first deletes its current gallery and waits for physical
purge, then deletes the candidate and observes its purge status. Pending
purge remains visible; refresh resumes the requested deletion. `PURGE_FAILED`
is terminal in the current native stores: the UI explicitly reports that this
session cannot recover, keeps candidate deletion blocked, and offers no ineffective
refresh action, even after another input is selected. Real failed-purge recovery
is explicitly deferred under the user's corner-case direction. Other candidates
and their templates remain. Session teardown closes consent/admission, revokes
the entire local grant and waits for all covered templates to purge, including
candidates created before a cancelled UI reply. The internal session returns
the native revocation receipt for durable cleanup verification.

The OpenCV host supplies **UNVERIFIED** identification and verification policies.
Its gallery publication is deliberately rejected with `THRESHOLD_UNVERIFIED`.
No threshold, calibrated dataset, real recognition outcome or PAD was fabricated.
The functioning publication path is exercised with explicit synthetic common-test
policies; it is not real-model recognition acceptance.

## Verification

Ignored evidence root: `artifacts/verification/identity-gallery-demo-20261103/`.
Initial RED logs record absent callback-helper/ViewModel symbols and the missing
session receipt return. A test type-inference error was corrected before the
separate signature-only RED run. One independent Medium finding identified
candidate deletion hiding an undeleted gallery. Three regression tests failed
first, then passed after gallery purge ordering, refresh continuation and the
explicit republish guard were added.

Final shared simulator tests **80/80**, default
Demo **48/48**, three-option Demo **53/53**, all with zero failures. Default and
combined APK/test APK/lint and shared iOS arm64 compilation passed. The unchanged
Android Identity suite retains its earlier **327/327** evidence. Existing
Kotlin/Native cache/archive and Android deprecation warnings remain recorded.
The helper's builder close remains best-effort and callbacks require the serial
UI executor contract; this is not a cross-thread callback API.

TECNO KM9/API35 final-source run `6848bc69-ed51-4e92-b30f-d5277d98e090` passed
real YuNet/SFace extraction and registration for two candidate records, single
candidate purge, unverified-policy/gallery rejection, idempotent session close,
model lease release and store reopen proving the grant-wide purge receipt is
`PURGED` and the remaining candidate is revoked. Native tests reuse only the
already approved hash-bound NASA portrait. This tests two records of one image,
not discrimination between two people or calibration.

- Installed target APK SHA-256:
  `405d64e1bf67d1879ff96db386282066e7cda612a2b77134377e070feec082c0`.
- Installed test APK SHA-256:
  `7b9e47ce9a9b9fe51c673c002697a16c3fe4dd6ee382f70c3a32c3c9493a621d`.
- `report-guard-final.json` SHA-256:
  `8a2122c77c6a1393c3587f6f218b292a10a636507583e38c4fb7fe9023bf92a1`.

Both installations completed before instrumentation; installed `base.apk`
hashes matched immutable `apks-guard-final/` snapshots. Earlier `apks/`,
`apks-final/`, `apks-terminal-final/` and reports are retained as prior evidence.
Final XML counts are in `guard-final-tests.json`; original RED/GREEN/build/device
logs are retained. Bounded independent review first requested gallery deletion
ordering, which was fixed and verified with three failing-then-passing tests.
Re-review reported **COMMENT**, identifying the terminal purge-recovery gap.
That recovery mechanism is explicitly deferred; the misleading retry wording
and action were removed with an additional RED→GREEN state regression. This
is not whole-branch approval or a claim that terminal recovery is implemented.

Actual picker/touch UI acceptance remains unverified: the device preflight read
`mWakefulness=Asleep` and keyguard `showing=true`; this slice does not unlock it,
launch the Activity or open the camera. Apple real Identity, real calibrated
1:1/1:N outcomes, PAD, process-death roster recovery and sustained-resource work
remain open. No push, merge or release is implied.

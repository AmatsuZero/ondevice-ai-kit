# Identity Demo candidate and gallery controls

Continue the authorized functionality-first roadmap. Use the existing shared
IdentityViewModel/IdentityBridge and optional Android OpenCV session, with no
new runtime dependencies, biometric samples or guessed recognition policies.

Add session-local numbered candidate rows containing only opaque references and
template counts. A user can explicitly add a candidate, select it, register
multiple images, delete that candidate, publish a new gallery from all retained
templates, and delete the current gallery. Switching candidates clears the
selected probe so another person's image cannot silently become enrollment
input. Keep per-candidate latest verification template. Display deletion's
actual status and gallery template counts. No business names/IDs are recorded.

Chosen approach: extend the existing ViewModel and optional configuration with
ownership/gallery options; a focused callback publication helper runs bridge
builder/add/publish/close sequentially. Replacing the whole host ViewModel would
close the live bridge; a separate Android-only enrollment flow duplicates shared
state and blocks future iOS use. Host reconfiguration still resets the entire
session, while ordinary selection stays inside the same shared ViewModel.

Every publication creates a new immutable gallery, not an in-place update.
Explicitly delete the current gallery before publishing another. Candidate
deletion first deletes/awaits the current gallery, then removes the candidate;
pending purge can be refreshed to resume the sequence.
The existing backend selection and unverified-policy errors remain visible.
OpenCV gets UNVERIFIED gallery options, so its publication attempt is refused
until calibrated host policy/data exists. Synthetic common tests exercise the
working gallery flow without pretending to be real SFace recognition.

Session teardown first closes admission/consent and revokes the entire local
grant, covering candidates created even if a UI callback was cancelled. Wait
for grant-scoped template purge, then close engines/backends/stores. Per-candidate
deletion remains independent. The real-device runner uses the already-approved
NASA portrait only for two local candidate records, then checks the removed
candidate cannot be reused and grant-wide teardown purges the remaining work.
This is flow validation, not two identities, accuracy, calibration or PAD.

No persistent roster recovery or new calibration/data-collection UI in this
slice. Actual picker/touch acceptance remains distinct from headless tests.

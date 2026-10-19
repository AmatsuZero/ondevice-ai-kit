# P10 KMP identity bridge integration

Date: 2026-10-19
Status: common-contract, Demo-state and native Android/iOS adapter slices verified; Task 11 remains in progress.

## Delivered prerequisite

- Ordinary Kotlin classes export full opaque native canonical grants, candidate/template/gallery references, model bindings and policies to Objective-C/Swift. Wrappers do not confer authorization; platform decoding and original native store admission must validate them.
- Enrollment, verification, identification and PAD remain separate option/result types. Completed results retain native quality, policy, backend/model, input/options/preprocess, session/run, timing and gallery grouping metadata. No business subject or vector fields are introduced.
- Explicit/default backend selection is carried at capability admission. Ownership authorization is distinct from the biometric grant. Identity/index deletion receipts and revocation receipts have distinct routes.
- The contract includes builder close and the entire liveness session lifecycle: start, frame admission, event polling, finish, cancel, result, proof consumption and close.
- Demo state invalidates old generations before cancellation, accepts one terminal callback, handles synchronous completion without retaining a completed task, and binds liveness events to their session. Immediate event delivery uses an iterative trampoline. Late-created sessions are closed.
- The explicit unavailable configuration produces no biometric result; after close it rejects admission with stable CLOSED.

## Fresh verification

Actual RED evidence:

- `/tmp/odai-task11-vm-red.log`: prior draft failed repeated terminal/synchronous task retention tests.
- `/tmp/odai-task11-contract-red.log`: new common contract missing-API failure.
- `/tmp/odai-task11-vm-red2.log`: liveness event consumption and session cleanup failed before their fixes.

Final command:

```sh
rtk proxy ./gradlew --rerun-tasks :kmp-binding:iosSimulatorArm64Test :demo:shared:iosSimulatorArm64Test :kmp-binding:linkDebugFrameworkIosSimulatorArm64 :demo:androidApp:assembleDebug
rtk proxy env CLANG_MODULE_CACHE_PATH=/tmp/odai-task11-clang-cache sh scripts/check-swift-bridge.sh
```

`/tmp/odai-task11-final-common.log`: BUILD SUCCESSFUL, 160 tasks executed. Inspected XML: binding 5/5, Demo 14/14 (identity 9, detection 5), zero failures/errors/skips. Swift consumer typecheck exited 0 and invokes every exported bridge method plus session controls. Android Demo APK assembly passed. Existing CameraX setTargetResolution deprecation and third-party native stripping notices remain; no new common-source compiler warning.

## Remaining acceptance work

- Native adapter slices are verified below; platform host configuration and UI wiring remain in progress.
- Platform host wiring and separate enrollment/verify/identify/liveness controls, including input ownership and host-supplied authorization/configuration.
- Full unsigned iOS Demo build, device/consumer/package gates, and final branch review.
- Production model, PAD backend, authorized datasets and calibration remain Tasks 9/10 blocked-unverified. Synthetic contract/state tests do not validate biometric accuracy or authorize a default decision threshold.

The protected signing project and original `docs/validation/face-identity.md` draft remain unchanged. No Task 11 or roadmap completion is claimed by this prerequisite.

## Native policy codec prerequisite

Both native Identity modules now expose `ThresholdPolicy.canonicalJSON/decodeJSON` and `PADPolicy.canonicalJSON/decodeJSON`. Version 1 serializes finite Double values as 16 lowercase IEEE-754 binary64 hex digits, preserving calibration bits while retaining the existing integer-only authorization/reference parser. Complete embedding/model bindings are nested objects. Unknown/missing/duplicate keys, numeric coercion, out-of-range capture/range integers, non-finite bits and duplicate attack types are rejected. Gallery persistence is unchanged.

The shared `fixtures/contracts/identity-policies.json` supplies synthetic identification and active categorical PAD policies; both platforms decode and re-encode the exact same canonical bytes. Unverified policies retain null calibration fields and continue to reject `requireVerified`. A serialized `verified` declaration establishes neither calibration provenance nor production approval.

RED logs: `/tmp/odai-policy-codec-android-red.log`, `/tmp/odai-policy-codec-swift-red.log` (missing public API). GREEN: Apple identity full 312/312 in `/tmp/odai-policy-codec-apple-full.log`; Android identity full 310/310, zero failures/errors/skips, plus lintDebug in `/tmp/odai-policy-codec-android-full.log`. Fresh codec tests passed 4/4 per platform; Apple log `/tmp/odai-policy-codec-swift-fresh.log`. The Android codec count is four (supersedes an earlier progress message saying three). Existing Swift async-wait and sandbox cache warnings remain. This is a native-mapping prerequisite, not Task 11 completion or algorithm validation.

## Android native adapter slice

`AndroidIdentityBridge` now calls the real native `IdentityStore`, `IndexStore`, verification/identification engines and optional liveness engine. It borrows host stores/engines/backends, resolves host-owned probe references, dispatches native work off the UI thread, and serializes callbacks through the Android main executor (or host-supplied serial UI executor). Explicit backend selection is forwarded for every capability, including builder creation; missing/unavailable selections do not fall back. The adapter maps all native result metadata, distinct receipt routes, deadline/stop-aware deletion observations, builder resources and full liveness session controls. PAD has no native preprocessing revision, so that common metadata field remains null.

Challenges now use full native canonical JSON on both platforms, preserving nonce/session/purpose/epoch/monotonic times. Decoding confers no issuer authority: the native session still compares the complete challenge to its issued value. This replaces the initial in-memory handle mapping. The native stop signal exposes its existing delivery-drain wait so callbacks cannot overtake an active native stop handler.

TDD evidence:

- `/tmp/odai-task11-native-bridge-red.log`: AndroidIdentityBridge missing API; native test dependency graph compiled without a cycle. Test-only generic inference/JUnit return-type errors were corrected separately and are not behavioral RED evidence.
- `/tmp/odai-challenge-codec-{android,swift}-red.log`: missing challenge codec API; both codec suites GREEN 5/5.
- `/tmp/odai-task11-native-challenge-red.log`: full challenge decode failed against the initial handle mapping; GREEN after native canonical mapping.
- `/tmp/odai-task11-stop-drain-red.log`: terminal callback overtook a deliberately blocked real native stop handler; GREEN after waiting for delivery drain.

Final native integration tests 8/8 cover encrypted Store enrollment/verification/gallery/identification/deletion/revocation, no fallback, malformed grant rejection, UI callback dispatch, real cancellation, stop-handler drain, borrowed Store lifetime, active challenge/proof, nonce tampering, revocation winning over later cancellation, and cancellable deletion waits. All backend vectors and calibration metadata in these tests are synthetic, module-internal fixtures.

Final gates: `/tmp/odai-task11-android-bridge-final.log` BUILD SUCCESSFUL (304 tasks): Android identity 319/319, zero failures/errors/skips; lintDebug; Android Demo APK assembly; binding simulator 5/5 and Demo shared simulator 14/14. `/tmp/odai-task11-android-bridge-final2.log` reconfirmed the final Identity/lint/Android bridge compile gate. `/tmp/odai-task11-native-apple-final.log` passed all Apple tests 371/371 (Identity313, Speech31, ModelStore13, Face9, Core5), exit0. Protected signing project and user validation draft hashes are unchanged. Root scoped self-review only; the final independent whole-branch review remains pending.

Task 11 remains in progress: the iOS native bridge, host UI/configuration wiring, unsigned iOS Demo and live device acceptance are not delivered by this Android slice. Tasks 9/10 production-model/PAD/calibration gates remain blocked-unverified; no biometric-accuracy claim or production threshold is introduced.

Final boundary audit also reproduced native exception leakage from synchronous `submitFrame` for malformed challenge JSON (`/tmp/odai-task11-malformed-frame-red.log`). The adapter now rejects a malformed frame with false before native admission; a structurally valid but altered challenge still reaches native session validation and produces INVALID_CHALLENGE. Final frozen Android suite/lint/APK log `/tmp/odai-task11-android-bridge-frozen.log` passed 319/319 with zero failures/errors/skips; bridge tests remain 8/8. No Apple source changed after the full 371/371 gate.


## iOS native adapter slice

`kmp-binding/apple/IOSIdentityBridge.swift` implements the exported KMP `IdentityBridge` protocol against the real Swift `IdentityStore`, `IndexStore`, verification/identification engines and optional liveness engine. The host retains its stores, engines and backend registry, resolves opaque probe references, and must close the adapter before disposing those borrowed resources. Only bridge tasks/builders/sessions are closed by the adapter. Callbacks use the main queue by default; a custom queue must be the host's serial UI callback queue.

The adapter stays outside the native Apple Swift Package, preserving its independence from KMP/Compose. `scripts/generate-identity-bridge-tests.rb` creates an ignored standalone XCTest project linking the actual exported `ODAIKitBinding.framework` and local Apple SDK products. It refuses to overwrite existing output. No Demo project/signing modification is needed for this integration gate. See `probes/ios-identity-bridge/README.md` for reproduction.

Observed RED evidence:

- `/tmp/odai-ios-identity-bridge-red-clean.log`: initial missing adapter API after the native/KMP XCTest dependency graph was established.
- `/tmp/odai-ios-identity-bridge-behavior-red.log`: native cancellation delivery blocking UI/terminal drain and unavailable gallery backend mismatch (4 tests, 2 failures).
- `/tmp/odai-ios-identity-bridge-boundaries-red.log`: bridge close lost CLOSED semantics, event waiter cancellation did not reach native close, and malformed challenge admission poisoned the session.
- `/tmp/odai-ios-identity-bridge-close-red.log`: repeated builder close crashed on force unwrap; returned sessions bypassed closed bridge admission. The crash triggered Xcode's diagnostic-collection timeout; the subsequent successful test runs are the acceptance evidence.
- `/tmp/odai-ios-identity-bridge-selection-red.log`: an unknown Swift implementation of the exported selection protocol silently chose the default backend. Unknown selections now return UNSUPPORTED_OPTION before backend work.

The Swift stop signal now exposes its existing asynchronous delivery-drain wait. Enrollment forwards the outer stop reason, preserving CLOSED and AUTHORIZATION_REVOKED rather than rewriting every stop as caller cancellation. Native operations and stop delivery remain off the UI thread. A null deletion duration invokes the native current-status overload; it does not invent a 1ns wait. Unit callbacks return the actual Kotlin Unit singleton through `IdentityBridgeValues`.

Fresh verification:

- `/tmp/odai-ios-identity-bridge-final.log`: simulator XCTest 10/10, zero failures. Covers native encrypted enrollment/verification/gallery/identification and metadata, deletion/revocation routes, active challenge/single-use proof, malformed admission, cancellation/drain, borrowed store lifetime, repeated builder close, closed session/bridge admission, unknown/missing/unavailable backends, and unverified policy rejection.
- `/tmp/odai-ios-identity-bridge-apple-full.log`: full native Apple 371/371 (Identity313, Speech31, ModelStore13, Face9, Core5), exit0.
- `/tmp/odai-ios-identity-bridge-kmp-final.log`: BUILD SUCCESSFUL; inspected XML binding6/6 and Demo14/14, zero failures/errors/skips; debug simulator framework exported successfully.
- Swift formatter strict lint, Ruby syntax, fresh standalone-project generation, overwrite refusal, and `git diff --check` passed. The local Ruby environment emits unrelated unbuilt-gem extension notices; no gem installation or environment mutation was performed.

Xcode's current XCTest libraries report simulator deployment-version link warnings (15 target vs 17 XCTest support), plus skipped AppIntents metadata extraction. These tests ran on the available iOS 27.1 simulator, not an iOS 15 device. No production model, PAD algorithm, accuracy/calibration or minimum-device gate is unlocked by synthetic fixtures. No independent whole-branch review is claimed here. Task 11 remains active for host controls/configuration and the full Demo gate; Task 12 distribution and Tasks 9/10 production gates remain outstanding.


## Demo selection preflight correction

Before host UI wiring, a real common regression (`/tmp/odai-demo-explicit-backend-red.log`, 16 tests, 1 failure) showed that the ViewModel's default Identity availability preflight prevented all explicitly selected capability calls. This also conflated identity availability with the independent PAD engine. The UI now uses availability for display; actual method admission, backend choice and unavailable errors come from the native bridge. Unconfigured `UnavailableIdentityBridge` still reports UNVERIFIED synchronously and produces no result. Explicit enroll/verify/identify/PAD forwarding is covered with an unavailable default declaration.

`/tmp/odai-demo-explicit-backend-green.log`: BUILD SUCCESSFUL; inspected XML Demo16/16 and binding6/6, zero failures/errors/skips; Android Demo APK assembly passed. A test generic-inference compile error was corrected before observing the behavioral RED and is not counted as failure evidence. This correction does not deliver host controls or unblock production backends.

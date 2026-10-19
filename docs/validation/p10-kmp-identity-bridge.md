# P10 KMP identity bridge integration

Date: 2026-10-19
Status: common-contract and Demo-state prerequisite verified; Task 11 remains in progress.

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

- Real Android and iOS adapters around native stores, clients and sessions, with options/results/error/ref/proof mapping tests.
- Platform host wiring and separate enrollment/verify/identify/liveness controls, including input ownership and host-supplied authorization/configuration.
- Full unsigned iOS Demo build, device/consumer/package gates, and final branch review.
- Production model, PAD backend, authorized datasets and calibration remain Tasks 9/10 blocked-unverified. Synthetic contract/state tests do not validate biometric accuracy or authorize a default decision threshold.

The protected signing project and original `docs/validation/face-identity.md` draft remain unchanged. No Task 11 or roadmap completion is claimed by this prerequisite.

## Native policy codec prerequisite

Both native Identity modules now expose `ThresholdPolicy.canonicalJSON/decodeJSON` and `PADPolicy.canonicalJSON/decodeJSON`. Version 1 serializes finite Double values as 16 lowercase IEEE-754 binary64 hex digits, preserving calibration bits while retaining the existing integer-only authorization/reference parser. Complete embedding/model bindings are nested objects. Unknown/missing/duplicate keys, numeric coercion, out-of-range capture/range integers, non-finite bits and duplicate attack types are rejected. Gallery persistence is unchanged.

The shared `fixtures/contracts/identity-policies.json` supplies synthetic identification and active categorical PAD policies; both platforms decode and re-encode the exact same canonical bytes. Unverified policies retain null calibration fields and continue to reject `requireVerified`. A serialized `verified` declaration establishes neither calibration provenance nor production approval.

RED logs: `/tmp/odai-policy-codec-android-red.log`, `/tmp/odai-policy-codec-swift-red.log` (missing public API). GREEN: Apple identity full 312/312 in `/tmp/odai-policy-codec-apple-full.log`; Android identity full 310/310, zero failures/errors/skips, plus lintDebug in `/tmp/odai-policy-codec-android-full.log`. Fresh codec tests passed 4/4 per platform; Apple log `/tmp/odai-policy-codec-swift-fresh.log`. The Android codec count is four (supersedes an earlier progress message saying three). Existing Swift async-wait and sandbox cache warnings remain. This is a native-mapping prerequisite, not Task 11 completion or algorithm validation.

# Apple OpenCV Identity — local prototype validation

2026-11-04. Worktree `.worktrees/foundation`, branch `feat/sdk-foundation`, implementation base `a11c8c8b40f4c6aeb158ba34a6f71ec63a23b008`. Evidence is local/ignored under `artifacts/verification/apple-opencv-identity-20261104/`. No push, merge or release.

The optional `apple-opencv/` package adds real CPU decode → YuNet detection → official SFace alignment → 128-dimensional normalized embedding. The default Apple package remains independent of OpenCV. The optional device Demo imports a fixed model folder, requires explicit 30-minute consent, and routes the existing shared multi-candidate/template/gallery controls through `IOSIdentityBridge`.

## Fixed inputs and runtime

| Input | Identity |
| --- | --- |
| OpenCV | Official 4.12.0 iOS archive, 221,312,528 B; SHA-256 `86b42c9f141cd9169b91be2fc380b0e556a88a95c98ccc8e7aef8349ab74cf70` |
| Zoo revision | `47534e27c9851bb1128ccc0102f1145e27f23f98` |
| YuNet | 232,589 B; `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| SFace | 38,696,353 B; `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` |
| Separately approved NASA astronaut image | `88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5` |
| Backend/space | `opencv.identity.apple` / `opencv-4.12-yunet640-sface112-rgb-l2-v1`; distinct Apple space, no cross-platform compatibility assertion |

The official binary is a static archive inside a legacy versioned framework. The preparation script verifies the complete ZIP before extraction, preserves upstream headers and archive members, thins arm64 device / x86_64 simulator and creates a **static-library XCFramework**. This avoids incorrect dynamic embedding. Earlier versioned/flattened framework build/installer failures remain in evidence. All inspected objects report minOS 9.0; the package contract remains iOS 15. No arm64 simulator slice is supplied. Runtime/model notices are retained; production redistribution is not approved by this local test.

## Verification

| Gate | Result / evidence |
| --- | --- |
| Model/input/normalization RED → GREEN | Missing descriptor and input validator failures, then 3/3 host contracts. Final `host-final.log`; contracts-only mode explicitly excludes native runtime |
| Native backend RED | `runtime-red.log`: absent `OpenCvIdentity`; harness clock/async-autoclosure errors corrected first |
| Native test compilation | iOS device test build passed; final static-library x86_64 simulator test build passed (`x86-build.log`). Simulator tests were not executed |
| Session / cleanup RED | `session-red.log` missing session; `cleanup-red.log` missing durable cleanup guard |
| Host import RED | `demo-validation-red.log` missing shared `installModels(from:)` path; implemented and build passed |
| Default Apple SDK | **481/481**, including Identity 322/322, Speech 76/76, Face 9/9 (`default-apple.log`) |
| Physical KMP Demo framework | `:demo:shared:linkDebugFrameworkIosArm64` passed (`demo-kmp-device.log`) |
| Optional and flag-disabled Demo builds | Device builds passed; `demo-build.log`, `default-demo-build.log`; later validation build includes the cleanup fix |
| Ruby/Python / whitespace | Generator syntax and preparation script compilation passed; final diff check recorded before commit |
| Protected work | All nine protected hashes match the existing baseline; none staged |

Physical device: iPhone16 Pro Max / iPhone17,2, UDID `00008140-0008703436D3001C`. Existing local development signing only; no signing asset change. Device was available without unlocking; no camera or microphone was used.

Final native/session probe **21/21**: `probe-final-report.json`, runId `7FF35B5B-D64B-4D95-88D2-D8FB879882AC`. Covers approved input hash, exactly one usable face, normalized 128-vector, cancellation rejection, blank rejection, encrypted enrollment, candidate and full-grant purge, durable purge on reopen, two-candidate session enrollment, idempotent session cleanup, revoked grant after reopen, policy rejection, durable cleanup marker and model pin/release. This reuses the same approved image for both candidate records; it is not evidence for two distinct people or accuracy.

Actual Demo host/bridge/shared ViewModel flow **15/15**: `demo-validation-report.json`, runId `90CAC894-C3E4-4BE9-A163-EA041255590C`. It exercises the production import path, explicit-consent boundary, opaque probe selection, two candidates with actual SFace enrollment, verification/gallery policy rejection, full-grant delete/reset and persistent cleanup blocking on a newly constructed host. This is a programmatic app flow, not touch/picker acceptance. Initial generated-host startup failed Compose's required `CADisableMinimumFrameDurationOnPhone` check; the generator now references the existing Demo Info.plist and the built boolean was verified before the successful run. Earlier failures remain in `demo-console*.log`.

The ordinary Demo was rebuilt, installed and launched with no validation argument after the successful run (`demo-normal-build.log`, `demo-normal-{install,launch}.json`). Its binary contains no validation entry string. App binary inventories are in `probe-app-hashes.json`, `demo-validation-app-hashes.json` and `demo-normal-app-hashes.json`; install/launch JSON files identify the local device runs. The generated apps contain no portrait/model assets and have no file-sharing overrides. Test inputs are transferred separately into each project's sandbox. Initial native `invalidSource` came from download range fragments in the source model directory; staging exactly the two manifest files resolved it without weakening validation.

## Behavior and review boundaries

Full-session cleanup stops the bridge, revokes the entire grant, drains engines/native work, waits for purge and closes stores. A durable marker is committed before session activation and cleared only after successful cleanup. Failed cleanup retains the session and blocks activation; a pending marker after restart reports incomplete cleanup. Automatic failed-purge/interrupted-session recovery is deferred, with encrypted artifacts retained rather than claiming deletion.

The independent reviewer supplied two actionable comments (validation import method and discarded cleanup failures); both were addressed with RED → GREEN checks. The service then failed with **429** before a final verdict. No independent APPROVE or completed whole-branch review is claimed.

Recognition and identification policies remain **UNVERIFIED**, and PAD is absent. Actual identity match/1:N outcomes require separately authorized data and calibrated policies. Natural-image quality, multiple-person accuracy, real touch/picker acceptance, minimum-system execution, arm64 simulator runtime, rapid native interruption, long resource runs, distribution and release remain unverified. These are separate from the working local model/enrollment/delete flow. See [package usage](../../apple-opencv/README.md).

# Apple Identity Files Input Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Autonomous local execution already authorized.

**Goal:** Select a local authorized image from Files for real Apple Identity registration.

**Architecture:** Reuse the optional host document picker and DemoController's ImageIO/useImage pipeline; shared contracts and protected project remain untouched.

**Tech Stack:** Swift, UIKit, ImageIO; existing OpenCV package.

**Spec:** ../specs/2026-11-04-identity-files-input-design.md

## Global Constraints

No Photos writes, new samples/models/dependencies, policy promotion, protected-file edits or remote operations. Encoded file limit 10 MiB; existing transformed thumbnail max 2048. Programmatic host validation is separate from touch/provider acceptance. Preserve evidence.

## Review Focus

Security scope spans decoding; no main-thread file decode; cancellation/close cannot publish late; failed input clears previous probe; folder/image picker modes cannot cross-install. Tests exercise malformed input and real approved image; source review covers system picker wiring and scope lifetime.

### Task 1: Files image input and real host regression

Files: modify demo/iosApp/IdentityOpenCvHost.swift, DemoController.swift, probes/ios-opencv-identity/IdentityDemoChecks.swift; document evidence/handoff.

Interfaces: host `selectImageFile()` presents one `.image` document picker; `loadImage(from: URL)` executes the same async handler used by its delegate. Reuse `DemoController.decodePhoto(_:)` and `useImage(_:)`.

- [ ] Extend real host probe to load the approved image through `loadImage(from:)`, require opaque probe and real enrollment, then require invalid file selection clears old input and permits retry.
- [ ] Build validation app: expected RED, missing `loadImage(from:)`.
- [ ] Implement explicit picker mode, bounded off-main security-scoped ImageIO decoding and guarded publication; add Files button enabled for an active session.
- [ ] Rebuild/run host probe: expected all checks pass; build ordinary app and flag-disabled Demo. Verify nine protected hashes and diff whitespace; bounded independent review.
- [ ] Record exact device/build boundaries and commit only owned files locally.

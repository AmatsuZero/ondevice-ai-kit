# Apple Identity Files input

Continue the authorized functionality-first Demo work without writing assets to Photos. Add an explicit Files image picker to the optional Apple OpenCV host. Keep the existing Photos action and the shared SDK contracts. Use the existing ImageIO thumbnail transform (upright, at most 2048 pixels per axis), then `DemoController.useImage` so detection and opaque Identity inputs share one image.

Approaches: writing Photos assets conflicts with the user's constraint; replacing the shared picker API expands both-platform scope. A local optional-host Files action reuses its document-picker delegate and the production image path with a small diff.

Only a selected, readable, bounded image may replace the current Identity probe. Opening/failed import clears the previous probe; cancellation does not enroll anything. Hold security-scoped access through off-main decoding, check cancellation before publication, and block publication after host close/session teardown. No raw paths/errors/images in logs, no new sample/model/dependency, no automatic enrollment or policy promotion. The user opted out of repeated questions and authorized safe local implementation.

Verify through the existing real-device host→bridge→ViewModel probe using the approved sandbox portrait: Files-handler input becomes an opaque probe, real SFace enrollment succeeds, and an unreadable selection clears old input. This is programmatic delegate/handler validation, not system Files picker/touch acceptance. Preserve nine protected paths and all ignored evidence; no push/merge/release.

# Single-image native host — author final review

Separate author review over 2c2aaef..854d256 plus Apple Task2 diff and checkpoint docs, against spec/plan Review Focus. Explicit inline/no-subagent instruction; not independent APPROVE. No Critical/Important remaining, no new deferred Minor. No merge/release decision.

- latest/once-only tickets are consumed before nil/import handling; replacement callbacks cannot import twice. Clear/leave/default background/close null pending tickets. External preservePicker retains only an already active ticket and does not call generate. Tests exercise both stale/duplicate and before-onForeground cases.
- Successful selection copies source, changes VM input then retires prior selected token. Failure leaves prior reference and maps stable INVALID_INPUT without private error text. Existing lease retains bytes through replacement/clear/close. Actual source bytes and retained copy are asserted.
- Existing VM controlsEnabled gates selection; VM owns all generation/cancellation/terminal filtering. Background/foreground do not restart it. Close seals host first, closes bridge via VM only once, and closes store admission immediately while native drain retains lease. Repeated close retries store cleanup only. Controlled pending terminal proves close waits; no claim of real backend integration.
- Default unavailable path uses the real existing bridge. No backend/client/model factory or success payload in production. Tests' drain bridge cannot enter App code. Native host sources do not change old shared/Kotlin Native export labels.
- Generated XCTest host sources are Demo ABI only; product set Core+Multimodal and Binding source set unchanged. No dependencies, picker/URI/shared actions, App navigation/session/signing/protected-project change. No cleanup or remote effects.

Evidence: Android7 new /21 total, APK/lint0error9existing warnings; generator3/3, Apple Demo21/21 (host7+input4+bridge10), Binding10/10, tools101/101, strict Swift lint0. Modern simulator/JVM tests do not establish actual UI, system picker, minimumOS, model, quality/resource or independent acceptance. Historical Video/P6/ASR gates retained.

Declined to judge: none. Ruling is limited package split recorded in progress; next integration must wire page leave into host invalidation and Activity external-picker lifecycle explicitly.

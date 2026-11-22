# CMP detail final native review — PASS

## Verdict

**PASS.** The prior source blockers are closed in the integrated worktree, and the supplied fresh validation is terminal and passing. This review ran no build, test, device, ADB, or cleanup command.

## Closure evidence

1. **Host-accurate file-SR requirements and main-path recovery — closed.**
   - `VideoScreen.kt:37-44` renders `VideoModelSetupDemoActions.videoProfileDescription` directly on the primary input card; Apple text is now only the no-Host fallback.
   - `VideoScreen.kt:102-146` keeps query failures, all-unavailable descriptors and reasons, retry, and model import recovery visible before Advanced. Backend IDs and strict selection remain folded at `:178-208`.
   - Existing `VideoViewModel` process/cancel/output-cleanup/release/record lifecycle actions are retained.

2. **Preview geometry and locator ownership — closed.**
   - Playback now sits outside the vertical scroll (`UnifiedDemoScreen.kt:365-372`). Its finite `BoxWithConstraints` computes an actual 16:9 fit (`:399-415`), while only controls scroll (`:433-486`).
   - The CMP wrapper is uniquely `playback-preview-container`; the native renderer retains the only `playback-preview` identity. The loaded-video test asserts exactly one native locator and a finite 16:9 frame.
   - Empty-state geometry correctly targets the wrapper rather than the intentionally hidden MTK view; production renderer visibility was not changed just to satisfy a locator assertion.

3. **Header geometric center and collision safety — closed.**
   - `PageHeader` is a measured `Layout` (`UnifiedDemoScreen.kt:533-592`): it measures the natural back width, reserves it symmetrically on both sides of the title, limits title width accordingly, and places title at the full header midpoint. It retains a 48dp back target and 52dp minimum height.
   - Tests check whole-screen title centering, finite in-bounds geometry, and no title/back intersection in portrait and landscape.

4. **Theme correction — source-closed.**
   - `UnifiedDemoTheme.kt:23-38` sets `surfaceContainerHighest` to white and preserves the approved green accents and warm background tokens. Parent visually reviewed the supplied portrait screenshot and found white cards/background coherent.

## Fresh validation supplied by the parent

- `centered-gradle-result.json`: exit 0 after the measured-header change; parent reports shared/Android build, test, and lint gates passed.
- `centered-ui-result.json`: exit 0 in 159.316 seconds.
- `centered-ui.log`: selected iOS UI suite passed **5 tests, 0 failures** in 146.968 seconds. It covers empty portrait/landscape geometry and header center/non-overlap, loaded native unique 16:9 motion in portrait/landscape, EOS replay, modes/pause/background/reentry, and file-SR picker cancel/Advanced flow.
- `geometry-result.json`: Android geometry passed normal portrait, normal landscape, font scale 2, and 375dp with font scale 2. Title center error was 0–0.5px, with no overlap and 16:9 preview geometry; settings restoration matched the original state.
- `functional-result.json`: Android playback functional coverage passed media selection, modes, pause, background, and reentry.
- `large-font-ui-result.json`: exit 0 in 23.331 seconds. The iOS accessibility-large empty-layout test passed center/non-overlap and 16:9 checks in portrait and landscape; the original content-size category was restored.
- The earlier landscape harness failure caused by Activity recreation/route reset was preserved. The succeeding harness re-entered the Video category; no production route behavior was changed to mask that harness condition.
- `protected-after.json` confirms all 19 protected root personal/scheme file SHA-256 values stayed unchanged.

## Evidence boundaries

- The iOS exported-protocol bridge must continue to use a current CMP framework and isolated fresh DerivedData. `docs/agent-pitfalls.md` **“CMP iOS 模拟器：新 framework 与旧 Swift 对象混用”** (lines 1-5) establishes that a new Swift header or compile heading cannot prove regenerated Host objects. The simulator UI suite is bridge/runtime evidence, not physical-device SR-quality or presentation-quality evidence.
- UI tests wait for asynchronous host status instead of treating a tap return as completion, consistent with `docs/agent-pitfalls.md` **“统一实时超分：异步打开完成与 CMP 按钮节点”** (lines 8-12).
- Candidate helper state was not treated as acceptance proof. The review used integrated source plus fresh build/UI receipts. The preview-fit artifact preserves its corrected relative-path Read errors; those helper artifacts do not substitute for validation evidence.

## Remaining validation gaps

- No physical-device run proves real SR quality, presentation timing, performance, thermal behavior, or device safe-area appearance.
- The Android default build's missing file-SR Host/unavailable-device behavior remains a separate platform-state acceptance path. No physical Android device screenshot is in this receipt.
- Activity route reset on Android configuration recreation is preserved behavior, not a production fix in this change. Keyboard occlusion and unavailable-device layouts need separate UI coverage.

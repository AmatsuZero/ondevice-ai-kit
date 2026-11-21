# Single-image Demo native host lifecycle design

## Intent and package boundary

After UI55a425b and input2c2aaef, implement the native host owner on both platforms before actual picker/UI callback integration. The host consumes the existing MultimodalViewModel/typed bridge/input store; it does not reimplement generation coordination. Its injectable selection callback lets synthetic actual-file tests establish replacement, stale result, background and close semantics without reading private media or adding a model.

This package creates only native host classes/tests and generated test-source support. Shared selection actions/buttons, system picker presentation/URI ingestion, platform opt-in navigation and app session owners follow in the next package; fixed Apple ModelStore/MLXVLM and KMP real-device loop remain separate. Default bridge is UnavailableMultimodalBridge, never synthetic success. No downloads/new dependencies/signing/project-protection changes/remote effects. Base iOS15/macOS12/API26 stays.

## Selected approach

Use native host owners following VideoHost's small API, each with injected store, bridge and picker closure. A shared coordinator would duplicate the existing VM and hide platform input lifetime; merging this with model initialization/pickers would conflate independently testable risks. Choose native ownership plus existing shared state.

Host API: model; selectImageFile(); clearImageFile(); leavePage(); background(preservePicker=false); onForeground(); close(). Apple picker replies URL?, Android replies File?; these paths are owned by the caller and are copied by the input store before reply returns. No URI interpretation in this package. Selected display name is source last component, opaque reference stays hidden by shared UI.

Each selection starts a monotonically increasing ticket. Only the latest ticket is consumed, at most once, even for nil/error/duplicate callback. clear/leave/background/close invalidate pending tickets. New selection is admitted only foreground/open/VM controlsEnabled. Successful import replaces VM selection then retires previous token; failed import leaves prior selection intact and calls typed inputFailed(). Cancellation/nil preserves existing selection. Selection launch throws map to inputFailed(), with ticket consumed.

background always calls VM.background and never starts generation. Normally it invalidates selection. Android-compatible preservePicker=true retains an in-flight externally presented picker's ticket, allowing the selected local file reply to be copied even before Activity onStart; it never starts inference. Only a currently in-flight ticket is preserved; leave/clear/close always invalidate it. onForeground changes admission only and never starts/restarts generation. Same optional behavior is exposed on Apple for test parity but defaults false.

clear resets VM input then retires selected token. Existing native-operation leases survive selection replacement/clear/close through the input store. close seals host first, invalidates picker, closes VM/bridge once, immediately closes store admission; store retains operation leases until adapter drain/release. Repeated close may retry store deletion but must not reclose bridge. Host is UI serialized/MainActor; bridge remains responsible for native drain. Holding a model beyond host close yields the existing CLOSED state.

## Verification and review

Actual synthetic local files: selected-copy isolation and replacement; independent operation lease survives clear/close until explicit release; cancellation/nil/duplicate/stale reply; selection error typed INVALID_INPUT and prior selection preserved; late reply after clear/leave/background/close; preserved external picker result before foreground; onForeground no automatic generate; close once during RUNNING/CANCELLING and wait for controlled drain terminal. Default unavailable and closed rejection tested. Controlled bridges live only in tests and are not real inference.

Each platform starts with missing-host RED then implementation GREEN. Android full Demo unit/APK/lint offline; Apple generated Demo ABI suite includes host tests and store tests, Binding remains unchanged. Generator behavioral tests inspect actual generated project sources/products. Full tools run after generator change. Scope author review each Task, final separate author review, exact local commits. Independent review remains pending under explicit inline/no-subagent instruction. Preserve all old device/quality/minOS/review gaps and artifacts.

# ModelStore hash memory implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline solo, RED→GREEN, author review, no routine confirmation/subagents per user.

**Goal:** Keep integrity verification temporary buffers bounded before continuing failed video device gate.
**Architecture:** Per1MiB Foundation read+SHA256.update in autoreleasepool; returns only Boolean. Apply to existingModelStore.verify and consumer inventory hash; no public API.
**Tech Stack:** existingFoundation/CryptoKit/Darwin/XCTest/Swift6.
**Spec:** docs/superpowers/specs/2026-10-23-modelstore-hash-memory-design.md

## Global constraints

Keep exactmanifest/byteverification, symlink/pathchecks, leases/close/cancellation unchanged. BaseiOS15/macOS12; fixedmodels/runtime/lock/dependencies; protect originaldirtyfiles. No downloads/upstream/MLXglobals/signingasset/remote changes. Keepallartifacts andindependentreviewpending.

## Review focus

Large files must not retain file-sized autoreleased readbuffers; truncated/wronghash inputs stillreject; readerrors stillpropagate/handlesclose; no per-file-only pool that retainsallchunks; consumerhash still checksactual14files.

### Task 1: Bound hash buffer lifetime

**Files:** apple/Sources/ODAIKitModelStore/ModelStore.swift; new apple/Tests/ODAIKitModelStoreTests/ModelStoreMemoryTests.swift; examples/ios-mlxvlm-native/VideoFixtureSuite.swift.
**Interfaces:** unchanged. Test private footprint sampler measures realstore install/acquire, synthetic sparse64MiB expectedliteralSHA256 and peakdelta<96MiB; no modeldependency.

- [ ] Add meaningfulrealstorememory regression with actualfullintegrity/lease lifecycle; run focusedRED beforeproductedit, save measuredpeak/failure.
- [ ] Implement per-chunk autoreleasepool around read+hash update in store and consumerhash, preserving errors andfileclose; no smallerframe/model/runtimetuning.
- [ ] Run focusedGREEN, fullbaseApple, enhancedhost(realTokenizer), fulltools explicitRuby, pycompile/diffcheck. Review andprecisestagelocalcommit.
- [ ] Resume originalruntimeTask2device suite with sourcecommit/newbuild, samefixedmodel/fixture/profile; do not mark complete unless strictactualreportpasses. If differentrealblockappears, diagnose/record separately.

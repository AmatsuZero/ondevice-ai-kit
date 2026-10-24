# Android typed multimodal video contract implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline solo per user; tests first, author scoped review, exact local commits. No subagents or artifact cleanup.

**Goal:** Add typed video request/optional backend/shared lifecycle and prove local Maven consumption.
**Architecture:** private sealed request with image/video branches dispatches inside existing operation lifecycle. No second client/session/registry. Optional backend opt-in guards admission before availability.
**Tech Stack:** existing Kotlin2.4/Android API26/JDK21/coroutines1.10.2/JUnit/Gradle offline.
**Spec:** `docs/superpowers/specs/2026-10-24-android-multimodal-video-contract-design.md`

## Global constraints

No new dependencies/models/runtime/upstream edits, minSDK26 unchanged, original two protected files untouched. All commands rtk, Gradle existing local helper --offline; fulltools explicitRuby3.4.11. No generation/backend/device/quality/release claims. No push after initial synchronization unless requested. Preserve failures/ledger/artifacts. Independent review pending;5/12/P9active.

## Review focus

- Both modalities share active Operation and clear only after mandatory drain/join.
- Cancelled admission prevents late model start; cancellation wins late outcome and callback updates.
- Video opt-in checked under lock before any legacy availability query/fallback.
- Close waits video, metadata query and backend cleanup despite cancelled waiter/errors.
- Published new type/overload present in fresh isolated Maven consumer, existing image source remains compatible.

### Task 1: Typed request and client parity

**Files:** modify android/multimodal/src/main/kotlin/io/ondeviceai/multimodal/{MultimodalContracts,MultimodalGenerationClient}.kt; create test counterpart MultimodalVideoClientTest.kt.
**Interfaces:** data class MultimodalVideoRequest(val videoFile:File,val prompt:String,val maxOutputTokens:Int=128); interface MultimodalVideoGenerationBackend:MultimodalGenerationBackend with suspend fun generateVideo(request:MultimodalVideoRequest,update:((MultimodalTextUpdate)->Unit)?=null):MultimodalTextOutput; client suspend generate(videoRequest,backend=Default,update=null):MultimodalGenerationResult.

- [ ] Write deterministic behavioral tests described in spec using cancellation-ignoring external backend gates and real client; assert selected request/output/drain, no I/O and legacy0queries, bidirectionalbusy, both modality reentry, callback identity, cancelledclose/querybarrier. Capture missingAPI RED via `rtk proxy python3 scripts/gradle-local.py --offline :android:multimodal:testDebugUnitTest --tests io.ondeviceai.multimodal.MultimodalVideoClientTest --console=plain`.
- [ ] Add values/optional interface, route overload through private sealed request, conformance check under existing lock. Preserve old worker/cancellation/close/publish/drain code other than request dispatch.
- [ ] Run focused GREEN then full module tests/lint plus KMP Android compile offline, capture XML counts. Author scope review, protecthash/index checks, exact local code commit.

### Task 2: Native consumer and local distribution

**Files:** modify examples/android-multimodal-native/src/main/kotlin/io/ondeviceai/example/multimodal/MainActivity.kt (compile/link only); ledger-owned generated isolated Maven project under artifacts/verification/android-multimodal-video-contract-20261024/.
**Interfaces:** nativeconsumer explicitlyunavailablevideo stub, constructs both requests/compilesboth generate overloads in unused suspend probe. Isolatedconsumer uses local Maven Core+Multimodal0.1.0-SNAPSHOT only; existing AGP/Kotlin/cache.

- [ ] Extend consumer compile surface before publishing: compile real new request/interface/overload in sourceconsumer, retain no autogeneration. Run assemble/lint offline, expected BUILD SUCCESSFUL.
- [ ] Build release AAR/sourceJar via local publish tasks; record SHA256/typeentries/POM dependencies. Generate fresh outside-root Gradle project with only localMaven coordinates and same fixed pluginversions; compile public image/video API from actual AAR, expected BUILD SUCCESSFUL, runtime graph no projectSDK/model deps. No remote Maven publication.
- [ ] Run existing complete Androidverify task list offline and fulltools explicitRuby, pycompile/diffcheck. Author scope review and exact local consumercommit; report exact task/test counts, no deviceclaim.

### Task 3: Recovery checkpoint

**Files:** new docs/validation/android-multimodal-video-contract.md, ROADMAP/handoff/current plan, ledger checks/finalreview.

- [ ] Record actual RED/GREEN/build/lint/Maven evidence, hashes, sourcecommits and authorselfreview; preserve pending independentreview and all roadmapgaps. Record initialsync outcome accurately; no inference from failedtransport.
- [ ] Verify protectedhash/index, docslinks, committedsource versus artifact hashes anddiffcheck; localdocscommit, continue next independent KMPdesign only after this parity package gates pass.

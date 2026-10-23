# Internal video snapshot profile implementation plan

> **For agentic workers:** superpowers:executing-plans, inline/solo, preserve all logs; no routine confirmation or subagents per user instruction.

**Goal:** Add real negative-profile coverage and attempt native iOS decode/cancel only with existing signing.

**Architecture:** New small synthetic fixtures exercise existing snapshot. No public API, new model or adapter reimplementation.

**Tech Stack:** FFmpeg, Python binary box rewriting, XCTest/AVFoundation, Xcode fixed cache.

**Spec:** `docs/superpowers/specs/2026-10-23-apple-mlxvlm-video-profile-design.md`

## Global constraints

Current foundation worktree only, fixedlock/model/runtime unchanged. No downloads/private media/signing-asset changes/remote writes. Preserve two originaldirtyfiles andallartifacts. No video modelinference/publicAPI. Native snapshot stillrequires8uniqueactualsamples and H.264BT.709SDRwithin existingbyte/time/sizebudgets; failuresmustcleanupownedcopy.

## Review focus

- H.264codec alone must not admitPQ/HDR (Task1).
- Nonzero/edittrack timing mustnotbe normalizedsilently (Task1).
- Fewerthan8actualsamples mustnotbe duplicated oracceptedpartially (Task1).
- ExternalURLdref profile mustberejected,not treatedas8ordinaryframes (Task1).
- iOS destination/signing/testinitialization must be distinguished from executingtestbodies (Task2).

### Task 1: Real negative profile fixtures and coverage

**Files:** add4smallfixtures under `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/Fixtures/`, update its README; create `MLXVLMVideoProfileTests.swift` in the sameTests target.

**Interfaces:** existing snapshotload(observer:) only; no productchange planned. Tests usecopiedobserver thenassertsnapshoterror andprivatecopygone; originalfixture bytesunchanged.

- [x] GenerateH.264PQ,nonzerooffset,2frame andexternal-dref fixtureslocally; preserveexactcommands/script/hash/ffprobe evidence.
- [x] Writeseparateasync tests foreachprofile and runagainstbaseline. Recordactualpass/fail; investigate anyunexpectedacceptancebeforefix.
- [x] If baselineGREEN,temporarilydisableonlytransferguard;runHDRtest andrecordrealmutationRED;restoreguardbyteexactlyandrunallprofiletestsGREEN. Neverstagefaultmutation.
- [x] Fullenhancedhostwithfixedrealtokenizerenv;authorscopereview;precisestage/localcommit.

### Task 2: Native iOS gate and recovery

**Files:** validation/ROADMAP/handoff/thisplan; localledger/logs only unlessprofilebugrequiresseparatelydocumentedfix.

**Interfaces:** same snapshot/ProfileTests; schemeOnDeviceAIKitAppleMLX andexistingprojectsigningteam, no profileupdates.

- [x] Checkconnectedphysicaldeviceandtestdestinations; attemptonlynative snapshot/profileXCTestwithexistingteam,3offlineflags,noallowProvisioningUpdates. Recordargv/exit andwhetherbodiesexecuted.
- [x] If existingprofile/platformrunnerconditionsblock, retainrawfailureandmarkiOSdecode/cancelunverified; no repeatedsamegate orimplicit signing changes.
- [x] Package tool-hosted device path actuallyfailedexit70; use separatelygeneratedemptyhostApp with existingprojectbundleID/team andsame snapshot/profiletestsource+fixtures,recordhashes/argv. This changeshoststrategy,not product orsigningassets. NoallowProvisioningUpdates;evaluateactualtestbodyresults.
- [x] Updateexacttestcounts/fixturehashes/deviceoutcome andnextsafeentry;authorselfreview,links/diff/hash/indexchecks;localdocscommit. PublicAPI/devicevideo inference andindependentAPPROVE remainpending.

## Execution checkpoint

Tasks1/2 complete as narrow profile/native-input-preparation evidence:81ea4df plus docs checkpoint. Fullhost34/34,tools126/126;physicalsnapshot/profile12/12andadapter6/6,no skips. PublicAPI/runtime/video inference remainunstarted;shorterclip supportstillmissing. No independent APPROVE.

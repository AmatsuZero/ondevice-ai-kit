# Enhanced Multimodal Demo integration plan

> executing-plans inline/no subagents; perTask RED→GREEN/review/exact local commit. Explicit user authorizes solo execution without routine approval.

**Goal:** actual enhanced bootstrap and App-local AX shared-control integration evidence, with explicit touch/picker/OS limits.
**Architecture:** optional validation source, real DemoController/host/VM/Compose/MLX unchanged; bounded UIKit AX driver.
**Tech Stack:** Swift/UIKit/KMP/Ruby generator/Python XCTest project tests.
**Spec:** docs/superpowers/specs/2026-10-22-multimodal-demo-integration-design.md

## Global constraints

Existing fixed model14files/lock/cache, syntheticfixture only, authorizediPhone00008140-0008703436D3001C/team4X996UV8LC. No KMP/header export changes, downloads/private media/recording/signing change/push/merge/publish. Both protected files untouched/unstaged; logs retained; defaults/basefloors unchanged; roadmap5/12/P9active; authorreview only.

## Review focus

Ambiguous AX activation, cyclic/unbounded containers, preparation failure misreported as success, validation running outside explicit gates, system-picker/physical-touch overclaim. Pin helper cases inTask1, generator/runtime and actual report inTask2.

### Task1: bounded App-local accessibility actions

Create probes/ios-multimodal-demo/AppAccessibility.swift and AppAccessibilityTests.swift; extend Demo-only multimodal-bridge XCTest generator/source-set tests.
Interface MainActor AppAccessibility.find(identifier: String, roots: [NSObject]) throws -> NSObject?; activate(identifier: String, roots: [NSObject]) throws -> Bool. Public UIKit APIs only; exact identifier, duplicate object deduplication, ambiguity throws, finite node/depth cap, false activation reported.

- [x] Write actual XCTest missing helper RED, using real UIKit container/element graphs: nested discovery/one action, duplicate objects not duplicate action, ambiguous exact ID rejects, absent returnsfalse, failing action returnsfalse, cyclic graph terminates. Extend real generator inspection before implementation.
- [x] Implement bounded finder/action; fresh complete Demo ABI XCTest and generator tests GREEN, strictnewSwift0. Scope authorreview/exact local commit test: add bounded in-app accessibility driver.

### Task2: enhanced real bootstrap and shared-control validation

Create probes/ios-multimodal-demo/MultimodalDemoValidation.swift; modify OnDeviceAIDemoApp.swift and scripts/generate-speech-demo.rb/tests. docs/validation/roadmap/handoff/ledger/final-review.
Interface MainActor static start(preparing: @escaping()->Bool, host: @escaping()->MultimodalHost?) onceonly, additionally explicit runtime flag; call from gated App.onAppear. Generated validation-metadata.json full sourceCommit resource, buildflag requiresenhanced and metadata rejects invalidbeforeoutput.

- [x] Generator RED missing validation flag prerequisite/source/resource/provenance validation, then implement gated driver/hook/generator. Driver bounded awaits bootstrap→real backend→actualtab/select/pickerdelegate/generate→actualVMoutput/AXidentity; fixed failure gate if AXunavailable. No test result substitution or global accessibility mutation.
- [x] Fulltools/freshDemoXCTest/newSwiftstrict/default+enhanced compile; localcodecommit binds source before final generated signeddevice build. Verify actual bundledmetadata/fixedlock/hash/connection, installonlyenhancedbundle/copyfixedassets/launch explicitflags, retrieve ownreport. One bounded AXattempt; if unavailable mark pageunverified and preserve successfulbootstrap evidence.
- [x] Separateauthorreview; update exact validation/roadmap/handoff/ledger/final-review, protection/indexchecks/localcommit. Physicaltouch/OSbackground/realfilepicker/quality/minimumOS/independentreview stayexplicitlyunverified.

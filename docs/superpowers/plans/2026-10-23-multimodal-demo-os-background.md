# Multimodal OS background implementation plan

Required skill: executing-plans inline, solo/no subagents. Spec: docs/superpowers/specs/2026-10-23-multimodal-demo-os-background-design.md. Goal real OS lifecycle evidence, not touch. All spec global constraints apply; existing worktree and signatures only, no downloads or protected-file staging.

## Review focus

Wrong-source/stale ready, generation completed before switch, missing OS lifecycle or invented direct host.background, same-process restore, reentry without automatic restart. Task1 mutationtests plus Task2 realnotification capture cover these; fail closed, no race retries.

### Task1 strict report

Create tools/multimodal_background_report.py and tools/tests/test_multimodal_background_report.py. validate(report,expected_commit) consumes existing provenance plus gates/events/ready/reentry; supports direct/moduleCLI. ActualmissingmoduleRED→validreport/mutations GREEN; fulltools with explicitRUBY3.4.11. Scopeauthorreview/exactlocalcommit.

### Task2 suite and real device

Create probes/ios-multimodal-demo/MultimodalDemoBackgroundValidation.swift and independent probes/ios-multimodal-background/{BackgroundApp.swift,project.yml}. Modify existing validationhook/runtime selection/generator source-settest. ActualgeneratorRED and missingSwift suitecompileRED→GREEN. No production callbacks changed. Temporaryselector observer records actual OS notifications; ready atomic write followed by externally driven companion activation, samePIDrestore, realcancel300msnoautostart/AXreentry32identity/close. Actualcompanion unsigned/signedcompile. Fulltools/freshDemo/strictSwift/default/enhancedcompile, codecommit then regenerate exactmetadata/signedbuild/checkconnection+assets+lock/installonlyownbundles/run/retrieve/strictvalidator. Record real failures accurately. No routine confirmation.

### Task3 records

Authorfinalreview; update validation/roadmap/handoff/ledger/final-review/protectedhash/index; exactlocaldoccommit. Only Task2 strictphysicalreport closes OS gate, never touch/systempicker/quality/minimumOS. If externalblocked retain exactgate and move nextsafeindependentwork.

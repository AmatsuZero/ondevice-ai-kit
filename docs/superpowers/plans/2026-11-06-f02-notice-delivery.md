# F02 notice delivery implementation plan

> Execute solo with superpowers:executing-plans; explicit user solo/autonomy overrides fresh reviewer/design handoff.

**Goal:** Produce a new normal Maven candidate carrying original notices and prove consumer retention.
**Architecture:** Source-level publishing supplement, classifier JAR plus source JAR material; explicit consumer collection.
**Tech Stack:** Existing Gradle8.14.3/KGP2.2.20 builder and Gradle/KGP2.4.0 consumer, offline caches.
**Spec:** docs/superpowers/specs/2026-11-06-f02-notice-delivery-design.md

## Constraints and review focus

No generated artifact rewrite, no .1/.2 mutation, no new downloads/dependencies/device/remote
publication, no production change. Protect9paths and lock. Evidence is not independent approval.
Verify missing-notices RED, normal task attachment, no classifier collisions, source comment
retention, exact consumer file set/hashes, all40 native input hashes and same-version closure.
No claim that resolving a notices classifier automatically copies it into the final iOS App.

## Task1: Material and source supplement

- [x] Inventory retained native header dependencies and capture exact extra notices/provenance; identify unresolved cases.
- [x] Show frozen .2 source and publication lack modification notices/notices classifier (RED).
- [x] Create isolated .3 source export; apply existing patches and add source publishing supplement.
- [x] Include original material and IJG attribution; no legal approval claim or release promotion.

## Task2: Normal publication and consumer

- [x] Publish .3 offline using normal Gradle release tasks; verify all3 coordinates, checksums, source modification comments and native archive identity.
- [x] Resolve notices from an isolated KGP consumer and preserve all exact texts/files in its distribution directory.
- [x] Compile/link dual-iOS consumer for the new candidate if binary inputs change; otherwise substantiate reuse with exact identity evidence.
- [x] Perform separate author review, targeted verification, update handoff/readiness once and local commit.

Ruling: use an explicit notices classifier + source-JAR attachment; no silent final-App embedding.
User solo override excludes independent agent review. Retain unresolved file/license obligations.

Outcome: final normal publication59tasks(51executed),115files/3modules; six notice/source
archives each contain69exact files and18distributed source comments pass. Consumer10tasks
resolves/retains69files and links both iOS frameworks. Missing/changed/extra-material negative
checks reject. Actual seven-file source bytes match all3patches against exact upstream revision.
Full header inventory3994inputs/2115headers/10external components; no missing local file.
Author review separate from implementation: fixed root sourcesJar task-case and nested Git
patch filtering issues with retained RED/GREEN evidence. No independent approval claim.
Historical Unicode/file exceptions, final App retention, remote consumer and production acceptance remain.

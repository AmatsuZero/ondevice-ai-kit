# F02 iOS publication implementation plan

> Execute solo with superpowers:executing-plans, under the user's autonomous local-work instructions.

**Goal:** Repair the fixed source publication and prove a complete iOS-only Maven graph.
**Architecture:** Dedicated source patch; normal KGP metadata generation and Maven publication.
**Tech Stack:** Existing Kotlin2.2.20, Gradle8.14.3, JDK21 and fixed Skiko/Skia.
**Spec:** `docs/superpowers/specs/2026-11-06-f02-ios-publication-design.md`.

## Global constraints

Solo; offline existing dependencies; preserve protected files, production lock, original archive,
and prior experiment sources. No remote publication, device action, generated-metadata rewrite,
or compiler diagnostic suppression. New candidate version for changed artifacts.

## Review focus

- Required native expect annotation retains existing SymbolName actual and exported symbol linkage.
- Shared native metadata is usable from a distinct consumer, including commonMain.
- Root metadata has no AWT/Android/Web targets or dangling same-version references.
- Publication source task graph does not create standalone AWT runtime when disabled.
- Candidate provenance identifies source patch and native TLS-owner inputs separately.

### Task 1: Source-level iOS repair

Files: isolated exported Skiko commonMain annotation sources and buildSrc publishing.kt;
retain final patch under `probes/skia-atlas-text-cache/` only if verified.

- [x] Reproduce strict dual-iOS compileCommonMainKotlinMetadata failure offline; require actual annotation errors.
- [x] Export fixed source into a fresh evidence directory; remove JVM-only annotations and OptionalExpectation.
- [x] Run the same strict dual-iOS metadata compilation; require zero compiler errors.
- [x] Gate AWT runtime publication at source when supportAwt is false; generate root metadata/POM.
- [x] Build normal root/device/simulator publications and validate all referenced same-version coordinates.

### Task 2: Consumer and handoff

Consumes: task1 Maven repo and exact patched source provenance.
Produces: linked consumer evidence and a retained patch/receipt, or exact failed gate.

- [x] Compile/link isolated consumer using only new local repository for patched Skiko.
- [x] Inspect changed API/signatures and native embedded library identity; preserve license gaps honestly.
- [x] Update F02 validation/handoff/readiness once with meaningful technical evidence; targeted checks then local commit.

Ruling: no independent agent review per user's explicit solo override; author review is not independent approval.

Verified outcome: strict metadata RED1,022 errors -> GREEN13 tasks; normal release58 tasks;
three modules/100 files/six references. Standalone KGP2.4.0 consumer9 tasks and actual SDK
Compose consumer14 tasks linked both architectures. Author diff/evidence review is recorded
at checkpoint; not independent approval. `.2` stays frozen. Supplemental source-notice patch
for the next version and full-source/ICU-data/license staging do not close remaining delivery gates.

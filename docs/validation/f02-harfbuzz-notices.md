# F02 HarfBuzz file notices and generated inputs (2026-11-06)

The fixed native build consumes `hb-ucd.cc`, whose ISC grant and Grigori Goronzy copyright
are absent from the frozen .3 notice material. This is a concrete omission in that material;
passing the .3 publication/App retention checks did not establish license completeness.
The original copyright and grant are now retained in a separate notice supplement.

The bounded332-input audit also retains255 original Old MIT leading notices and the Unicode
emoji copyright/reference.115 unique leading blocks map to257 inputs;75 inputs have no
leading legal block, with the existing HarfBuzz root COPYING retained. That last category is
not a claim of missing permission. The audit covers leading blocks and actual build inputs,
not every comment or all source distribution files. The fixed source revision is
`08b52ae2e44931eef163dbad71697f911fadc323`.

Tracked material:

- [Original leading notices](../../probes/skia-atlas-text-cache/harfbuzz-notice-supplement/harfbuzz-compiled-notices.txt)
- [Input/block/generator hash mapping](../../probes/skia-atlas-text-cache/harfbuzz-notice-supplement/HARFBUZZ-INPUTS.json)
- [Source-specific review](../../probes/skia-atlas-text-cache/harfbuzz-notice-supplement/GENERATED-DATA-REVIEW.md)

Eleven generated/external-source tables are identified, expanding the earlier generic
Unicode question to concrete sources:

| Inputs | Current evidence | Remaining boundary |
| --- | --- | --- |
| Seven Unicode16 tables/functions | Embedded generation headers and scripts identify versions/inputs; .3 already includes Unicode16 ReadMe and License v3 | Raw release inputs/exceptions not reproduced; no blanket historical grant conclusion |
| Three Microsoft ms-use files | Local data and separate MIT COPYING, already in .3 | Grant applies to these inputs, not all Microsoft website content |
| IANA registry |2021 primary statement applies CC0 to IANA/IETF rights; registry linked from protocol index | Fixed generator used2025-03-10 data; current registry not substituted; no grant over other sources |
| Microsoft language tags/OS2/Windows IDs | Source/generator URLs and input header date retained | No table-specific grant established in bounded check; general Learn terms are not a prohibition verdict |
| SIL mapping | gen-tag-table.py cites original SIL URL | Primary endpoint403; third-party transcriptions not accepted as grant; exact terms remain unverified |
| Two Arabic PUA mappings | Local inputs/generator hashes, root HarfBuzz COPYING | No separate leading grant; source history/applicability unresolved |
| Eight Ragel outputs | Explicit meson source list, original Old MIT blocks retained | Generator machinery, not additional Unicode datasets |

Primary pages checked: [Unicode terms](https://www.unicode.org/copyright.html),
[Unicode16 ReadMe](https://www.unicode.org/Public/16.0.0/ucd/ReadMe.txt),
[IANA licensing](https://www.iana.org/help/licensing-terms),
[IANA protocol index](https://www.iana.org/protocols),
[Microsoft language tags](https://learn.microsoft.com/en-us/typography/opentype/spec/languagetags),
[Learn terms](https://learn.microsoft.com/en-us/legal/termsofuse),
[SIL table endpoint](https://iso639-3.sil.org/code_tables/download_tables) (403).
No challenge bypass, new source/model download or production change.

Evidence: `artifacts/verification/f02-harfbuzz-license-review-20261106/`. `prepare.py` creates
fresh72-file draft .4 material without replacing .3; `verify.py` checks332 source hashes,
115 exact notice blocks,11 generated/external tables,16 extra source inputs, old material
preservation and protected inputs. Four negative cases reject missing/tampered material,
including reindexing a removed ISC attribution. The primary-terms record retains retrieved
source evidence. Prepared data is not an independent legal/review approval.

```sh
rtk proxy python3 artifacts/verification/f02-harfbuzz-license-review-20261106/verify.py
```

The separately retained .4 engineering publication/consumer receipts determine delivery
status; preparation alone is not publication or App retention. Frozen .3 remains unchanged
and retains its known omission. F02/P4/P11 and release remain open/not-ready.

## .4 local delivery verification

New candidate `0.144.6-ondevice-f02.4` was normally published offline from the same verified
source/native inputs with the 72-file supplement. All 59 publication tasks executed successfully.
The output has 130 files: 115 immutable version files across three modules, plus 15 module-level
Maven metadata/checksum files. Six notices/source JARs each preserve the same 72 exact materials.
The archive excludes mutable module-level metadata: 108,785,484 bytes, SHA256
`e59efab02e31d2f609e7328b5f8118c86e47e47029c74c4437a70c2b9dc68869`.
Frozen .1/.2/.3 remain unchanged; the original .3 ISC omission remains recorded.

A fresh actual SDK export of `544cb15929eb15a6dd51810797eaeffe4631516e` uses three isolated
overlays: exclusive local .4 repository, candidate version rule, and notice collection script.
Both SDK iOS frameworks link successfully (15 tasks: 14 executed, one up-to-date).
`dependencyInsight` confirms Compose selects .4. The unmodified committed App generator and
normal unsigned device build retain all 72 materials exactly, including the ISC attribution;
all 77 App files (including debug dylib) and both SDK framework binaries are hashed.
No signing, installation, phone workflow or runtime acceptance was performed.

The integrated collector initially failed with `Unable to find a matching variant` while the
same artifact collected successfully in a KMP standalone consumer. The SDK root only declares
KMP with `apply false`: its attribute-free configuration lacks KMP variant disambiguation rules.
The collector now explicitly requests the common / kotlin-metadata / library variant before
selecting `notices@jar`. This passes in both the SDK root and KMP standalone build without
altering Maven metadata or artifacts. Making the version rule conditional did not fix the
failure; that hypothesis was rejected and the original rule restored. Both RED logs remain.
See Gradle's [configuration attribute guidance](https://docs.gradle.org/current/userguide/best_practices_dependencies.html)
and [variant selection](https://docs.gradle.org/current/userguide/variant_attributes.html).

The full tools suite passes all 312 tests with the verified Ruby 3.4 environment. The first
run accidentally used system Ruby 2.6 with Ruby 3.4 gems (17 failures / 6 errors); its full
log is retained separately. No source change was needed to resolve that environment mismatch.

Delivery receipts: `artifacts/verification/f02-harfbuzz-notice-delivery-20261106/`.
Preparation evidence remains the historical draft snapshot; delivery evidence records the later
local publication and App build. The read-only verifier checks repository/archive bytes, six
notice/source JARs, 40 native archives, consumer/App retention, SDK inputs and protected paths:

```sh
rtk proxy python3 artifacts/verification/f02-harfbuzz-notice-delivery-20261106/verify.py
```

This closes the concrete ISC packaging omission in .4. Source-specific generated-data terms,
independent approval, Nexus anonymous remote consumption, maintenance ownership and production
resource acceptance remain open. Packaging is not a license approval. Nexus reads require no
credentials; publisher permissions are settled. Solo author review is not independent approval.
F10 has no authorized calibration dataset and remains `THRESHOLD_UNVERIFIED`.
Foundation stays 10/12; P4/P11 and release remain open/not-ready, with no benchmark samples added.

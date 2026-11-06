# F02 publication notice integrity (2026-11-06)

The existing notice publication supplement accepts a directory containing an index without
checking the indexed material. A real offline Gradle regression changed `LICENSE` after
indexing it: publication exited0 and both the notices/source JARs contained the changed
bytes alongside the original index. This is a future-publication input-validation defect;
it does not show corruption in the separately verified frozen `.4` candidate.

Apply [the validation supplement](../../probes/skia-atlas-text-cache/Skiko-ios-notice-validation.patch)
after the existing iOS-only, source-notice and notice-publication patches, in a fresh source
copy for a later version. It validates schemaVersion1, nonempty entries, safe unique paths,
SHA256, exact file membership and real files/directories without symlinks. Validation runs
at configuration and through an always-running prerequisite of the notices/source JAR tasks.
No project dependency was added; it uses the existing Gradle/Groovy and JDK facilities.
This detects invalid input at those boundaries; it is not an atomic snapshot against a
concurrent writer. Continue to freeze and verify the material used for real publication.

The earlier three patches, frozen `.1`–`.4` sources, Maven files and App evidence are unchanged.
The new supplement has not been applied to production dependencies. A fresh isolated source
copy compiles its actual buildSrc and creates only the `.5` notices JAR, retaining all72
materials exactly. The `.5` name is local test output: no full `.5` Maven publication, native
build, SDK/App relink, upload, signing or device runtime acceptance is claimed.

## Verification

Evidence: `artifacts/verification/f02-notice-validation-20261106/`.

- The original valid fixture publishes normally; `red-tampered/result.json` records the
  genuine regression failure (Gradle exit0 with incorrectly indexed published bytes).
- `matrix.json`:14 real offline Gradle checks pass, including valid publication, changed,
  missing/extra files, invalid/missing index, schema, duplicates, path escape, invalid digest,
  empty entries, root/file symlinks and a change after configuration before task execution.
  These run an isolated fixture of the actual added publication code, not a native SDK build.
- `actual-v2-result.json`: actual patched Skiko buildSrc compiles and validation executes;
 72 retained files in the notice-only JAR match the frozen input index. The repaired source
  SHA256 is `410575642025d0935f01183f5fe40720fe2d370b3365edcbe911c862411b1b6f`.
- `actual-followups.json`: the validator still executes when the notice JAR is up-to-date;
  a changed72-file input copy is rejected and the preceding JAR stays unchanged. The frozen
 `.4` verifier still passes130 Maven files, six notice/source JARs,40 native archives and
  both consumer/App72-file sets.
- Fresh full tools suite:414 tests passed in50.034s; this is separate from the14 Gradle checks.

The first actual-source attempt is explicitly rejected in `actual-parent-rejection.json`:
running `git apply` below the parent repository silently skipped the patch. Its exit0 and
old-code JAR result are retained but not counted as corrected-code proof. Recovery uses
`patch --batch --forward --fuzz=0 -p1`, checks the repaired source hash and requires the real
validation task in the build log. Existing upstream buildSrc warnings remain in the log.

The portable regression entry accepts an existing Gradle installation and already prepared
Skiko publishing source. Run it with an existing supported JDK, offline, into a fresh output:

```sh
rtk proxy python3 probes/skia-atlas-text-cache/check-notice-publication.py \
  --gradle /abs/gradle-8.14.3/bin/gradle \
  --publishing-source /abs/frozen-dot4/skiko/buildSrc/src/main/kotlin/publishing.kt \
  --validation-patch probes/skia-atlas-text-cache/Skiko-ios-notice-validation.patch \
  --case tampered --output /abs/fresh-evidence
```

Use `--case valid` for the positive control; `--help` lists the negative cases. Omitting the
validation supplement intentionally exercises the earlier code for the RED comparison.

Ollama `qwen3-coder:30b` returned a bounded two-file review in12.58s. Parent reproduction
confirmed the integrity finding. Its suggestion to remove notices from source JARs was
rejected because those materials are a required part of the distribution. Raw response and
assessment: `.omx/artifacts/ollama-f02-publication-review-20261106/`. This is supplied-context
local technical assistance, not independent license or release approval.

The user subsequently authorized sending the unchanged original seven-file packet to agy.
It completed SUCCESS/one turn in225.17s with no reported new findings; all seven source hashes
still match. The reviewer did not flag the already reproduced old publisher integrity defect.
That defect is fixed separately in `3123f3c`; the fourth validation supplement was outside
this packet and has not received this review. Zero reported findings does not override the
actual RED evidence or approve source-specific licensing, the whole branch or release.
Raw response, exact authorization and parent assessment remain in
`.omx/artifacts/agy-f02-notice-review-20261106/`; the initial approval rejection is preserved
as history rather than overwritten. No new test execution is inferred from reviewer prose.

## Source-reference follow-up

The separate native public-reference researcher failed at startup with429 and returned no
research result; that lane was stopped. Parent read-only primary-page inspection found:

- [HarfBuzz discussion6323](https://github.com/harfbuzz/harfbuzz/discussions/6323) has a
  maintainer's factual account that the name-language table was initially joined by a script
  and then edited manually. It supplies no equivalent-input regeneration record or new
  source-specific Microsoft/SIL grant. Its upstream revision differs from this candidate.
- The discussion links the actual mapping introduction
  [6e29060](https://github.com/harfbuzz/harfbuzz/commit/6e29060af2e4490e67e24ec2ef5172541275a855)
  (Khaled Hosny authored, Behdad Esfahbod committed), followed by the separate
  [rename/generator change a15ad77](https://github.com/harfbuzz/harfbuzz/commit/a15ad778fede9e94428f6811293c71a63216234a).
  Those are provenance references, not an input-ownership or redistribution verdict.
- [MS-LCID](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-lcid/70feba9f-294e-491e-b6eb-56532684c37f)
  includes an explicit implementation/documentation copyright permission. The current
  [Windows language-identifier article](https://github.com/MicrosoftDocs/win32/blob/docs/desktop-src/Intl/language-identifier-constants-and-strings.md)
  directs readers to MS-LCID, and its repository has a CC-BY4.0 license. This identifies
  specific permission sources to assess; it does not establish historical byte identity
  for the candidate's manually assembled table or cover all OpenType page content.
- The primary SIL code-table page still returns403. Microsoft Typography's public issue
  repository does not expose the OpenType source tables. Neither third-party transcriptions
  nor unrelated repository licenses were substituted for the missing source-specific proof.

No upstream message was posted, dataset substituted or downloaded, or frozen notice material
changed. The source-specific terms, independent approval, maintenance ownership, remote
anonymous consumer and production resource gates remain open. Foundation10/12, P4/P11 and
release `open/not-ready` are unchanged; no benchmark samples or product acceptance added.

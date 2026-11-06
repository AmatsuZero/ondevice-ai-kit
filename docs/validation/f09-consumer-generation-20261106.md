# F09 binary consumer generation: consistent input and exclusive publication

The consumer generator now reads the manifest once. Validation receives a temporary file containing those frozen bytes with the explicit artifact root; the copied manifest, generated Swift hash and consumer receipt all use the same snapshot. Mutating the caller's manifest during validation or XcodeGen no longer changes the consumer's identity.

Publication uses an exclusive rename operation: macOS `renamex_np(RENAME_EXCL)` or Linux `renameat2(RENAME_NOREPLACE)`. An existing destination is preserved, including an empty directory created after the initial existence check. An unavailable API or unsupported filesystem fails publication; there is no fallback to ordinary overwriting rename. The temporary staging context cleans only its own scratch directory.

The intermediate mkdir-reservation implementation was rejected after Copilot identified that the reservation itself could be swapped before the final ordinary rename. That implementation and its tests are retained in local evidence; it is not the delivered design. The installed Apple SDK/manpage and canonical Linux rename manpage were checked in a separate read-only reference task. macOS publication was executed in the regression tests. Linux runtime validation was subsequently completed in the environment below.

## Linux runtime and additional boundary regressions — 2026-11-06

The unchanged Python generator (`570b64a0…22d48c`) was exercised on actual Linux/aarch64,
Python3.12.15 and glibc2.41 using the already-installed local Colima Python image
`sha256:a0f57064cbdcd6285fa45f51237ab0b35ea8ebaed06a1f5973e89e19dfb86704`.
The container had no network, read-only source mounts, an unprivileged UID and temporary
scratch storage; no image was downloaded and existing container identities were unchanged.

Ten standalone boundary checks plus three existing repository regressions passed with no
failures or skips. Actual native operations verified successful publication, preservation of
existing empty/nonempty directories, regular files and both valid/dangling symlinks, one
winner between concurrent publishers, and EXDEV without a copy fallback. Scoped fixtures
also verified unavailable-API/unsupported-platform failure; those two branches use mocks.

Two portable regressions are now in `tools/tests/test_mlxvlm_binary_consumer.py`: file/symlink
preservation and competing directory publication. Both pass on Linux; the full focused
consumer module passes **18/18** on macOS. The earlier 412-test tools result remains its
historical baseline and is not presented as a new full-suite run after these two additions.

Ollama generated the initial candidate in22.79s; parent review corrected missing imports,
directory fixtures, expected exceptions, invariant checks and the unavailable-API mock
before execution. Candidate and raw response remain in
`.omx/artifacts/ollama-linux-publish-checks-20261106/`; actual Linux receipts are
`linux-result.json` and `integrated-linux-result.json` there. macOS result/log and the
source/log hash summary are in `artifacts/verification/f09-linux-publication-20261106/`.
This closes the previously untested Linux publication branch for this environment, without
rebuilding native SDK artifacts or granting independent release approval.

## Verification

- Original generator: manifest receipt mismatch and replacement of a raced empty output directory reproduced with actual XcodeGen. Initial regression tests failed before implementation.
- Frozen-validation/copy regression and real exclusive-publication boundary tests also reproduced the missing behavior. The final tests cover empty/nonempty destinations and unavailable native APIs without falling back to overwrite.
- Complete tools suite: **411 tests passed in 54.756 seconds**. Actual consumer generation, relocation, resource validation and Swift parsing remain covered.
- Subsequent combined regression after correcting the Android SR fixture profile: **412 tests passed in 47.682 seconds**. F09 generator/test bytes were unchanged; the new test concerns Android SR dimensions. Evidence: `artifacts/verification/android-persistent-candidate-20261106/tools-tests-after-sr-profile-host-result.json`.
- The first full-suite attempt had 19 failures and 8 errors caused by Ruby 2.6 loading inherited Ruby 3.4 gems. It is retained under its original filename. The successful rerun prepended the existing Ruby 3.4.11 binary directory only in the test subprocess environment; no global Ruby/gem settings or locks changed.

Local evidence: `artifacts/verification/f09-consumer-fix-20261106/`, including `red.log`, `red-extended.log`, `exclusive-publish-red.log`, `exclusive-publish-green.log`, `tools-tests.log` and `tools-tests-ruby-aligned-result.json`. Original characterization remains in `artifacts/verification/f09-consumer-race-20261106/before-fix.json` and must not be overwritten with a post-fix run.

The hy4free fix request terminated without a supervisor timeout, but its JSON response was truncated and no usable candidate was accepted. The primary agent implemented the fix. Copilot's focused review found the intermediate publication issue; its feedback was assessed and corrected with fresh regressions. These helper tasks do not provide independent release approval.

The subsequent Copilot review of the final exclusive-publication implementation returned a complete result with no findings in 33.24 seconds. Its prompt included the shared-pitfalls excerpts; zero tool executions were recorded. Evidence: `.omx/artifacts/copilot-f09-exclusive-review-20261106/`.

No native SDK artifacts, production dependencies, models, signing state, protected files or Apple lock were changed by this fix. Android candidate staging and device acceptance are tracked separately. Roadmap remains 10/12, P4/P11 open and release not-ready.

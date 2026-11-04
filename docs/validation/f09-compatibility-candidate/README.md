# F09 local compatibility candidate

2026-11-04. **Draft, not adopted.** This is a concrete proposal to unblock the
existing [complete binary-closure plan](../../superpowers/plans/2026-10-30-apple-mlxvlm-binary-distribution.md).
It does not approve production dependency changes or turn the historical
wrapper-only diagnostic archive into a release candidate.

## Proposed decision

Allow a versioned compatibility overlay **only in fresh isolated F09 binary
build copies**, bound to the existing production lock and exact source hashes.
Keep the normal Apple source packages, dependency checkout cache and
`apple-mlx/Package.resolved` unchanged. No dependency upgrade or new package is
included. The overlay becomes part of the resulting binary artifact's declared
source provenance and maintenance responsibility, rather than an invisible
build workaround.

There are two meaningful routes: wait for a supported installed compiler fix,
or adopt this maintained overlay and verify the complete binary closure. The
current host has only Xcode's Swift6.4.2; issue91540 remains open and PR91672
remains unmerged. A related repair, PR92814, merged into upstream main on
2026-10-01; that does not establish inclusion in the installed compiler. A fresh
arm64 iOS18 minimal reproduction on this installed compiler failed interface
verification with the exact cross-module Element error; applying the selector
flag only to the failing Client module made it pass. Latest released
swift-collections is1.7.5, which
already failed the isolated iOS interface gate. This proposal recommends the
isolated overlay route to make local packaging progress; it is the author's
recommendation, not a user-approved change.

## Exact changes

`manifest.json` pins the four original and patched file digests, two upstream
revisions, patch hashes, production-lock digest and MLX runtime version.

| Package | Patch | Behavior |
| --- | --- | --- |
| swift-collections1.7.3 / `35a1edcb8ebd2bfe251d587f0b69e62af28f508c` | [swift-collections.patch](swift-collections.patch) | Only OrderedCollections target adds `-Xfrontend -disable-module-selectors-in-module-interface`; EventSource and all other targets keep their own defaults. Interface verification stays enabled. |
| mlx-swift-lm / `6e338e5df49424fc0db7c6d58184d622f92ed18a` | [mlx-swift-lm.patch](mlx-swift-lm.patch) | Five `@unknown default` arms in KVCache, TurboQuantKVCache and Gemma4 explicitly trap unsupported attention-mask enum values. Existing cases are unchanged. |

The five arms call `fatalError("Unsupported attention mask mode")`. This is a
crash policy for future/unsupported enum values, **not** a recoverable error,
CPU fallback or attention approximation. The candidate is bound to
MLX0.32.3/revision19601207e9a0de51e03ee6ec0c3c5f3784275075; mixing newer MLX
binaries is unsupported and must fail the artifact provenance gate. That binding
does not prove the unknown arms unreachable under arbitrary runtime substitution.
If a graceful unsupported-value error is required, this candidate is unsuitable;
changing those nonthrowing upstream APIs needs a separate behavior design.

The cached source licenses are swift-collections Apache2.0 with Swift runtime
library exception, and mlx-swift-lm MIT. Any eventual binary set must carry the
original notices plus an explicit modified-source notice and exact patches.
This source-header inspection is not a completed redistribution/license review.

## Fresh checks and remaining gates

Four individual source files were read from the exact official revisions to
recover pristine bytes: the old diagnostic checkout's Git alternates refer to a
now-absent DerivedData object store, so its HEAD alone cannot prove its contents.
The existing patched bytes match the recorded successful diagnostic archive's
four source hashes. Applying these exact patches in a fresh temporary directory
reproduced all four patched hashes. `patch-replay.json` records this check;
it does not execute the model or claim a new archive.

Ignored evidence: `artifacts/verification/f09-compatibility-policy-20261104/`.
`upstream-status.json` records the fresh official API observations:
[Swift issue91540](https://github.com/swiftlang/swift/issues/91540) open,
[PR91672](https://github.com/swiftlang/swift/pull/91672) open/unmerged at
`0a53ffc501e5d3ac475ad36a9fc485b5623c570d`, and
[swift-collections1.7.5](https://github.com/apple/swift-collections/releases/tag/1.7.5).
[Related PR92814](https://github.com/swiftlang/swift/pull/92814) and the fresh
compiler RED(default exit1)/GREEN(target flag exit0) are recorded separately in
`related-compiler-fix.json`, `compiler-repro.json` and `compiler-repro/*.log`.
No full dependency checkout/model/toolchain was downloaded or installed.

### Bounded independent patch review

A subsequent independent read-only review completed on 2026-11-04, superseding
the earlier HTTP429 attempt that yielded no result. The reviewer verified lock,
revision/runtime and patch/source digests, independently replayed both patches
in a fresh `/private/tmp` copy, and matched all four patched digests. The copy
was removed after successful replay; this review's command output is retained
in the session, with a local `independent-review-summary.json` in the evidence
directory, rather than a surviving raw replay artifact.

The reviewer found no blocking semantic defect for **candidate reviewability**:
the selector flag affects only OrderedCollections; the five switches retain all
current `.causal`, `.array`, `.arrays` and `.none` handling and explicitly trap
unknown values as disclosed. This is a coherent proposal for the pending
maintenance-policy decision, not approval to adopt it or F09 closure.

At that review two medium adoption/closure gates remained: executable hash
enforcement and full binary validation. The preparation tool below now supplies
the enforcement mechanism with local-fixture tests; the exact historical cache
still cannot yield a proven full dependency copy. Replay provides no dependency
tests, device/simulator framework/interface builds, dual-slice/20-module/C-resource
closure or relocated-consumer/runtime evidence.

Before adopting this proposal: explicit policy decision, still pending.
After adoption: prepare full exact-revision/hash-guarded dependency copies,
run dependency tests and real device/simulator framework/interface builds,
package every declared module/C module/resource, inspect both slices, then
build a relocated source-free consumer and verify resource lookup/runtime.
Keep F09 `open/not-ready` until those checks pass. No remote publication,
tag, signing-asset mutation or dependency-cache rewriting is included.

### 2026-11-04 guarded preparation tooling

`tools/apply_mlxvlm_compatibility_overlay.py` prepares fresh copies from **Git
objects at the exact lock-bound revisions**, ignoring dirty/untracked checkout
bytes. Raw blob reads preserve files that `git archive` might drop through
`export-ignore` or alter through `export-subst`. It validates the production
lock digest, runtime version/revision, patch bytes and declared input/output
digests, and rejects undeclared patch paths, source/output overlap, existing
output, symlinks and submodules. Only declared patched files may change; an
entire resulting file-hash inventory is recorded. Failures leave no output.
Input repositories, candidate and production lock are never patched.
Git lazy fetching and terminal prompts are disabled; missing partial-clone
objects fail locally instead of triggering an implicit dependency fetch.

```sh
rtk proxy python3 -m tools.apply_mlxvlm_compatibility_overlay \
  docs/validation/f09-compatibility-candidate apple-mlx/Package.resolved \
  /path/to/existing/complete/checkouts /path/to/fresh/isolated-copies
```

The result is a preparation receipt with `policyStatus=draft-not-adopted` and
`buildStatus=not-run`. Preparing tooling does not adopt the crash/maintenance
policy or authorize building/distributing a patched binary. It copies only the
two declared packages; it is not a complete build dependency cache.

Focused local miniature-repository tests pass12/12 with real Git/patch commands.
The existing historical `f09-target-scoped-20261030` cache is correctly rejected
at exact-revision lookup because its referenced Git objects are unavailable;
no prepared output is left. Separately, the already retained four pristine
source files replay both real patches and match all four output digests. Those
four files do not establish the missing complete dependency revisions. No new
dependency fetch, build, device operation or policy adoption occurred. Raw
evidence is under ignored `artifacts/verification/f09-overlay-applicator-20261104/`.
The final complete tools suite passes262/262; Python compilation, diff checks,
nine protected-file hashes and the production lock also pass. Independent
tool review returned429 without a conclusion. No independent approval is inferred.

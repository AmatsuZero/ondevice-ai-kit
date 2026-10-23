# Apple MLXVLM source distribution and binary feasibility

## Intent and scope

Resume b988742 in the existing foundation worktree, solo. Deliver a relocatable local source consumer for the optional iOS18 MLXVLM package, using only the existing fixed dependency cache. Distinguish that result from a distributable binary. User continuation/AGENTS authorizes safe local design, implementation, verification and commits without routine approval handoffs.

## Design

Extend `tools/generate_mlxvlm_consumer.py` with opt-in `--portable-source`. Default generation remains the existing repository-backed consumer. Portable mode copies only Git-tracked package manifests, lock, Sources and Tests under `packages/apple` and `packages/apple-mlx`; the latter's `../apple` dependency therefore remains valid. Copy current file bytes, reject symbolic links, and exclude untracked files/build outputs by selecting from Git's index. The generated project uses relative local package paths and contains the existing consumer and measurement sources. Preserve the canonical JSON resource and exact lock; no model weights or dependency checkouts are included.

Write `source-bundle.json` with formatVersion=1, kind=local-source and SHA256 of every copied package file. This inventory binds the snapshot bytes, not an inference result or a binary acceptance statement. Existing/nonempty output and symbolic-link output must reject before writing. Generation does not resolve packages or access the network.

Move the generated output to another directory before an unsigned generic iOS device build with the fixed SourcePackages cache and `-disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile`. Prove package references and built sources resolve to the relocated snapshot, and that the lock and bundled metadata match. No device installation/inference is needed for source distribution.

Separately attempt a Release archive of the existing enhanced package product using the cache, without changing its manifest or upstream sources. Inspect the actual archive products and compiler diagnostics. Automatic/static SwiftPM product archival, library evolution, transitive modules and resource/Metal bundles are separate binary requirements. A successful source App build or the base ten XCFrameworks cannot satisfy them. If the bounded archive experiment fails or produces no usable framework, record the exact barrier and leave enhanced binary distribution unverified; do not invent a wrapper, download dependencies, or expand into an upstream packaging redesign.

## Alternatives

Repository-backed consumer alone already works but cannot travel independently. Repackaging all MLX dependencies as XCFrameworks has a much larger ABI/resource boundary and is premature without archive evidence. A source snapshot plus separate binary probe provides the smallest verifiable next increment.

## Validation and constraints

RED→GREEN tests execute the real generator/XcodeGen, inspect the generated project, relocate it, validate snapshot hashes/lock/resource, exclude an untracked sentinel, and verify refusal preserves outputs. Run the complete tools suite with explicit Ruby3.4.11, Python compile/static checks, then the relocated cached iOS build and archive inspection. Each Task and the final range receive author self-review; no independent APPROVE under solo execution.

Root iOS15/macOS12/Android API26 contracts remain unchanged. Preserve both handoff-protected hashes, all logs/artifacts/.superpowers, and original main worktree. No push/merge/publish, model/dependency downloads, recordings, private media, signing changes or minimum-OS/quality/resource acceptance claims. Roadmap remains 5/12, P9 active.

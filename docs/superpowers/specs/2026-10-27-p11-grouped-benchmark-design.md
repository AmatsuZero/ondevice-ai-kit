# P11 grouped benchmark snapshot design

## Intent

Produce a reproducible P11 snapshot that groups existing benchmark evidence by
device, OS, P11 capability, and actual backend. The snapshot must make measured
p50/p95 and bounded quality evidence easy to review without promoting missing
quality, memory, GPU, energy, minimum-OS, or production-model evidence.

## Goals

- Recompute supported metrics from retained raw per-run JSON rather than copy
  numbers from prose or existing summaries.
- Bind every raw input and the readiness matrix by SHA-256.
- Emit all nine readiness capabilities, marking capabilities with no compatible
  raw grouped benchmark as `unverified`.
- Preserve source-specific timing names and quality scope; do not create a
  cross-capability score.
- Reject duplicate run IDs, changed inputs, unsafe paths, and output aliases
  before writing.

## Non-goals and anti-goals

- This does not close P11, define release thresholds, or turn a synthetic rubric
  into natural-quality acceptance.
- It does not normalize Video SR, speech, identity, camera-resource, or
  unavailable LiteRT-LM evidence into the face/MLXVLM timing schemas.
- It does not download models, accept licenses, run devices, mutate raw reports,
  or add dependencies.

## Constraints

- Work in the existing `foundation` linked worktree and preserve the eight
  protected dirty/untracked paths.
- Reuse `tools/benchmark_report.py` and
  `tools/mlxvlm_benchmark_report.py` as the source validators/calculators.
- Keep raw evidence under ignored `artifacts/`; commit only the manifest, tool,
  tests, documentation, and checkpoint metadata.
- Missing quality, energy, GPU peak, or memory remains explicit in each group.

## Approaches

1. **Manifest-driven raw aggregation (selected).** A tracked manifest pins input
   paths, hashes, source schemas, and P11 capability mappings. A small tool
   recomputes supported summaries and derives the coverage gaps from the P11
   readiness snapshot. This is reproducible and does not rewrite old schemas.
2. **Force all historical evidence into one new raw schema.** This would make
   future aggregation uniform, but it would require inventing mappings for
   reports that never recorded comparable timing or identity fields.
3. **Hand-maintained JSON/Markdown table.** Smallest implementation, but numbers
   and source hashes could drift independently and would not prove regeneration
   from raw evidence.

## Data contract

`benchmark/p11/grouped-report-manifest.json` contains a readiness snapshot path
and hash plus source entries. Each source entry declares `kind`, repository-
relative `path`, SHA-256, and `p11Capability`; MLXVLM additionally declares the
expected sample count.

`tools/p11_grouped_report.py` resolves all paths inside the repository, verifies
hashes, rejects globally repeated run IDs, delegates raw validation/statistics to
the existing source reporter, and emits deterministic JSON with:

- `grouping`: `device`, `OS`, `p11Capability`, `actualBackend`;
- `groups`: source identity, run IDs, source-specific timing p50/p95, bounded
  quality, resource observations, and explicit `unverified` fields;
- `coverage`: all readiness capabilities with `reported` or `unverified`;
- `p11Gate`: copied conservative `open` / `not-ready` conclusion.

The initial manifest includes the frozen iOS and Android face runs and the fixed
30-sample Apple MLXVLM run. It intentionally leaves the other seven readiness
capabilities unreported by this grouped snapshot.

## Acceptance

- Focused tests observe RED before implementation and then pass.
- The repository Python tools suite passes in the known Ruby 3.4 environment.
- Regenerating the real snapshot yields three groups, two reported readiness
  capabilities, seven explicit gaps, and unchanged raw-input hashes.
- P11 remains `open/not-ready`; no device or publication action occurs.

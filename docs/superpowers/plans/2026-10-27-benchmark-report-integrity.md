# Benchmark report input integrity

2026-10-27 用户授权更新：已验证改动可分批提交并同步 `origin` + `jihu` 的现有 foundation 分支；覆盖本计划此前的暂不提交约束。受保护文件和未完成线程取证保留本地；不包含合并、tag 或发布。

Continue the existing Foundation Task8 and P11 requirement to retain per-run
evidence and compute reproducible grouped statistics. This is a bounded repair
of the current reporter, not a new acceptance policy or a P11 completion claim.
Use the existing inline execution, debugging and regression-test workflow.
Preserve all dirty files; no commit, publication, new dependency or device run.

## Reproduced defect and decisions

The existing iPhone P5 report has30 inference samples. Passing it twice to
`summarize` produces60, treating one observation as two independent runs.
Both native producers create a new UUID per benchmark run. The reporter must
reject any repeated `runId` in one invocation, including copies with changed
metadata or measurement values, rather than silently discard conflicts.
Distinct run IDs remain independent even when their measurements are equal.

The CLI also allows `--output` to be an input file, which overwrites retained
raw evidence. Reject identical paths, symlink aliases and existing hard-link
aliases before writing. Ordinary report regeneration at a separate path remains
supported. No changes to schema version, grouping, percentile calculation,
measurement thresholds or unmeasured fields are needed.

## Tasks

1. Add regressions against the real aggregation function and CLI: repeated IDs
   within/across groups fail; valid independent runs retain their samples;
   rejected duplicate input leaves an existing report intact; output aliases
   cannot overwrite raw input; valid CLI report generation still works.
2. Observe RED, implement minimal identity/path checks, then run targeted tests
   and the repository Python tools suite. Preserve exact failures and results.
3. Reprocess the saved iOS/Android P5 reports separately and together. Verify
   their original30-sample results and raw hashes remain unchanged. Run the
   duplicate-input CLI case against those real reports and retain its rejection.
4. Document the contract and checkpoint. Keep P4/P11, all SDK/Demo extensions,
   production models, device/quality/resource/minimum-OS gates open.

Review focus: global identity validation precedes grouping; copied conflicting
reports cannot evade the check by changing memory method or device metadata;
file aliases cannot destroy inputs; valid distinct runs must not be deduplicated
by equal content. An author verification pass does not imply independent review.

# Capability details execution ledger

- 2026-11-22: User approved the design. Source baseline `7b8065a`; no product changes yet.
- Ruling: Execute directly after design approval without another plan permission handoff; user already requested implementation.
- Ruling: Use existing caches for validation; do not duplicate models or ordinary device installations.
- Managed worktree created at `/Users/daubert/.codex/worktrees/3e3c/ondevice-ai-kit`; root checkout retains protected changes.
- Pending: helper test candidate, RED, helper implementation, integration, checks, native review, delivery.

- Layout RED: default App build succeeded; navigation back minY=136pt fails <=100pt on dedicated iOS simulator. Raw ui-red.log/xcresult retained.
- Baseline: shared107 tests, Android67 tests, lintDebug passed with existing warnings. Offline worktree build564 tasks.
- External candidates: hy3 file test accepted unchanged; first playback test rejected for invented locators; corrected playback test initial disabled-state assertion failed before settling, retained for diagnosis.
- hy4free implementation launched in OS-verified task-only write scope; no product files integrated before terminal candidate review.

- 旧ui-red使用旧framework，随后ui-source-red才是当前源导航136pt的RED；同轮hasVideo崩溃由新framework/旧Swift对象混用触发，无播放器源码改动，以新隔离DerivedData恢复。
- 原hy4free终态empty_model_response/无文件；用户指定hy4flash-free并明确授权六源码及必要摘录外发。新候选、白卡、profile/预览/测试修正及标题几何中心修正均保留独立任务raw/文件摘要，主代理精确集成。
- 首实现横屏2.30128/重复预览标识、测试LazyColumn查找顺序及空态隐藏native定位错误均保留；修正后空态CMP与播放后native分别16:9，新增标题整屏中心及不重叠断言。
- 用户指出标题右偏后，最终PageHeader以返回键自然测量宽度预留对称空间；正常/大字/横屏/375dp验证通过，无固定80dp占位。
- 最终shared107、Android67单测及framework/lint/APK通过；iOS默认App五UI测试通过；accessibility-large一项通过；Android实际播放/模式/暂停/后台/离页与四档几何布局通过，所有显示设置原值恢复，root19保护文件SHA一致。
- 验收报告：docs/validation/cmp-capability-details-20261122.md。真机质量/时序/性能、默认Android文件SR Host、键盘/无Home条的未验边界保留。
- 最终只读审核及精确commit/main普通推送收据在交付终态追加；此前Pending和首实现错误仅为历史记录。

- 最终native只读审核PASS，报告docs/validation/cmp-capability-details-review-20261122.md；两远端main刷新仍为基线7b8065a，主代理开始精确commit/fast-forward/普通推送，远端终态由delivery-receipt.json记录。

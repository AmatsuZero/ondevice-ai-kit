# 独立只读复核 — W3 arena 父端设备路由/报告评分 与 单变量诊断冻结提案

独立免费 hy3 只读审阅。仅拥有 `candidate/REVIEW.md`。不执行任何 Bash/测试/构建/设备/网络/下载；不修改除本文件外任何文件；不重新打开已接受的 wrapper/source 复核（含不援引 `Instrumentation.onStart == App 主线程` 之误：`start()` 新建线程调用 `onStart()`）。W3 / 真机 / 原生 effect 不作"通过"判定；`nativePolicyEffectVerified` 一律 `false`。

## 结论：代码缺陷是否阻断冻结

**无 Critical 代码缺陷阻断冻结。** 发现 1 项 Important（冻结门 `required` 哈希集窄于协议列举的必备件）与若干 Minor。Important 应在冻结被当作权威前收紧，但**不阻断一份本身完整的冻结**——被执行于设备的产物（APK、五个 python 模块、协议、wrapper+parent review JSON）均已被绑定。

## Critical

（无）

## Important

### I1 冻结门 required 哈希集窄于协议必备件
- **位置**：`arena-device_acceptance.py:41-53`（`verify_frozen_contract` 的 `required` 集合）。
- **触发**：`diagnostic-frozen.json` 缺少 `baseline-check_parent.py`、`source-review.json`/`.md`、Kotlin 源（`QwenArenaInstrumentation.kt`/`QwenArenaPolicy.kt`/`QwenFileActivity.kt`）或 `package.json` 中承载的 `.kt` SHA 时，`verify_frozen_contract` 仍通过（`required <= set(contract['files'])` 不触发）。
- **后果**：`protocol.md:28` 要求"父端新旧设备/评分工具及检查、独立源码/父复核收据、新源码 … 缺项一律阻断"。当前 `required` 仅覆盖 3 个 arena python + 2 个 baseline python（`device_acceptance`/`verify_report`）+ 6 个 OUT 相对件 + `wrapper-review-20261111.json` + `parent-review-20261111.json` + `diagnostic-protocol-20261111.md`。遗漏了"检查"基线 `check_parent.py`、独立源码复核收件 `source-review.json`/`.md`、以及新源码 `.kt`/`package.json` SHA。冻结验证会接受一份不绑定这些件的合同，削弱"缺项阻断"保证。
- **严重程度说明**：被执行产物（APK / 五个 python 模块 / 协议 / 两份 review JSON）均被绑定，故非"执行完整性"缺陷，而是 provenance / 门控强度缺陷。建议将 `required` 补全覆盖上述件后再把该冻结视为权威；这不影响一份由正常流程完整填充的冻结。
- **不阻断理由**：冻结由正常流程在两复核后创建；即便验证器较宽，只要填充件齐全即完整。属校验器稳健性缺口，非阻断性代码缺陷。

## Minor

### M1 `verify.main` 在缺 `allocator-policy-collected.json` 时未结构化失败
- **位置**：`arena-verify_report.py:57` `json.loads((root/'allocator-policy-collected.json').read_text())`。
- **触发**：OUT 中 `device-report.json` 存在但 `collect_policy` 产物缺失（如手工组装、或 collect 动作未跑）时抛 `FileNotFoundError`，而非返回 `executionAccepted=False`。
- **后果**：违背"失败不得误接受、但应允许同 run 恢复"（评审焦点 2）。建议 `exists()` 守卫并给出结构化失败。

### M2 exclusive-create(`'x'`) 阻断同 run 恢复重跑
- **位置**：`arena-device_acceptance.py:96`（`allocator-policy-collected.json`）与 `arena-verify_report.py:66`（`parent-arena-acceptance.json`）；基线 `baseline-device_acceptance.py:29` `save()` 亦用 `'x'`。
- **触发**：`collect_policy`/`verify.main` 中途因瞬时 adb/IO 失败，于同一 OUT 重跑 → `FileExistsError`。
- **后果**：与"同 run 恢复"存在张力；与 no-replay 哲学一致但阻碍恢复。建议写临时文件后改名或跳过已存在。

### M3（记录，不改）私有根解析假定
- `collect_policy` 经 `adb readlink -f no_backup` 取 `private_root` 并断言严格等于 `'/data/user/0'/APP'/no_backup'`（`arena-device_acceptance.py:86-87`），与 `QwenArenaPolicy.kt:63-71` 的 `assertNoSymlinkAncestry` 及 `prepare` 要求 `no_backup` 为真实目录一致；记录以供完整，无需改动。

### M4（已知限制，非本审阅代码范围）
sameUID TOCTOU / 独占 alias、采样 maxRSS 非 true peak、ORT 内部策略 effect 仍 `false`/未证、annotation-negative 缺人审、生产通知/运行时传递许可/发布门仍开——按 protocol 携带，不视为代码缺陷。

## 测试诚实性核对（焦点 5）
- **17 个父测试** = 8 个继承 FILE（`baseline ParentChecks`：`binding_release_counter`、`complete_fixture`、`inflight_case`、`missing_collected_report`、`nonempty_silence`、`nonterminal_persisted_zero`、`resource_boundaries`、`stopped_host`）+ 9 个 arena（`ArenaParentChecks`）。
- **9 个新增**：`metadata`(`policy_metadata_mismatch`)、`missingfreeze`(`no_frozen_contract`)、`newidentity`(`run_identity_resource_gates`)、`boundedcollection`(`policy_collection_bounded`)、`essentialhash`(`frozen_contract_essential_hashes`)、2 个 actual main-entrypoint 回归(`arena_main_missing_report`、`arena_main_failed_policy_collection`)、`report_never_changed`、`nonterminal_prepolicy_failure`。
- **两处边界先 RED（2 失败）后 GREEN**：
  - `boundary-red`（15 测试，2 FAIL）：`test_frozen_contract_cannot_omit_essential_hashes`（断言未触发）与 `test_policy_collection_is_bounded_and_rejects_extra_byte`（旧 `cat` 路径，未用 `head -c 21`）。对应代码已改为 `head -c 21`（`arena-device_acceptance.py:91`）并收紧 essential-hash 断言（见 `parent-boundary-red-stderr.txt:18-34`）。
  - `entrypoint-red`（17 测试，1 ERROR）：`test_arena_main_missing_report_is_structured_without_scoring` 因 `verify.main` 缺失字段 `countsAreExactTerminalEvidence` 触发 `KeyError`（`arena-verify_report.py:53` 现已补，见 `parent-entrypoint-red-stderr.txt:20-26`）。
  - `final-green`（`parent-final-green-stderr.txt`）：`Ran 17 tests ... OK`；`parent-final-green-result.json`：`exitCode 0, tests 17, nativeCalls 0, deviceOperations 0`。stdout 第 4 行含 `readLimitBytes:21, boundedReadBytes:21, fileBytesMatch:false`（21B 变异输入被正确拒），证明有界读取与"非原生 effect"断言成立。
- **`regressions.json`**：baseline 8 OK、metrics 31 OK、arena 15 OK（arena 15 为边界期快照；权威验收为 17 测试 `final-green`）。原始 8 parent + 31 metrics 套件均重跑通过。
- 证据均为合成/host；**无 on-device 声明**。

## 各评审焦点核对
1. **设备适配器仅改 7 全局**：`OUT/APP/TEST/RUN/STAGE/REPORT/RUNNER`（`arena-device_acceptance.py:106-112`）后委托 `baseline.main`；执行前先 `verify_frozen_contract()`（:104）再断言 `currentUser==0`（:105）后才进 Android 命令。旧 run 不重放；`prepare/run/collect/restore` 全走基线原 `force-stop`/`transfer`/`restore` 逻辑，未重实现。无新模型/麦克风/AiAi/网络/默认/生产操作。
2. **精确 20B 配置**：`POLICY_BYTES=b'EnableCpuMemArena=0\n'`（`arena-verify_report.py:10`；与 `QwenArenaPolicy.kt:50` 一致）。`collect_policy` 经 `head -c 21` 有界读取（:91），`private_root` 取 canonical alias `no_backup`；`nativePolicyEffectVerified` 恒 `false`；`receiptMatches` 失败则 `fileBytesMatch` 保持 `false`，不误接受，且同 run 可恢复。
3. **评估器 deepcopy**：`normalized=copy.deepcopy(report)` 仅改 baseline 的 `cpu` 字面（:37-39），原始报告字节不变；实际 provider 经 `policy_matches` 独立绑定（:40）；`verify.main` 追加 `policyFileCollectedExactly`（:62）形成报告层 receipt + 设备层文件字节双重绑定。政策不匹配或采集失败 → `executionAccepted=False`。基线 full65 coverage / counters / cleanup / errors / thresholds 全部保留（`baseline-verify_report.py`）。非终态零非精确终态计数（`exact_counts = terminal & host & counters`，:98）。
4. **冻结必备哈希/身份不可省略或放松**：`automaticRetry false` 与资源门固定（`budget_reason` `baseline-device_acceptance.py:34-41`，arena 复用）；缺冻结即无设备操作（`test_no_frozen_contract`）。正常流程两复核后建 `run-plan.json` + 实际哈希绑定收据。
5. **测试诚实性**：见上节。

## 范围限制
- 仅父端新增代码；wrapper / source+package 复核已接受（见 `source-review.md`/`source-review.json`/`package.json`），不重开。
- 不判定 W3 / 设备 / 原生 effect 通过；不执行任何动作。
- `package.json` 中 `.kt` SHA、APK SHA、`exactRunner` 已由 wrapper 复核绑定，本审阅直接采信。
- 已知限制（M4）按 protocol 携带，不计入代码缺陷。

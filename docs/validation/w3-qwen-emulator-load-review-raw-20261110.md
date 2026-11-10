# 父脚本模拟器补充 只读评审 — `device-parent.py` (emulator)

评审性质：只读、范围限定于用户新增的 Android 模拟器补充。仅评审 `device-parent.py`(emulator 版) 与 `parent-adaptation.json`，对照 `emulator-protocol.md` / `physical-protocol.md` / `README.md`。
**未执行脚本、未构建/运行模拟器、未触碰 probe 源(Kotlin/JNI/APK)。** 本报告是唯一交付物。

---

## 1. 供给上下文限制（按要求声明，非缺陷）

- 未提供原始物理版 `device-parent.py`，无法做逐字节 diff；JSON 中的 `before` 值按父代理声明采信，仅能验证 `after` 与脚本实际内容一致。
- `baseScriptSHA256` / `emulatorScriptSHA256` / `initialEmulatorScriptSHA256` 对应的源文件不在供给上下文，无法独立核验哈希。
- `EVIDENCE`(`.omx/artifacts/w3-model-reselection-20261110`) 下的 `production-before.json`、`model-manifest.json`、APK、`sourceFingerprint`、ONNX 哈希均不在供给上下文，未核验。
- AVD 创建配置(4GiB guest RAM / 2 核 / 关音频·摄像头·快照)不在脚本内，无法从供给上下文核验。
- `adb emu avd name` 在目标模拟器上的真实输出未执行；仅按其声明 `observedRaw` 与脚本断言做逻辑一致性检查。

上述限制不影响对“脚本逻辑本身是否忠实实现了已授权模拟器改造”的判断。

---

## 2. 关键结论：无 Critical / 无 Important 阻断项

模拟器补充在目标隔离、单次独占、精确别名停止、PID/startTicks 观测、1.5GiB/512MiB/120s 停止门、采集/恢复完整性、纯模拟器诚实上报、精确替换与 CRLF 修正八个维度均**通过**，且改造范围严格限定于已授权集合。详见第 4 节逐项核对。

---

## 3. Minor / 已有界定性限制（非缺陷，非阻断）

1. **运行前 2 GiB MemAvailable 硬门（L176-177）**：`assert available >= 2*1024*1024*1024`。这是执行前置条件而非停止门；若宿主机内存紧张导致模拟器 guest `MemAvailable` 启动即低于 2 GiB，run 会在采样前被阻断。属保护性设计，非脚本缺陷，但为环境前提。
2. **观测失败即保护性停止（L208-212 + L213-216）**：单次 `adb`/`run-as` 异常会被记为 `resource_observation_failed:...` 并触发仅对 APP 的 force-stop。与物理合同“采样受限则不提交加载，先解决观测前置”一致，属设计意图。
3. **未测 true peak（host-result.json L225 `truePeakMeasured: False`）**：采样为周期 RSS，非峰值。emulator-protocol 明确“不把虚拟机分配的 4GiB 记为手机预算通过”。属设计。
4. **本批不可替代 TECNO 真机 1.5GiB 验收**：真机验收仍 pending，本补充明确为 supplemental（L266 `supplementalEmulatorEvidenceOnly: True`）。缺失真机证据不构成本补充的缺陷。
5. **force-stop 仅作用于 APP（L215）**：依赖 instrumentation 在 APP 进程内运行（probe 未改动）。若未来 probe 设置独立 `android:process`，pidof/force-stop 粒度需重审——但 probe 不可变，超出本补充范围。

---

## 4. 用户八点核对（逐项）

1. **目标隔离 — PASS**
   - serial 硬编码 `emulator-5576`（L61）；`ro.kernel.qemu==1`（L139）；精确 AVD 名（L140）；identity 三元组 `Android SDK built for arm64`/`36`/`arm64-v8a`（L79）；`ActiveInstrumentation` 不存在（L80）。四重防误投实体机。

2. **独占单次处理 — PASS**
   - `save()` 用 `'x'` 排他创建（L28-31），任何动作重跑即 `FileExistsError`，强制单次；identity 要求无 ActiveInstrumentation；run 断言报告不存在（L175）；`automaticRetry: False`（L179, L226）；仅一次 `am instrument`（L182-183）。

3. **精确别名停止 — PASS**
   - 停止分支仅 `am force-stop APP`（L215），注释“No other package is stopped”；停止前先 `verify_installed()` 确认未篡改（L215）；restore 仅卸载 `[TEST, APP]`（L255）。无其它包被停止。

4. **PID/startTicks 资源观测 — PASS**
   - `pidof` + PID 变更断言（L189-193）；`startTicks` 取自 `/proc/PID/stat` 字段 22：经 `rindex(')')+2` 剥离 `pid (comm)` 前缀后 `split()[19]` 即 start_time（L194-196），索引正确；同 UID `/proc` 可读性于 prepare 预先验证（L166-167）；以 startTicks 相等防 PID 复用（L197）。

5. **保留 1.5GiB/512MiB/120s 停止门 — PASS**
   - `budget_reason`（L34-41）严格为 `1536*1024*1024` RSS、`512*1024*1024` 可用、`120` 秒，与物理版一致，未放宽。

6. **采集/恢复完整性 — PASS**
   - 报告双读一致 + sha 校验（L231-235）；runId/sourceFingerprint 断言（L237）；`streamsCreated/nativeDecodeCalls/microphoneStarts/networkRequests` 全为 0 断言（L238）；模型哈希重核（L244-246）；ordinary 包/模型前后一致（L248, L262）；staging 仅删除 manifest 绑定项（L256-261）；恢复至 Launcher（L263）。

7. **纯模拟器诚实上报 — PASS**
   - `truePeakMeasured: False`（L225）；`supplementalEmulatorEvidenceOnly: True`（L266）；`emulatorLauncherReturned: True`（L266）；无任何真机声明。

8. **精确替换与 CRLF 修正 — PASS**
   - JSON `exactChanges` 八项均与脚本吻合：L11(ROOT)、L12(OUT)、L16(RUN)、L61(serial)、L79(identity)、L139-140(qemu+avd)、L263(restore am start)、L265-266(restoration 键)。
   - CRLF：`observedRaw` 为 `W3_Qwen_Load_20261110\r\nOK\r\n`；`text()` 经 `.strip()` 后为 `...\r\nOK`，`splitlines()` → `['W3_Qwen_Load_20261110','OK']`（L140），正确处理 `\r\n`，且 `exactNameStillRequired` 保留。逻辑正确。

---

## 5. 范围变更检查

- 改造严格限定于已授权集合：evidence root、serial、identity、runId、qemu+AVD 门、恢复至 Launcher。
- 未改动：probe 源、budget 门、采样逻辑、transfer 完整性、collect 逻辑、force-stop 粒度、报告断言。
- `physicalRunReservationChanged: false`（JSON L40）；`sameAPKAndSource: true`（JSON L39）。
- **未发现越权范围变更。**

---

## 6. Go / No-Go

- 模拟器补充**无 Critical / Important 阻断项**，逻辑忠实实现已授权改造，八点核对全过。
- 建议在“单次模型构造”前提下 **GO**，但受第 1、3 节供给上下文限制与第 3 节界定性限制约束。
- 物理 TECNO 1.5GiB 验收仍为真机质量/资源的门槛证据；本补充明确为 supplemental，不替代真机。

# W3 系统资源请求与路径修正 — 2026-11-10

**最新范围裁定：用户已选择将当前 TECNO 限制纳入验收。R1–R3 在当前 TECNO 配置按已知限制完成范围裁定，功能失败/未执行保留；不再等待换机、解锁或资源重试。** 见[正式裁定](w3-tecno-system-asr-scope-disposition-20261110.md)。以下资源实测与原失败保持不变，R4/R5仍open。

当前：**已执行获批的一次 en-US SDK 资源请求，终态失败，实际数字错误15；资源ready未证实。** 跨首次失败/只读诊断/修正版共一次SDK调用、零麦克风；无重试或备用API调用。普通App/模型/默认服务及完整包清单已恢复。

原准备记录见[preflight](w3-system-resource-provision-preflight-20261110.md)。该文及原冻结合同中的“授权待答/尚未安装”是准备时状态，后续由本记录补充，不覆盖原收据。

首次 run `w3-asr-resource-enUS-20261110-01` 在 `onCreate` 的 `reservation` 阶段返回 `FAILED / java.lang.SecurityException / reportWriteSucceeded=false`。没有进入 `start/onStart`，没有构造或调用 SDK wrapper；**下载0、麦克风0**。主机 `am instrument` exit0 不能当成功，父 collector 对缺失报告的断言也失败，原件保留。两个临时别名和精确 stage 已移除，完整包清单、普通 App APK/四模型和默认服务均与执行前相同。

随后免费 hy3 交付独立只读路径诊断。新 test APK 不含 SDK/下载/录音调用，只在 App 进程中读取固定路径并输出 Bundle：

- `filesDir.absolutePath` 为 `/data/user/0/<probe>/files`，`canonicalPath` 为 `/data/data/<probe>/files`。
- 报告路径的 absolute/canonical 不相等，但 canonical 报告仍位于 canonical 报告目录内。
- `status=DIAGNOSTIC`、`INSTRUMENTATION_CODE=-1`，SDK0/麦克风0。诊断别名/stage 已恢复，普通模型/路径/默认值再次核验相同。

这证明首次路径相等检查错误拒绝了 framework 提供的合法根目录。shell `run-as readlink -f` 当时仍返回 `/data/user/0/...`，不能把 shell 观察代替 App 进程的 `File.canonicalPath`，也不能据此断言具体 OEM mount/软链实现。

免费 hy3 的单行修正只将 `targetContext.filesDir` 改为 `targetContext.filesDir.canonicalFile` 后再附加固定子目录，保留对子目录/报告的软链与逃逸检查。原候选的合法根 alias 用例真实 RED；修正版 **11 项实际 reserve 路径断言**及原 **31 项完整探针故障断言** GREEN。fresh offline assemble main/test + lint 通过，lint零error/10warnings。六份生产源逐字节不变，main/test签名通过、无 RECORD_AUDIO/native libraries。

修正版候选 SHA-256：`db6a5ccd48b6714251446a711348086c692be3467382b8000329a1a52588d57c`。
main APK 与首次相同；新 test APK SHA-256：`3c554b5a68646464ae64d58c0d287a4a53131253d78218c1be6d2a32e1edf026`。
补充合同 `w3-asr-resource-enUS-20261110-02` 将**跨全部尝试最多一次实际 SDK download**绑定到原授权及首次0调用证据。10:54 gate 实测 Awake但锁屏，未启动。用户随后明确回复“已解锁，执行资源请求”，父重查解锁后执行一次，未重复请求下载批准。

## 获批单次请求的真实结果

报告 `w3-asr-resource-enUS-20261110-02`：`terminal=true/status=failed`，`downloadInvocationCount=1`、`microphoneStarts=0`、`sdkCallCompleted=true`，`errorClass=io.ondeviceai.systemasr.AsrResourceException`、`resourceErrorCode=15`、`readyClaim=false`、`progress=[]`。前台确认成功；teardown观察到`isFinishing=true/failed=false`，没有suppressed cleanup错误。instrumentation明确FAILED且报告写入成功；主机exit0不改变真实失败结论。

同run报告双读逐字节相同，SHA-256 `580d628a6daffd33c3319d1d15195fc51a92f7938a09c39a7e1c9b34cf161f7c`；源码、APK、合同和生命周期/恢复共**30项父证据检查**通过。移除本次两个alias和精确stage后，完整包清单、普通App APK/四模型及两项默认服务与before完全相同，返回普通App。

[Android官方错误码](https://developer.android.com/reference/android/speech/SpeechRecognizer#ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS)将15定义为`ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS`：服务不支持监听模型下载事件。它不是网络/VPN失败的证据，不证明资源INSTALLED、PENDING或所有provisioning路径不可能；本次没有资源ready证明，也不能称麦克风通过。官方`ModelDownloadListener`及`triggerModelDownload`文档已由Context7查询并保留在本次artifact中。

原授权的一次调用已经消耗，按合同**停止，不自动重试、不调用无listener备用API、不改变网络或默认服务**。没有再次开启麦克风。资源前置当时阻断R1/R2/Android R3；用户随后已明确接受当前 TECNO 的已知限制，完成这三项的范围裁定，未改写任何功能结果。

证据分别位于：

- `artifacts/verification/w3-resource-provision-20261110/`：原授权、首次失败、0调用裁定和恢复。
- `artifacts/verification/w3-resource-path-diagnostic-20261110/`：helper原件、APK、App进程路径及恢复；build成功后父首次复制使用错误输出文件名，原错误保留，随后从唯一实际APK收集，未重建/重跑诊断。
- `artifacts/verification/w3-resource-root-fix-20261110/`：单行候选、RED/GREEN、完整构建/签名、补充合同及run02准备。

W3仍open：固定前缀诊断与AMI逐片观测已完成，中文/远场质量没有获批通过门槛，真实失败保留；R5仍引用W8预算裁定。本次修好探针并取得资源接口的真实失败证据，不能据此勾绿整包。

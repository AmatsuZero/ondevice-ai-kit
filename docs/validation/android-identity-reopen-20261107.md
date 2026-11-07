# Android Identity：20次持续重开真机验证

固定SDK提交`636a6eb8854db5dff74244c42c3ce0e82bd2be9f`，TECNO KM9/API35，
同一已批准NASA图像SHA88431cd9…与既有YuNet/SFace模型；不新增采集、注册或embedding持久化。
agy免费模型直接编写独立Kotlin探针并修正审查问题，源码最终SHA`d60781e7e29fa2f145cda8f414dbc96691fa7b261e645507118217f2fce196f7`原样集成。
cbc hy3编写的严格reducer此前15聚焦/221独立变异/578完整工具测试通过；本次真实报告也由同一reducer接受。

## 实际验收

证据`artifacts/verification/android-identity-sustained-reopen-final-20261107/summary.json`，
唯一runId为`identity-reopen-20261107-20cycles-a1`，设备PID11557。
20周期各至少30秒，总600908.639ms；每周期新ModelStore安装模型、fresh backend打开、
2warmup+8measurement、closeAndWait、立即/1秒后RSS、移除模型并确认ABSENT和lease释放。
总200调用，160测量，462处RESUMED前台检查全部达到；自有工作目录清理、源输入与调用者输入不变。

| 指标 | 实际结果 |
| --- | --- |
| 测量耗时nearest-rank p50/p95 | 128.708 / 139.669 ms |
| 全200调用进程VmRSS采样峰值（含warmup） | 570,290,176 B |
| 1秒关闭后RSS最后−首次 | −5,492,736 B |
| 总时长 | 600,908.639 ms |
| 终态原始报告SHA256 | aee9c57b1cee0e8f1d203bb33c3ff75c9cf1f02b921455f484f1de3ac60fc167 |

这是固定输入、paced20周期/10分钟、带idle MainActivity的进程级采样。不能据此推出无限期/满载压力、
SDK独占内存、真实峰值、零泄漏、GPU/能耗、质量、身份/PAD准确率或生产预算；budgetPassed和productionReady均false。

## 失败、构建与恢复

首版hy4free任务自然上游空响应、无交付；用户选agy免费接手。agy初稿经父读审后修正flag清理吞错与测量时间改写；
第二处主线程清理错误传播缺陷依据本机SDK源码确认，再由agy最小修正。实际工具权限/缺上下文错误均保留，
无自动任务耗时超时、静默付费fallback或主代理替写探针实现。

最终测试overlay397任务/49秒构建过；测试APK215574B、SHA0f1564cc…，签名、DEX、1046冻结源码及主APK摘要检查过。
34项离线编排边界检查过。主APK不重建；以既有rollback+两段literal重建暂存，原App/测试包/runner/25私有摘要/
四SR备份/权限/TTS先验证。host运行提前返回且首次collector取得非终态，原失败及attempt1保留；
回读同PID/同runId继续到20周期，未重发推理。终态attempt2通过后恢复原main/test/runner/25私有文件/四SR/权限/TTS，
精确清理本次报告、暂存及work路径，普通App已返回；保护文件和Apple生产lock保持。

可复核`actual-summary.json`与`summary.json`来源摘要；旧35次resource报告与11个grouped benchmark均不改。
本次关闭此固定协议的10分钟持续重开验证子项，P4/P11与10/12历史里程碑保持；生产阈值、授权数据等仍开放。

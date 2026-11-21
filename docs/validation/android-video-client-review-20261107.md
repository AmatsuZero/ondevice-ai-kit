# Android 视频客户端限定审查（2026-11-07）

cbc hy3 对 `VideoContracts.kt`、`VideoProcessingClient.kt` 和原有客户端测试进行了仅供给上下文的独立静态审查。
自然结束260.577秒，exit0、result success；没有重启长任务或设置耗时终止。
审查只覆盖固定摘要的三文件，不含实际媒体后端、整分支、设备或发布批准。

## 审查建议与实际验证

唯一一条low建议要求把进度过滤中的 `<` 改成 `<=`，以去掉同帧数回调。
主代理未采纳：`completedFrames` 是累计值，`totalFrames` 可以从未知变为已知；接口没有逐次回调均须严格递增或唯一的要求。
新测试 `sameFrameProgressCanUpdateTotalFrames` 发送 `(3, null)` 再发送 `(3, 8)`，要求保留两次更新。

- 原生产实现：聚焦测试实际执行1/1通过。
- 临时应用建议：相同测试实际执行1/1失败，输出仅剩 `(3, null)`，丢失总帧数8。
- 精确恢复生产源码后：整个video模块18项及Demo56项实际执行通过；LiteRT SR后端4项缓存通过，不计新执行。
- video lint无error、1项既有`NewerVersionAvailable` warning。没有新增产品缺陷修复、设备运行、benchmark或质量样本。

这里的RED证明助手建议会破坏既有行为，不是原实现存在缺陷的证据。
第一次受限运行因Gradle缓存锁权限失败，测试未启动，单列为启动器失败；获准缓存访问后的结果才是有效执行。
保留的测试仅扩展fixture的可选总帧数及增加一个回归，没有生产代码修改。

审查正文称六项测试，但供给的原文件实际有七个 `@Test`，不能据此推断遗漏或完成率。
其跨协程异常必然保持对象身份的说法也不采纳；协程恢复可能复制异常，参见踩坑记录第14节。
新测试在审查后编写，由主代理实测，未声称得到独立审查。

## 证据与边界

原提示、命令、stdout/stderr、会话身份、三文件摘要、完整结果与逐项处置：
`.omx/artifacts/cbc-video-client-review-20261107/`。
助手索引：`.omx/artifacts/ask-cbc-video-client-review-20261107.md`。
原实现通过、建议失败、恢复后全模块结果和lint：
`artifacts/verification/video-client-review-20261107/`；只读入口 `freeze.py --verify`。

9个保护路径和生产Apple lock保持；未操作普通App/模型、下载、采集、上传或签名。
qoder的此前403及OpenCode额度等待边界不变，没有重试失败线路。
P11最新指针见 `benchmark/p11/grouped-report-manifest.json`；10/12、P4/P11、原质量/资源/校准与发布缺口均不晋级。

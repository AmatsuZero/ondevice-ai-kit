# F10 绑定分数协议与离线指标

2026-11-07，新增 `tools.identity_calibration_scores` 和小型统计模块，先验证现有数据
manifest，再绑定其规范化摘要、模型／预处理声明、任务和 split。仅计算调用者提供的固定
阈值结果，不拟合阈值、导出策略或取得真实数据。原 manifest CLI 复用严格 JSON loader。

```sh
rtk proxy python3 -m tools.identity_calibration_scores MANIFEST.json SCORES.json
```

manifest 内文件引用相对其目录；score 格式见
[设计](../superpowers/specs/2026-11-07-calibration-scores-design.md)。
报告只保留聚合、声明绑定和输入摘要，不输出样本／主体ID、媒体路径或逐条分数。

| 协议 | 校验及统计 |
| --- | --- |
| verification | 同 split、正确 enrollment/probe 角色、唯一比较对；真假配对由主体声明推导；支持提供的比较子集，明确不保证完整试验设计 |
| identification | 完整 gallery × probe 矩阵；FPIR 按未知 probe 搜索计数；不同身份同最高分拒绝，同身份多模板并列有效；任意缺响应单元使该搜索为 nonresponse |
| passive PAD | 所选 split 的 presentation 每条恰一次；按 PAI 分组 APCER、BPCER；任一 PAI 无响应时最差组指标为 null |

比较包含阈值相等，支持两种分数方向。错误率显式命名 `OnResponse`，以有响应试验为
分母；nonresponse 另列且以全部试验为分母。空分母为 null。它不声称符合未指定的
ISO／NIST 失败处理协议。calibration split 要求声明 local-calibration，test 要求
local-evaluation；拒绝错绑、漏项／重复、跨 split、bool／非有限／不可表示数值和非法 JSON。

21项新增合成测试加23项既有 manifest 测试，聚焦44/44。完整 tools **510/510**、
48.281秒，源码／测试前后摘要、九保护路径和生产 Apple lock 保持；收据为
`artifacts/verification/f10-calibration-scores-20261107/tools-suite-final.json`。
先前17项独立缺实现 RED，以及统计候选额外字段的两项真实 RED 均保留，没有把重复发现
的 imported TestCase 算成新增测试。

cbc hy3 统计候选231.987秒完成，父修正严格字段检查；Copilot 最终限定源码审查38.11秒
完整返回、无 finding／工具事件，输入摘要匹配。另一个 cbc hy3 CLI smoke 候选119.325秒
完成，原候选因漏 test split 实际失败；父补独立合成主体／媒体、全输入摘要和原始命令／
输出收据后，verification／identification／PAD 三项成功及一次有效十六进制错绑拒绝均通过。
原失败、候选和父处置保持，不称候选原样通过。

CLI 证据：`artifacts/verification/f10-calibration-scores-20261107/cli-final/receipt.json`。
验证场景 FAR＝FRR＝0.5；识别场景两已知搜索一对一错、一个未知搜索误接受，FPIR＝1；
PAD 场景 BPCER＝0.5、print APCER＝1、replay＝0。这些是手算合成结果，不能作为实际质量。

所有报告保持 `validationStatus=pass-bounded`、`calibrationStatus=unverified`、
`thresholdOrigin=caller-supplied-unverified`。声明检查不能认证法律授权、实际分数来源或标签。
授权数据、总体／独立试验、ROC／DET／置信推断、生产阈值／策略及PAD／识别验收继续开放。
没有下载、采集、装机或阈值／生产依赖变更；本项不关闭F10、P4／P11或release。

# W3 Mac Qwen 限定复核及主代理裁定

免费 CBC hy3 完成一次独立的 supplied-context 代码/证据复核，261.167s、完整 result、
0工具调用，未发现 Critical/Important 缺陷。来源runner由此前不同任务交付，本次没有生成实现。
原native复核的429仍为无结果，没有重试或覆盖。此复核不覆盖Android、真实native析构或发布许可。

[原样复核](w3-qwen-host-review-raw-20261110.md)、[验收收据](w3-qwen-host-review-accepted-20261110.json)。
主代理在接收时重核四份被审文件摘要一致，原始report及运行JSON协议摘要也与冻结值一致。
fresh runner14组故障检查和原scorer31项测试通过，均没有触发新的真实识别。

## 原报告的解释性纠正

复核助手收到的是完整报告的pretty JSON与声明的摘要，不是原始report字节，也未收到运行协议JSON。
它的“独立核对原始report哈希”只能理解为声明一致性；精确原始字节SHA由主代理另核，不能冒充
独立字节验收。原文提及`qualityVerdict`/`productionReady`等字段并非原报告实际字段，主代理
仅采信实际`androidAcceptance:false`和`productionPromoted:false`及文档明确边界。
没有因复核结论放松任何质量或资源门。

## 逐项裁定

| 观察 | 主代理裁定及未解决时的代价 |
| --- | --- |
| M1，假设原生auto可能返回空白串 | 协议明确只拒绝严格空字符串，空白不trim，14组测试已覆盖。保留未观察风险，不更改管线/重跑调参；若真实环境出现，将需要新的独立协议和证据，不能外推13挑战证明一切非语音 |
| M2，float清零不擦除整个批次PCM bytes | 本轮全部为公开文件，runner只声明float清零和引用清理，不声明全媒体擦除。保留内存卫生限制；不得用于私人音频安全擦除保证 |
| M3，decode计数表示dispatch尝试 | 与native调用边界及故障检查一致，非缺陷；抛错的decode仍计数，create/setter/accept失败不计 |
| M4，未提供运行协议JSON | 限定复核覆盖范围；主代理核原始协议/report摘要，不写成独立复核已验所有输入字节 |
| M5，中文word指标不能当主门 | 原门与结果均只用CER42/590；保留原scores不删改，不使用中文空格词指标裁定 |

当前Mac独立确认及限定复核可支持进入手机候选；W3仍open。
下一步Android加载探针、两阶段实际质量/资源与最终发布交付物另验。

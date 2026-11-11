# W3 arena 外壳独立复核父裁定

独立免费 hy3 只读审阅交付完整报告，287.633s 自然结束；报告 14508B，SHA `0dfa2582be0c8db58fbaf4f870f46f2a0daf386ed4d28b7eecad59cc91e0b41f`。[原文](w3-qwen-arena-wrapper-review-original-20261111.md)保留，未改写助手结论。

父裁定：没有阻断当前源码/实包准备的 Critical/Important 代码缺陷。实际构造参数、receipt 绑定、落盘后篡改/失败与前台门、全部快照、原生命周期错误/清理及独立包身份均有对应 host 或实包证据。复核只审阅供应的源码和证据，没有运行测试、重新计算 APK/hash 或执行设备；父端验收负责这些检查。

原文需要订正的表述：

- 原文称“10 个 write 点”，但列举及实际源码均为九个；九个均使用 augmented snapshot。
- `d3d1a844…` 是新外壳的 sourceFingerprint；旧 FILE 的是 `ef466519…`。两者不同，不能将相同 PCM 摘要说成与 sourceFingerprint 相等。父逐项核对的是旧/新 65 资产及未修改共享模块的相同字节。
- 原文把旧内存记录称为 peak observation；它只是采样 maxRSS，未测 true peak。host doubles 也不证明 native 消费或效果。
- 原文的关键机制判断可由供应源码/收据支持；部分行号和范围描述不作为额外实测证据。未供应旧 scaffold 摘要的缺口由父已有 `host-candidate/base-hashes.json` 及当前文件核验补充，不能说助手独立重算过。

Minor M1/M3 是请求元数据的说明建议，无代码变更建议；按明确的 `nativePolicyEffectVerified=false` 与准备前 provider=null 记录即可。M2 的七项 lint warning 保留，不扩展到生产配置修改。

已知 verify→构造期间的同 UID TOCTOU 限制继续有效，依赖独占隔离 alias。此复核未覆盖新的父端设备路由/报告评分工具，后者另做 RED/GREEN 和独立复核。没有新设备运行、额外 smoke 或旧 run 重试授权来自该报告；新诊断仍须先冻结合同、再过实时设备门。W3 保持 open。

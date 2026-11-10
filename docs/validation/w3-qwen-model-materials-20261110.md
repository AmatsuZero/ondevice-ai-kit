# W3 Qwen 模型许可与归属材料本地验收

已组装可随源码查看的[材料目录](../../probes/qwen-model-materials/393f8a14/MODEL-NOTICES.md)：
许可证全文、模型/供应方声明、六份运行文件的 size/SHA、归属与转换未知项，以及原始公开来源快照。
本项完成材料准备；生产模型/runtime 包的通知保留与独立发布复审仍未验收，W3 保持 open。

| 检查 | 本次证据 |
| --- | --- |
| 实际模型 | 六文件重新流式核 SHA，共 987015347B，与原供应收据一致；未修改模型 |
| 许可证 | Apache-2.0 全文 11343B，SHA `a44a6081c73ad75f0255bb2bb5cab74ef1829565a895a24e53a4f11290ab7655`，逐字复制既有 QwenLM 固定 commit 来源 |
| 许可声明来源 | 模型卡与 ModelScope 元数据声明 Apache-2.0；全文取自代码仓库。没有声称下载的权重包原含该文件 |
| 归属文档 | 外部免费 hy3 实际交付；定点订正一处来源表述、去除内部指令并改为实际包内容措辞，父验收确认仅这些改动 |
| 可携带材料 | 24 文件；归属文档 8 个相对链接全部解析；21 份既有来源 SHA 重核通过 |
| 本机压缩包 | 36696B，SHA `571e7b16c5b566e67da23ebb10223f3c00f62d332be5e920ba285f71000eb515`；24 成员逐字核对通过。未上传该包 |
| 交付边界 | 材料不含模型二进制、运行时库、导出脚本或测试音频；不修改已冻结的 Android APK、源码绑定或加载合同 |

首候选与订正分别自然完成于 67.530s、76.064s；两次均有完整 success result、实际文件及独立写入范围检查。
原工具错误仍保留：大篇幅 pitfalls 首次读取超限，以及订正任务在写入前读取尚不存在的候选文件。
没有将 CLI exit0 当交付证据，也不宣称助手执行零错误。

转换所用精确原始权重 revision 仍未知；固定导出 commit 与输出 SHA 不足以证明该 revision。
供应仓库声明支持模型来源，代码缺 LICENSE 不等于权重必然无许可。详见[原供应链核对](w3-qwen-supply-chain-observed-20261110.md)。
最终生产分发包仍须核实际通知保留、运行时传递性许可和发布复审；此材料包不代替这些验收。

Android load-only 探针仍已构建且未执行。本次末尾 fresh preflight 为 TECNO Awake=false、unlocked=false、
新 alias 不存在，保护文件与包摘要一致；没有安装、构造、stream、decode、麦克风或 AiAi 请求。
原解锁请求继续有效，下一步仍按[冻结加载合同](w3-qwen-android-load-protocol-20261110.md)单次运行，不重新请求授权。

[机器收据](w3-qwen-model-materials-20261110.json)保留全部材料摘要。
原件及本机包位于 `.omx/artifacts/w3-model-reselection-20261110/redistribution-materials/`；
两个 helper 的 prompt、原始事件、sandbox 检查和父裁定分别留在同证据根的 `helpers/` 下。

材料已本地提交`ceead2e`，并随模拟器加载结果同步两端Issue/Wiki；
[逐端回读收据](w3-qwen-emulator-materials-sync-20261110.json)。没有推源码分支或上传模型。

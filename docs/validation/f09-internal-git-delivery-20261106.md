# F09 内部 Git 交付候选

用户选定 Git 渠道，超过50 MB的制品走 Nexus。本地完整 Apple 交付 ZIP 为
**25,288,674 B（约25.3 MB）**，因此按该规则准备内部 Git 制品／Release 交付；
Skiko `.4` 归档为108,785,484 B，继续准备 Nexus 交付。此轮未上传。

本机 Apple 包：
`artifacts/verification/f09-git-delivery-20261106/ODAIKitAppleMLXVLM-internal-notice-candidate-20261106.zip`

SHA256：`639205a78911289a604bc542a850d6d1d4772dc6a8b11e4d1b98635d41a152ed`。
ZIP包含完整20个XCFramework、4个C模块、18份资源、24份notice及原编译来源记录。
原20个单框架ZIP合计27,029,834 B，最大MLX.zip为10,723,491 B；它们保持原样。

## 项目许可与编译来源

旧完整二进制包仍声明项目许可未定。本轮在全新副本中接入用户已选定的内部许可，
四个SDK模块的license引用指向随包的`notices/ondevice-ai-kit/LICENSE`。
许可证取自精确提交`3b5150dc5c45d47e0a4ccf7cd23ce2ccacded449`。

实际编译提交仍为`f3899bbf282a32b05a3f3aafcb54c3d0dbcf5f9c`，所有原build receipt、
source inventory、toolchain、二进制/C/资源字段原样保留。150文件许可快照只比原149文件
SDK快照增加`packages/LICENSE`；149份原输入逐字节相同。没有原生重编译。
新增`LICENSE-PROVENANCE.json`分别绑定编译提交和许可提交，包含两份inventory和许可摘要；
写入前校验结构/值/149文件等价性，作为第24份notice纳入manifest摘要。

`prepare.py`及`preparation.json`保留在同一证据目录：319份非notice文件不变，
原完整分发树和20份ZIP未改。新manifest及static gate通过；完整ZIP解压后文件集合、
摘要和manifest再验通过。使用解压产物生成新的独立consumer，24份notice逐字节保留。
这次未重新编译consumer、运行设备或验证远端下载。

本机Ollama在14.74秒返回方案建议；主代理采纳来源JSON的结构和值检查，
保留已正确标注的原编译提交和有效旧build receipt。原文及逐条处置：
`.omx/artifacts/ollama-f09-notice-refresh-review-20261106/`。

## 当前审查与剩余条件

已完成的限定源码审查现已与当前源码核对：agy7文件、cbc4文件均无摘要漂移；
Copilot最终consumer生成器全文与当前源码一致。agy报告0条新finding；cbc的3条意见
经原契约/实际归档核实均未确认新阻断；Copilot报告0条finding。
它们不合并为完整分支、法律或生产发布批准，原缺失上下文继续保留。

实际接收组织、维护团队、内部Git上传端点与远端消费者仍需落定；最终复审、
P4/P11质量/资源和已延期最低系统门槛仍开放。九份保护输入和Apple锁文件不变。
基础仍10/12、P4/P11 open、release not-ready，无新增benchmark样本。

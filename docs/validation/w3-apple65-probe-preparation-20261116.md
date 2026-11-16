# W3 Apple 固定65 FILE 探针准备 — 2026-11-16

状态：本地隔离探针准备已接受；新的真机65文件批次未运行。R4/R5、P1–P4、W3仍为`open`，goal保持active；最终生产候选仍为null。

## 已交付

新目录`probes/ios-apple65-file/`包含Core校验、host检查、SwiftUI App、XcodeGen配置、README和源码身份清单。使用已有生产AppleSpeech SDK，普通App、旧三片/40请求探针和模型均未修改。Core及测试提交`8d12062`；App基于`8537933`加单独记录的精确source overlay，不把base commit称最终App身份。

固定runId为`w3-apple65-file-20261116-01`，bundle为`io.ondeviceai.probe.w3apple65file20261116`。65份WAV仅为既有PCM加44字节header，441.102s/63个唯一PCM摘要；顺序、reference、locale未变。9个annotation-negative未经听审，已暴露Qwen结果，不称新盲测。音频保留在本地任务artifact，不加入Git。

## 验证与边界

- Core先编译未实现stub并实见70项有效输入断言失败；正式Core通过506项检查、零失败。父端另对65份实际文件核整WAV及PCM摘要。stub的435项其它断言不能单独证明负例拒绝。测试候选原abc标准SHA字面量抄错，父端按独立hashlib值机械修正，raw原件保留。
- 初版iOS编译因`cancelledBefore`未定义exit65；6个helper精确替换修正变量、1基索引、输入错误分类、RSS结束时间及资源配置。原稿、日志与错误保留，没有主代理替写实现。
- 修正版generic iOS Debug/arm64/minOS15.0无签名构建exit0。实际App72个文件均留摘要，65份WAV/manifest/source identity匹配；14项selected inputs含Package.swift和配置，实际编译输入以保留的4份SwiftFileList为准，host检查入口未编入App。
- Debug主入口SHA`3c5b186ce3980b3bde1987948223ba6615bbb85504045e255634c2ceddaca20e`不是整个App代码身份；实际代码另在`W3Apple65File.debug.dylib`（SHA`a0de212d4dd7aa88e9a3762653310e2109f95dc7f343f1f2a46b18d979612156`）。72文件清单SHA`35000f9f5d3a24fa2bb67a53c57aa62388f3dbc2a8849bf11bb87b8648fbadb0`，source identity SHA`fe1c00efb4cb5d0157ec2e690c515b6dee184ed867cfca16cbe60296e306c0e6`。
- 免费hy3提供测试、Core、App及6项修正；各请求完整终态，零观察工具调用。一次独立供应上下文复核返回`accept-preparation`、无finding；它没有执行工具、重算摘要或接受生产发布/P4。
- Xcode仍有orientation/launch配置警告及无AppIntents依赖的metadata skip；仅对无签名准备作非阻塞处置，不证明设备UI或生命周期。原176个dirty/untracked内容保持，新增状态前缀独立保存。

App只允许手动触发；Speech授权设置独立，授权完成不自动识别；固定run报告存在即拒绝重放。全65预检早于backend/client创建；每段按自身locale新建file-only backend/client。typed1110保留domain/code和error状态，不造empty hypothesis。普通识别错误继续收集；输入/report I/O/取消停止，余项保留not-run。所有创建后的drain/close均await；RSS sampler cancel并join，monotonic请求时长、识别时长及cleanup分记。以上运行层目前仅源码审阅与编译，未新做真机生命周期验证。

## 后续执行交接

准备的run未消费。新的65文件运行属于后续独立执行步骤，本轮未安装、签名、识别、请求授权或操作设备；不重跑旧Apple40请求/麦克风/已验生命周期。运行前需用此具体候选及输入身份确定执行边界，产出每段终态和资源记录后再评分；产品质量与Apple专属预算仍未批准，不能把完整或部分执行终态直接写成通过。

Android仍为Qwen24/65，第25片触及1.5GiB实验守卫；SDK/Demo打包Whisper，Qwen仍独立实验。既有未消费Android单片诊断合同与fresh guards保持，不增加产品预算或Apple步骤作为其前置。旧quota/源站429及GL Wiki EOF路线不自动重试。

证据：[机器收据](w3-apple65-probe-preparation-20261116.json)、[Core收据](w3-apple65-core-preparation-20261116.json)、[运行合同草案](w3-ios-closure-contracts-20261116.md)。本地完整原始材料：`.omx/artifacts/w3-apple65-probe-preparation-20261116/`，含接受App、编译FileLists、全部候选/错误和限定复核。

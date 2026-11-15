# P3 通知交付集成任务

这是主代理准备的下一项生产实现范围与验收条件，尚未派发或交付候选。现有 native 与 CBC hy4free 的 429 停线继续有效；此前的 hy4free 选择已消费，不能再次用于调用。等待已提出的新免费线路选择期间，可继续本地核验，不能自动换模型或由主代理替代生产实现。

## 目标及所有权

实现候选只负责 `android/backend-sherpa-asr` 的通知资源和相应说明，必要时交付独立的最终 APK 打包验收夹具候选。主代理负责审阅、按候选字节集成、构建及产物验收；不调用设备、不改普通 App 或模型。不得递归委派。

优先利用模块默认资源入口，将运行时通知放在唯一命名的模块 assets 路径，例如 `ondeviceai/sherpa-asr/notices/`；实际是否需要 Gradle 配置由候选说明并由构建证明。允许文件限于本模块新增通知资源、README，以及确有必要的本模块最小打包配置。不得修改坐标、版本、依赖、锁文件、识别逻辑或生产模型，不允许修改上游 runtime AAR。最终 APK 夹具位于独立任务目录，不是用户普通 App。

## 输入及处理原则

固定 runtime 为 Sherpa 2.0.1，AAR SHA256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`，SDK 当前来源为 `a18292ac56e7c694fd2e7efb2550864a0b3ef21e`。输入沿用本目录检查收据、已核来源清单和 P1/P2 材料，不下载新模型或改变运行时。

候选必须提供通知选择表，逐项写明来源路径、原文字节摘要、组件/revision、采用理由、适用边界、交付路径。按已确认的实际构建版本选材；不能把历史 Eigen 3.4 候选、实际 Sherpa Eigen 5.0.1、嵌套 ORT 依赖混成一种版本。Darts 声明及已观察的上游 Android 补丁说明不得遗漏；原 ThreadPool 的实现已换为空 stub，不能声明其必然最终嵌入。对组成尚未确认的材料显式保留未决或补充说明，不能将全部 58 份参照文本自动当必要通知。

原许可、声明、归属逐字节保留，单独记录修改说明，不改写上游条款。此任务不包含 Qwen/Whisper 模型权重交付，模型材料另按其固定来源处理。Kotlin、coroutines、annotations 等已解析依赖也在生产消费链的适用清单核对范围内；本轮未补取它们的声明，候选不得凭名称猜测条款。

## 必须由父端执行的验收

1. 审阅真实候选与选择表，先核原文本的来源、摘要、交付路径及未决项；未决项不允许静默消失。
2. 本地构建/发布新的 backend AAR，提取通知/index/修改说明，与选择表逐项比对。原 SDK 逻辑、依赖和 runtime 摘要保持；记录新的 AAR 身份。
3. 独立消费者通过本地发布坐标解析新 AAR，核实际选中的摘要，无 project-dependency 替代；保留原 Debug/Release library 编译作为独立工程检查。
4. 用普通 Android App 打包边界的独立最小 APK 夹具，核最终 APK 中每份通知的路径和字节、传递 runtime 的成员。仅 library AAR 不能满足这一项；不安装、不访问麦克风、不请求 AiAi、不下载资源。
5. 在隔离复制件删除通知或同长改单字节，最终产物验收必须拒收。不要修改来源材料或通过包来制造负例。
6. 更新候选来源、覆盖和剩余缺口，保留 P2 未决项及 P4 独立发布复审；局部通知保留通过不能自动关闭完整 P3 或 W3。

## 委派边界

下一条 helper prompt 必须给精确文件所有权、允许动作、验收条件，明确：You are not alone in the codebase. Preserve other edits. Do not recursively delegate. Report blockers and scope changes.

Before work and on errors, consult docs/agent-pitfalls.md. For a no-tools task, supply applicable excerpts: W3 复审包仅证明文本保留，不代表最终组成/生产通知/发布接受；源码依赖声明不等于静态嵌入；外层 CLI exit0 不等于候选交付成功；新 quota/auth/重复供应商失败按停线规则处理。不得因本任务扩大工具权限、使用付费回退、触碰签名或改变全局配置。

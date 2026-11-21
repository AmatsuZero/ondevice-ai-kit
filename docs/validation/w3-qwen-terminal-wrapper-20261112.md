# W3 独立终态 wrapper 与实包验证（2026-11-12）

新 `QwenTerminalInstrumentation` 已按免费外部候选原字节集成：真实 Kotlin 编译/运行 **58 组、783 断言通过**，构建工具 **10/10**，缓存离线 Android **75 任务 build/lint 通过**，实际 APK 校验 **19 类、65 份资源、4 个 runtime 库逐字节一致**。独立 diff 范围 wrapper 及完整构建工具源码复核 **accept，无发现**。W3/R4/R5 仍 open，源码分支未 push。

别名 `io.ondeviceai.acceptance.qwenterminal`、目录 `files/w3-qwen-terminal` 和 variant `cpu-arena-off-heap-terminal-ack-v1` 均独立于旧 heap。它仅为新的终态协议故意重复公开 index25/1776，不追改 consumed run 或扩大质量结论。默认构造器保留真实 JVM noargs；首次 report/native 前验证 own `/proc/PID/stat` 的有界 ASCII、精确 PID 与 canonical positive Long startTicks，并拒绝现有 final/stage ACK 文件、目录或链接。

原 core/Activity/model/65 输入/allocator/heap/清理保护保持。真实 release/teardown 后才持久化终态，从成功持久化的同一 serialized bytes 计算 SHA 并进入 30 秒 ACK barrier。失败终态也可被观察；ACK 失败只改变 Bundle 与 instrumentation result，不重写报告、不覆盖原识别/清理错误。父检查覆盖以上顺序、精确 hash、坏 stat、陈旧 ACK、ACK timeout/invalid、持久化失败跳过等待和核心错误保留。

首整份 hy4free 请求自然终态 CLI0/raw0、stderr `Empty stream`，没有源码交付，也不是主代理超时或已观察到配额错误；原件保留且未重发。独立 IO、预检和终态片段交付后，主代理给出的 ACK API/nullability 上下文有误，真实编译 6 errors；外部修正又引入多余 File(File) 构造，真实 1 error；再次外部单行修正后 58 组全 GREEN。预检片段把 serialization 移出错误边界的问题也在终态片段中恢复，未替换旧错误语义。主机编译有两条继承 double 参数名警告，不宣称该编译为 `-Werror`。

构建工具首稿错误要求 adapter receipt 使用绝对 key，真实有效收据失败；实际 map 是 root-relative。窄修后 8 GREEN；新增 canonical alias/dangling entry 检查再真实 4 errors，外部精确修正后 10 GREEN。所有失败/补丁保留，未改真实收据或放宽检查。准备源 fingerprint `ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5`；main/test APK 分别 SHA `88757e37ac741a6bf7b3256f1d7378852b0e6c9bd9464762dd6155be81c7dd13`、`6104d8b51051ac834c2e8bc2cadd42f24673bc24d012fb766124b77a07a08815`，均无权限声明。runtime 与缓存 AAR 一致不证明最终递归组成/source 对应或生产通知完成。

复核只读供给 diff、实际 ACK API/state 与完整构建工具，未执行命令；不独立覆盖未改的 teardown/native/core。父端实际执行这些清理路径，但 host doubles 仍不代表 Android/OEM。机器收据见[同名 JSON](w3-qwen-terminal-wrapper-20261112.json)；原件位于 `.omx/artifacts/w3-terminal-observation-20261112/wrapper-*`、`build-tools-*`，构建/实包在 `artifacts/verification/w3-qwen-terminal-20261112/`。

完整启动/轮询/恢复驱动、新冻结合同、同 run 真机末端证据和完整质量/资源，以及生产与发布余门继续。当前零设备安装、推理、麦克风、AiAi 或模型/runtime 下载。

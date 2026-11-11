# W3 arena 单变量文件诊断已准备，等待设备解锁

新的 arena-off 变体通过外壳/实包及独立复核，父工具发现已修正并通过 22 项检查、原父端 8 项及 metrics 31 项。新 run `w3-qwen-arena-20261111-01` 已按[独立协议](w3-qwen-arena-diagnostic-protocol-20261111.md)冻结 108 项实际文件摘要；freeze SHA `a78b3661558223eeb083c56935ef89e8a88040755b1327ae7567ec0f09cc736d`。

本次只读 preflight 通过 TECNO KM9/API35/arm64/currentUser0 身份、源码/包/保护清单检查，但 Awake=false、unlocked=false，可用内存 2955546624B，新 alias 不存在。**零新安装、构造、stream 或 decode**。已请求用户为该单次文件诊断解锁；不绕过安全锁，不把此前解锁当当前门已通过。

后续在 fresh 门通过后依次 prepare→再次 gate→唯一 run→同 run 双读/政策收取→固定全量评分→恢复。保持 1.5GiB/512MiB/120s 门、原 65 输入和质量阈值。任何停止只收同 run，不重放已消耗的旧 FILE/load-only，也不自动重试新 run。

外部实现与两个 APK 原字节保持，实际 provider/配置精确内容可被核验，但 native 内部效果仍明确为 false/unverified。无麦克风、AiAi、新模型下载、生产或默认服务改动。Android 完整质量/成本及生产通知、运行时许可和发布复审未通过，W3 保持 open。

[外壳与包](w3-qwen-arena-wrapper-20261111.md)／[源码复核](w3-qwen-arena-wrapper-review-20261111.md)／[父工具复核及修正](w3-qwen-arena-parent-review-20261111.md)／[机器收据](w3-qwen-arena-ready-20261111.json)。本机冻结合同/包位于 `artifacts/verification/w3-qwen-arena-wrapper-20261111/`，完整失败与 RED/GREEN 证据位于 `.omx/artifacts/w3-qwen-arena-wrapper-20261111/`；这些目录不保证随 Git 分发。

准备结果已同步 GitHub #15 comment6784999365、GitLab #15 note42672 及双端专题 Wiki；GH Wiki commit `686434d4f1fe24e20a861251a12a507327a93281`，两页内容 SHA `d6af3c22821d1ab0f9ed6dd73a264397ea72d72ffe27386c069ef5b4a2d380c0` 一致。逐字回读[收据](w3-qwen-arena-ready-sync-20261111.json)已保存；这次只发布准备与边界，没有推源码或上传模型/本机包。

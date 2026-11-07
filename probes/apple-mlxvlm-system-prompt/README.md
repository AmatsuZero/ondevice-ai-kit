# apple-mlxvlm-system-prompt 探针

本 `probes/apple-mlxvlm-system-prompt/` 目录同时存放后续构建用的 XcodeGen 配置 `project.yml` 与逐字提交的探针源码 `SystemPromptProbe.swift`（SHA-256 `0d5a40893d6883ba08efadb49d7ed11894229dffe5a823d6c3267142c7a02e71`）。配置目标为 `type: tool`、macOS 15、Swift 6。

## 组装隔离 consumer（父代理负责）

- 新建**全新隔离** consumer；探针旁并无已随附的锁副本，须按下列步骤从生产源复制生成。
- COPY 生产 `apple-mlx/Package.resolved` 作为 READ-ONLY 源，写入生成的 consumer `.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`；绝不覆盖生产源。
- 依赖解析用固定离线 SwiftPM 缓存，构建走普通获批 host 的 pinned 本地缓存 + offline 标志。
- 模型目录和既有报告不改动、不覆盖；模型与夹具经绝对只读路径引用，不复制进 consumer。输出根目录经绝对路径引用，必须可写，且本次 runId 子目录必须尚不存在。
- helper 路径 `apple-mlx/Sources/ODAIKitAppleMLXVLM/{MLXVLMVideoInputAdapter.swift,MLXVLMVideoSnapshot.swift,LocalTokenizerLoader.swift}` 逐字复制到入口 `SystemPromptProbe.swift` 旁（不修改），另有 `project.yml`。
- 所有 shell 命令加 `rtk`；原始/二进制证据用 `rtk proxy`，不把过滤输出当原始字节算摘要。

## CLI 与输出

- 输入/输出均为绝对目录，经 `--model`、`--fixtures`、`--output-root` 传入；`--run-id` 为安全标识，`--sdk-commit` 为 40 位十六进制。
- 主输出目录（`--output-root/<run-id>`）以 `lstat` + 独占 `mkdir` 创建：路径已存在或为符号链接，均在写报告前拒绝，不覆盖、不改动既有字节。

## 已记录运行

本 README 不罗列完整构建步骤。本次已完成的 macOS 单系统消息诊断（共四次生成，运行 `system-message-birds-pair0-20261107-a1`）及证据，见 [f08-system-prompt-diagnostic-20261107.md](../../docs/validation/f08-system-prompt-diagnostic-20261107.md)。

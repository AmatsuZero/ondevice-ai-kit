# Apple 固定模型文件夹导入交接 — 2026-11-06

续接 [前一交付](HANDOFF-2026-11-05-local-model-startup.md)。本轮完成真实功能，
不重做旧固定样本、旧 F09 构建或失败 UI 自动化。继续 solo、本地提交和原授权边界。

## 已交付

代码提交 **df46772531c487910c68a28beb24c8eb84b72913**：增强 Apple Demo 新增
“导入图像理解模型文件夹”入口。系统 Files 单选文件夹，使用既有 canonical 14 文件
SmolVLM2 描述及 ModelStore 校验；安全作用域覆盖异步准备，并在所有退出路径释放。
缺失/错误模型准备失败后可显式重试；成功后只发布一次并停用导入，不替换活动模型。
取消/重复/晚到选择不重复准备。不会自动下载或开始推理，默认非 MLX 图仍隔离。

复用原 picker、preparation 和 host/VM，只增加固定文件夹路由与状态；未改共享 ABI、
模型身份、依赖、生产 lock、九个保护路径或 PAD/阈值策略。准备失败信息单列到模型设置，
不再借用全局错误消息。设计/计划在 `docs/superpowers/{specs,plans}/2026-11-05-apple-model-folder-import*`。

## 验证与签名包

- 重试测试旧行为只尝试 1 次，预期 2 次，真实 RED；picker 缺入口编译 RED。
- 最终完整 Swift bridge/host suite **82/82**，tools **304/304**，组合 App **28 输入 typecheck exit0**。
  5 份聚焦 Swift 文件 strict format 通过；既有 10 条 typecheck warnings 保留。
- 从新提交 **966 份 committed 文件**归档构建完整 iOS18/arm64 组合 App，exit0。
  实际符号包含 Identity/MLXVLM/KMP 及 `presentModelFolder` / `importMultimodalModels`。
- 既有 profile/设备/唯一匹配签名身份重验通过；签名副本的 3 份 Mach-O section map 不变。
  可安装 IPA 每个条目的解压内容与签名 App tree hash 一致。

回归证据：`artifacts/verification/apple-model-folder-import-20261105/`。
整包/签名证据：`artifacts/verification/apple-combined-folder-import-20261105/`；只读 `verify.py`。
IPA：该目录 `ODAICombined-model-import.ipa`，45,724,108 B，SHA-256
`f96915105a6f3c987496c691b1f56770e515f5678885d9856d25b152a7004d87`。
证据目录沿用开始执行日期，不因过午夜重命名。

## 装机待选择，保留现有会话

更新前读取设备发现旧 `io.ondeviceai.combined.demo` 又有进程 **PID2562**。
只能证明进程存在，前台/推理状态未知。没有终止它、更新 App 或切换前台，既有会话保留。
一条 `executable CONTAINS` 设备过滤因 URL 类型被工具拒绝；改读进程并在 host 按 App URL
匹配后确认 PID，没有把查询失败当 App 不在运行。

已通过异步问题询问：先保留当前会话/安装包，还是允许结束该会话更新。
写本交接时尚无答复；**不把超时或默认选项当作允许更新**。
无须重新构建或签名；若得到更新授权，使用新 `runtime-update/` 证据目录执行一次更新，
保留原模型容器与其他 42 个 App，避免改写此处冻结的 build-only summary。

新入口的真实系统 picker/安装/取消/重试验收尚未完成；callback 单测与类型检查不替代。
独立审查遵循既有 solo 约定保持 pending。P4/P11、质量/资源、校准与发布仍 open/not-ready。

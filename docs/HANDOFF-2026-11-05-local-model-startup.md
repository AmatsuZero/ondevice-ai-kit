# Demo 普通启动修复与进度说明 — 2026-11-05

本文件续接 [前一交接](HANDOFF-2026-11-05-next-session.md)。继续使用 foundation worktree、
solo、本地提交和原有授权边界；九个保护路径与生产 lock 保持不变。

## 为什么仍是 10/12

10/12 是基础阶段关闭数，不是功能交付比例。剩余 P4 是交互/运行时资源与分发收口，
P11 是统一验收；其内部子项完成不会直接增加阶段数。执行中对固定样本、精确候选和
报告传输的重复补证占用过多时间，削弱了功能交付节奏。后续以实际使用障碍和未实现功能
为优先，用户已后置的最低系统、长期质量/资源专项继续列为未验。

| 最近交付 | 已验证边界 | 仍缺 |
| --- | --- | --- |
| Android TTS 私有终态报告，b4db1fe | 精确候选真机正常播放、取消/join、重入、close、关闭拒绝 | 更广普通 UI/provider 与质量验收 |
| Apple 组合 Demo，2b0ddd2 | 同一 App 包含 Speech/Video/MLXVLM/OpenCV Identity，unsigned device build 成功 | 签名安装、普通 UI 验收 |
| 本轮本地模型启动修复 | 已安装模型可在删除导入目录后重新打开；普通增强版启动不再依赖隐藏参数 | 修复后的整 App 构建、安装与普通 UI 验收 |

## 本轮修复

- `DemoController.swift`：在编译启用 MLX 的增强版中，普通启动即执行既有 preparation；
  不再要求 `--multimodal-mlx-local-model`。缺失/无效模型保留 unavailable，并提示检查本地模型。
- `MultimodalModelHostFactory.swift`：由真实 ModelStore 状态判断是否需要安装；已安装模型
  直接 acquire，再比较完整 canonical manifest。文件损坏或 manifest 不匹配仍拒绝，
  不匹配时释放已取得的 lease。原取消/关闭/失败清理继续生效。
- 不自动下载、不自动推理；默认未编译 MLX 的配置不启用本后端。未更改模型、签名资产或设备。

## 新鲜验证

证据：本地 ignored `artifacts/verification/apple-local-model-startup-20261105/`。

- RED：新 reopening 测试在旧工厂抛出 `invalidSource`，14 项中 1 失败，exit65。
- GREEN：完整现有多模态 bridge/host/preparation suite **79 tests，0 failures**，exit0。
  新增 4 项覆盖安装后删除导入目录、缺失模型、损坏安装、同 ref 的 manifest 不匹配及 lease 释放。
- 完整 tools：**304 tests，OK**，exit0，45.957 秒。
- 组合 App **28 份当前 Swift 输入**在 arm64/iOS18 及全部组合编译 flag 下 typecheck exit0；
  复用已编译依赖，只写新 cache/log，没有重建包或改动冻结组合产物。
  既有 actor-isolation/weak-capture warnings 保留；不是新整 App build 或设备运行证据。
- 九个保护路径及生产 lock 摘要复核不变。旧 readiness 和旧冻结证据保留原样，
  不把它们冒充本轮新源码的验收。

## 下一停止点

优先交付可普通启动的组合 App，在新的隔离构建区构建修复后的版本；若沿用既有签名资产，
先复核有效期/设备/证书匹配，不创建签名资产，不替换普通旧 App。无变化的失败 UI 工具不重试。
外部缺口单列：PAD 获批数据与校准、独立审查、分发策略；不得靠增加固定样本运行关闭它们。
P4/P11 和发布仍 open/not-ready，整个 roadmap 未完成。

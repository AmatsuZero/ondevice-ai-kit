# Demo 普通启动修复与进度说明 — 2026-11-05

本文件续接 [前一交接](HANDOFF-2026-11-05-next-session.md)。继续使用 foundation worktree、
solo、本地提交和原有授权边界；九个保护路径与生产 lock 保持不变。

## 最新交付：修复后的组合 App 已装机

已从 `bad63623769074ae8aa61661fac851327a918304` 的 964 份 committed 文件新建隔离源码和构建区，
完整 iOS18/arm64 unsigned build 通过；Identity、MLXVLM、KMP 与 MLX 资源均实际链接。
沿用未过期、包含授权设备的既有 wildcard profile 和唯一匹配签名身份；只签名产物副本，
显示名 **ODAI Combined**，bundle `io.ondeviceai.combined.demo`，3 份 Mach-O 的 section map 不变。

该 App 已安装到 `00008140-0008703436D3001C`。既有 14 份固定 SmolVLM2 文件重新核验 hash/size 后，
仅复制到新容器。**不传任何启动参数**启动，PID2560 存活，ModelStore 生成的 manifest 与固定描述
逐字段一致。首屏显示 Identity 设置区与五个共享入口，无模型准备失败提示；截图只证明渲染，
不代表点击、picker、生成或识别已验收。Identity 仍需要显式模型导入与同意，不自动启用注册。

42 个既有 App 的安装 URL 全部不变，唯一新增 App 为本组合版本。新 App 与模型保留，PID2560
已停止，普通 `io.ondeviceai.opencv.demo` 已恢复前台。未录音、拍照、写 Photos、下载或修改签名资产；
未重试失败的 UI 自动化。准备脚本一次 Path JSON 序列化失败已修正，从已验证步骤继续，未重做设备请求。

新鲜证据：`artifacts/verification/apple-combined-startup-20261105/`，只读复核：

```sh
rtk proxy python3 artifacts/verification/apple-combined-startup-20261105/verify.py
```

最新 readiness：`artifacts/verification/p11-readiness-20261105-combined-startup/summary.json`。
仅给 Apple 多模态/Identity 两行补充组合装机与普通启动证据；原质量/触摸/校准等 gap 不变，
没有新 benchmark 样本。P4/P11 和发布仍 open/not-ready。

## 为什么仍是 10/12

10/12 是基础阶段关闭数，不是功能交付比例。剩余 P4 是交互/运行时资源与分发收口，
P11 是统一验收；其内部子项完成不会直接增加阶段数。执行中对固定样本、精确候选和
报告传输的重复补证占用过多时间，削弱了功能交付节奏。后续以实际使用障碍和未实现功能
为优先，用户已后置的最低系统、长期质量/资源专项继续列为未验。

| 最近交付 | 已验证边界 | 仍缺 |
| --- | --- | --- |
| Android TTS 私有终态报告，b4db1fe | 精确候选真机正常播放、取消/join、重入、close、关闭拒绝 | 更广普通 UI/provider 与质量验收 |
| Apple 组合 Demo，2b0ddd2 | 同一 App 包含 Speech/Video/MLXVLM/OpenCV Identity；修复版 bad6362 整包构建、签名、装机及无参启动完成 | 点击/picker/生成的普通 UI 验收 |
| 本地模型启动修复，bad6362 | 已安装模型可在删除导入目录后重新打开；普通增强版启动不再依赖隐藏参数；新组合 App 无参启动已完成固定模型安装 | 新 App 实际触摸与推理验收 |

## 本轮修复

- `DemoController.swift`：在编译启用 MLX 的增强版中，普通启动即执行既有 preparation；
  不再要求 `--multimodal-mlx-local-model`。缺失/无效模型保留 unavailable，并提示检查本地模型。
- `MultimodalModelHostFactory.swift`：由真实 ModelStore 状态判断是否需要安装；已安装模型
  直接 acquire，再比较完整 canonical manifest。文件损坏或 manifest 不匹配仍拒绝，
  不匹配时释放已取得的 lease。原取消/关闭/失败清理继续生效。
- 不自动下载、不自动推理；默认未编译 MLX 的配置不启用本后端。未更改模型、签名资产或设备。

## 启动修复代码回归（前轮）

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

组合 App 已交付到设备，不再重复构建、装机、固定样本或首屏截图来增加计数。
普通触摸/picker/推理需要可用的 UI 工具或真实人工验收；无变化的失败 UI 工具不重试。
外部缺口单列：PAD 获批数据与校准、独立审查、分发策略；不得靠增加固定样本运行关闭它们。
P4/P11 和发布仍 open/not-ready，整个 roadmap 未完成。

# F08 Qwen3.5 vision-end token upstream patch (2026-11-15)

状态：**本地补丁已生成并验证；未修改项目依赖、产品模型或外部仓库。**

## 补丁范围

`Qwen35` 的两个 `Qwen3VLLanguage.getRopeIndex` 调用点显式传入 checkpoint 配置：

```swift
visionEndTokenId: config.visionEndTokenId,
```

补丁文件：[f08-qwen35-vision-end-token.patch](patches/f08-qwen35-vision-end-token.patch)。补丁只有两行新增，没有删除或其他重构。

## 验证

补丁已分别对以下源码执行 apply check 和实际临时应用：

- 项目固定 runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`；
- 检查时 upstream `main` commit `6352907543270ae9c61fcaf59cb3e07b0b989ea1`。

两个未修源码的参数出现次数均为 `0`，临时应用后均为 `2`。固定 revision 应用后的文件与完成 valid36 的 `Qwen35Fixed.swift` 逐字节一致。

行为证据复用既有红绿链：

- 静态回归在原源码上 exit `1`，确认两个调用点缺参；
- 同一第一题由原 runtime exit `-5` 变为修复后的 exit `0`、`completed`；
- 完整 Mac Metal valid36 随后 exit `0`、完成 `36/36`。

机器可读收据：`artifacts/verification/f08-qwen35-upstream-patch-20261115/verification.json`。

## 集成边界

`apple-mlx` 当前公开产品路径仍固定 SmolVLM2，Qwen3.5 没有进入模型清单，且其 valid36 只有 `17/36`，未达到手机晋级门。因此本轮保留独立上游补丁，不改变 `apple-mlx/Package.swift`、`Package.resolved`、SDK 默认模型或 App。

该补丁解决 runtime trap，不改变 Qwen3.5 的质量处置；W6 仍为 `open/not-ready`。

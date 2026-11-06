# AtlasTextOp TLS 缓存原生回归

固定源码：JetBrains Skia `22f58c9fd43d55bde818821c04b48fda5d7ec939`，对应当前 Skiko `0.144.6`。本目录的补丁用于独立源码复审，不改变本项目的 Compose/Skiko 生产依赖。

| 文件 | 用途 |
| --- | --- |
| `AtlasTextOp-tls-owner.patch` | 不含测试 hook 的候选修复：TLS 单槽改由 `CacheOwner` 在线程退出时释放 |
| `native-regression-baseline.patch` | 在原始 TLS 指针源码上接入真实 `AtlasTextOp` 测试和测试事件，预期 worker 退出用例失败 |
| `native-regression-candidate.patch` | 在相同固定源码上接入候选修复、相同测试和测试事件，预期通过 |
| `Skiko-ios-only.patch` / [说明](Skiko-ios-only.md) | 固定Skiko的iOS-only源码发布补丁，修复共享Native metadata注解失败并关闭未启用AWT的独立发布；不修改生产依赖 |
| [Skiko-ios-source-notices.patch](Skiko-ios-source-notices.patch) | Supplement for the next immutable iOS candidate: dated modification notices in all seven changed source files; not applied to frozen .2 |
| [Skiko-ios-notice-publication.patch](Skiko-ios-notice-publication.patch) / [说明](Skiko-ios-notices.md) | 正常Maven notices classifier及source JAR许可材料，顺序应用在前两份Skiko补丁后 |
| [collect-skiko-notices.gradle.kts](collect-skiko-notices.gradle.kts) | 消费者显式解析并保留notices原文；不自动嵌入最终App |

两份回归补丁**各自独立应用到干净的同一 revision**，不要顺序叠加。测试事件仅在回归补丁中；它在实际 `::operator delete` 返回后记一次释放。生产候选只有 `AtlasTextOp-tls-owner.patch`。测试通过 `AtlasTextOpTools::CreateOp` 构造真实对象：同线程验证单槽复用和 `GrRecordingContext` 析构清理；跨线程让主线程销毁 context、worker 线程销毁 op 并退出，验证 worker 的 TLS 缓存最终释放。

在两个分别准备并同步依赖的 Skia checkout 中运行：

```sh
git apply --check /path/to/native-regression-baseline.patch
git apply /path/to/native-regression-baseline.patch
bin/gn gen out/Debug
third_party/ninja/ninja -C out/Debug -j8 dm
out/Debug/dm --src tests --match '^Skiko_AtlasTextOp' --threads 1
```

另一干净 checkout 将上述补丁路径换成 `native-regression-candidate.patch`，使用相同构建和测试命令。基线预期 2 项中 context 用例通过、worker 释放断言失败；候选预期 2/2 通过。候选另运行 `out/Debug/dm --src tests --threads 8`，本机记录为 2809/2809 exit 0。宿主日志中的 `CGLChoosePixelFormat failed` 不代表 GPU 路径通过。三份补丁已在第三个干净 checkout 各自通过 `git apply --check`；实际 RED/GREEN、完整测试日志和 SHA-256 见 ignored 本地 `artifacts/verification/f02-remediation-20261101/native-regression-summary.json`。

这组测试证明固定源码的原生分配/释放路径，不代替独立代码复审、可分发 Skiko artifact、正式无 recorder iOS 真机资源验收或 P4/P11 发布结论。

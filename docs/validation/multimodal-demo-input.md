# 单图 Demo opaque input/lease 验证

更新：2026-10-22。前置 shared UI 包 `55a425b`；设计/计划 `0b0f851`，Apple Task1 `caab202`，Android Task2 与本检查点同次提交，最终 HEAD 以 git 为准。

## 已实现

双端 `MultimodalInputStore` 实现既有平台 input provider。导入本地 regular nonsymlink 文件时创建私有副本并返回 opaque UUID；acquire 只查 registry，不把 reference 当路径。remove 退役选择，close 封闭新导入/acquire；已有独立 lease 直到最后 release 才能释放副本，每个 release 幂等。只拥有新建目录和自身副本，关闭不递归删除外来内容。删除失败保留 registry entry，后续 close/remove 可重试。

Apple 薄适配复用 SpeechInputStore，Android 使用同步锁、Java NIO `NOFOLLOW_LINKS` 与 `CREATE_NEW`。源文件不删除、不改写；不做图片解码或格式/模型支持证明，bounded PNG/JPEG admission 仍属于 backend。基础 iOS15/macOS12/API26 及独立 MLX 增强边界不变；无新依赖。

本包只有输入所有权基础，尚未接 picker、host、页面输入回调、ModelStore 或真实模型。默认 unavailable 不变，不能称 host/picker 已完成。

## 实际验证

日志保留在 `.superpowers/sdd/2026-10-22-multimodal-demo-input/`，生成工程保留在 `artifacts/verification/multimodal-input-*-20261022/`。

| 门禁 | 新执行结果 |
| --- | --- |
| Apple RED→GREEN | generator 实际生成 source 集合先 RED，更新后3/3；XCTest 缺 store symbols RED；首实现13/14暴露真实外来文件误删；修复后14/14，格式化后再14/14，`task-1-apple-final.log` |
| Apple ABI 回归 | Binding10/10，Demo14/14（既有10 + 新输入4）；新生成 Speech bridge/host/session39/39、Video bridge/host9/9。`task-1-binding-regression.log`、`task-1-{speech,video}-host-regression.log`。较早旧工程20/6不替代完整host套件 |
| Swift 格式 | strict lint exit0；不代表最低系统设备执行 |
| Android RED→GREEN | 四项真实临时文件测试先因缺 store symbol 编译失败；实现后新4/4、全 Demo14/14，0失败/跳过，`task-2-android-{red,green,final}.log`；最终消除测试 nullable receiver warning 后单测再次执行 |
| Android 编译/lint | APK成功；lint0error、9条既有warning。首次539tasks（17执行），最终539tasks（7执行、532 up-to-date），不把缓存任务称新执行 |
| 工具 | 完整101/101，`task-2-tools.log`，已有 Ruby3.4、全程离线依赖 |

四个新文件测试覆盖：源修改后的副本隔离、opaque reference、多 lease 与 remove/close、重复 release/close、closed admission、unknown/path-like reference、symlink/directory/missing source 拒绝、fresh directory 及 foreign sentinel 保留。仅合成字节，不证明自然图片质量或真实 picker 往返。

复跑 Android/工具：

```sh
rtk proxy python3 scripts/gradle-local.py :demo:androidApp:testDebugUnitTest :demo:androidApp:assembleDebug :demo:androidApp:lintDebug --offline --console=plain
rtk proxy env RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby python3 -m unittest discover -s tools/tests -v
```

## 作者审查与保留缺口

每 Task 范围作者自审、最终单独作者审查完成；用户要求当前会话单人执行，独立 review pending，无独立 APPROVE。唯一必要范围修正：新测试复现旧 SpeechInputStore 空 registry 时递归删除外来文件，改成原子非递归 `Darwin.rmdir`。误判的代价是暂存目录留存/清理失败，不接受外来文件丢失；新输入与既有 Speech/Video host 回归均通过。

下一独立包：双端单图 host/picker 和页面/后台/close 生命周期接线，消费本 store 与既有 VM/bridge。Apple 固定 ModelStore/MLXVLM 可选连接及 KMP→真实 SDK 设备闭环继续另包；Android 没有生产后端时明确 unavailable。

仍未验：iOS实际页面交互、picker真实选中往返、host/lease集成、KMP真实模型成功/首update后取消/drain/重入/后台不自动重启/close释放与关闭后拒绝、最低系统、自然质量、Metal/GPU/energy、Android生产推理与独立审查。旧 Video UI 初始化超时、P6实录、Android ASR MISSING_ASSETS 等缺口保留；本包不提升 roadmap 的5/12阶段计数，P9 active。

没有 push/merge/发布、依赖/模型下载、录音、私人媒体读取或签名资产变更。两保护文件状态不变且不暂存：pbxproj SHA256 `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`；face-identity.md SHA256 `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。`.superpowers/`、artifacts 和全部日志保留。

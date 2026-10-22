# Apple MLXVLM Backend 验证

更新时间：2026-10-22（Asia/Shanghai）

## 交付边界

新独立 Swift package `apple-mlx` 提供 `ODAIKitAppleMLXVLM`。根 `apple/Package.swift` 没有 MLX 依赖，既有 iOS15/macOS12 产品保持不变；增强包的真实推理仅支持 arm64 iOS18+ 物理设备，macOS/simulator 明确 unavailable。

固定 model `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`、revision `fa57db46815177fbdfd65cc85a2b3416a8332268`、MLX runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`、Tokenizers revision `b38443e44d93eca770f2eb68e2a4d0fa100f9aa2`、mlx-swift0.32.3；完整图见 `apple-mlx/Package.resolved`。bundled JSON 只有14文件的元数据，无权重。

调用方在自己的独占 ModelStore root 显式安装已有本地资产，acquire lease 后传入 `MLXVLMBackend(lease:)`。成功初始化后由 backend 拥有 lease，调用方不得提前 close 或修改资产；backend/client close 会释放。manifest 必须与 canonical 元数据一致，身份字符串本身不能代替哈希验证。availability 不加载模型、不安装、不读取图片；构造时 ModelStore acquire 的文件验证与 availability 分开。

生成遵守 request 的 maxOutputTokens（本后端1…512）、prompt UTF-8≤8192B；输入为单帧 PNG/JPEG，编码≤20MiB、每边≤4096px、总像素≤16,777,216。以 O_NOFOLLOW 打开 regular file，读取有界 Data snapshot，先校验元数据再解码；后续源文件变化不影响本次输入。orientation1…8传递给CoreImage。每次请求新载入模型与KV状态，无跨轮聊天历史。

后端显式持有、取消并等待 upstream generateTask 的 Task；AsyncStream 结束不作为 producer join。未把 ChatSession.clear 当取消屏障。使用固定 Tokenizers 公开接口的本地 typed adapter，未修改 compiler-macro trust 设置。模型对象释放不等于 Metal 全局 allocator 缓存归零。

## RED→GREEN 与本地证据

ledger/log：`.superpowers/sdd/2026-10-22-apple-mlxvlm-backend/`。

- Task1 `440af94`：缺少package/backend的RED；GREEN4/4。canonical manifest、真实小型 ModelStore lease、防提前 remove、不加载模型的 availability、重复close、非canonical拒绝。
- Task2 `815435b`：真实RED断言复现取消后的晚到update（1而非0）、旧序号update被交付；修复后host11/11、0 failures。另覆盖budget/raw prompt、累计有序update、普通错误、close join/重入、PNG snapshot/symlink/非法图片/超尺寸拒绝。
- iOS18 device target与独立consumer编译链接成功。全部使用现有SourcePackages与 `-disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile`，没有下载/更新依赖。初次macro trust拒绝已通过移除macro target解决，没有关闭信任校验。保留上游Metal C++17-extension/AppIntents及样例orientation warnings。
- root `apple` fresh全量461/461、0 failures（日志中各runner12+74+14+13+313+9+5+21）；工具完整90/90。新consumer/基础依赖隔离回归2/2，生成器拒绝覆盖非空输出。
- 首次直接xcodegen外置输出缺少Swift source的失败保留；现用下方生成器同时复制样例源码、固定lock并固定本地package路径。

## 生成独立消费者

    rtk proxy python3 tools/generate_mlxvlm_consumer.py artifacts/MLXVLMConsumer-fresh

项目为输出目录的 `MLXVLMConsumer.xcodeproj`、scheme `MLXVLMConsumer`。生成步骤不解析/下载依赖；离线构建需提供已缓存并匹配Package.resolved的SourcePackages目录。普通启动不自动推理；只运行用户点击的固定fixture套件或显式参数 `--sdk-mlxvlm-fixture-suite`。准备的fixture/model放入该独立App的Documents，例子展示local ModelStore安装/acquire/client/close/remove，不提供自动下载或私人媒体选择。

## 真实 SDK 设备链路

iPhone16ProMax/iOS27.0.1，UDID `00008140-0008703436D3001C`；独立bundle `io.ondeviceai.mlxvlmconsumer`，沿用既有开发team签名，没有修改签名资产或默认Demo。固定输入 `artifacts/P9/fixture.png` SHA256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`；本机 `artifacts/P9/verified` 的14文件逐一复核bytes/SHA256。只复制公开模型与合成fixture到本App容器。

原始结果 `artifacts/verification/apple-mlxvlm-backend-20261022/device-result.json`，launch/install JSON与所有build/provision/copy logs保留在相邻目录/ledger。

| 设备断言 | 结果 |
| --- | --- |
| state与model/runtime identity | success、与固定值一致 |
| 首次生成 | 非空描述；firstTotalMs=3625.341709（含该轮模型加载） |
| 首个update后主动cancel | cancelledAfterUpdate=true |
| 同进程reentry | 再次成功返回非空描述 |
| active lease期间remove | leaseProtected=true，ModelStore.inUse |
| client.close后remove | leaseReleasedAfterClose=true |
| close后新请求 | closedRejectsRequest=true |
| 输入完整性 | 前后固定fixture hash一致 |

输出是合成红色方块图片的一句描述。该套件经过真实SDK/ModelStore/runtime，不复用旧probe的结果；不把它称为质量、最低系统或资源峰值验收。

## 保留门槛

独立reviewer仍pending，作者自审不能代替独立APPROVE。未完成：短视频、Android实际推理、KMP/Demo、自然图片质量基准、首token/多轮性能统计、Metal/GPU峰值、最低系统设备及增强包binary分发。根基础模块的十XCFramework无需重复打包，本包只验证新增source package消费。

没有push、发布、录音、私人媒体读取或新资源下载。两个保护文件hash未变且未暂存。整个P9/roadmap保持未完成。

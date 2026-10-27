# Apple MLXVLM Backend 验证

更新时间：2026-10-23（Asia/Shanghai）

最新独立分发包：可搬移 source consumer 已在固定缓存下离线编译；Release/library-evolution archive 在固定 `swift-collections` 的 `OrderedCollections.swiftinterface` 验证失败（exit65），增强 binary 分发仍未通过。见[分发验证](apple-mlxvlm-distribution.md)。下文历史 backend/benchmark 证据不替代该门槛。

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

## 有界固定单图 benchmark（2026-10-22）

设计/计划：`docs/superpowers/{specs,plans}/2026-10-22-apple-mlxvlm-fixed-image-benchmark*`。实现提交 `f9512a0f9fb0956a41e5e99eb4dd1e0a05011169`，前置 validator 为 `b97f0b3`。只新增 opt-in observer 与独立消费者测量；没有更改基础 SDK、模型、runtime 或输入。

### 固定条件和可复核原始证据

- iPhone16ProMax (`iPhone17,2`)，iOS27.0.1/build24A446；同一授权 UDID、bundle、team。签名 **Debug** build，不是 Release 性能结论。
- 模型/runtime/fixture hash 与上节相同；本地模型14文件重新逐一校验 bytes/SHA256，设备端通过真实 ModelStore install/acquire 验证，消费者再次核对固定图片 hash。prompt=`Describe this image in one brief sentence.`，maxOutputTokens=32，真实 runtime 使用 `temperature=0`，image preprocessing resize=512×512。其余 GenerateParameters 默认值由固定 runtime revision 限定。
- 新进程 PID20276，runId=`6A376940-9A43-4654-BCA2-33EDD3D2C453`；1 次不入统计的 warm-up＋5 次测量。每轮新建 backend/client、重新 load、无聊天历史；文件系统/Metal allocator/编译缓存没有清空，**不是冷启动**。
- `artifacts/verification/apple-mlxvlm-fixed-image-20261022/`：`device-result.json`、`summary.json`、`launch.json`、`install.json`、`devices.json`、`asset-verification.log`。构建/测试/RED日志在 `.superpowers/sdd/2026-10-22-apple-mlxvlm-fixed-image-benchmark/`。这些本地忽略目录保留，不含模型权重的 Git 提交。
- raw SHA256=`51215802ad610c5887c681443bd4379e59b4326734e639556591e8101a651407`；summary SHA256=`a520754c434f342630418f96816dd164fd65921bef6339c667e1996c09775666`；`apple-mlx/Package.resolved` SHA256=`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。

重新校验和汇总（不启动设备或访问模型网络）：

    rtk proxy python3 tools/mlxvlm_benchmark_report.py artifacts/verification/apple-mlxvlm-fixed-image-20261022/device-result.json --output artifacts/verification/apple-mlxvlm-fixed-image-20261022/summary-recheck.json

### 测量结果与边界

| 指标（ms） | p50 | p95 |
| --- | ---: | ---: |
| loadContainer 区间 | 311.094917 | 323.690167 |
| generate 调用前至首个交付 update | 763.803375 | 774.153375 |
| generate 调用前至成功返回 | 924.392542 | 934.323334 |

时钟为 `ContinuousClock`，nearest-rank，n=5（p95就是5次最大值），不是P11的5+30协议，也没有性能SLA通过结论。首update包含加载/预处理/调度，不冒称纯解码首token；总耗时不包含前置ModelStore install/acquire、request构造或返回后的close。load只覆盖loadContainer，不含前置图片校验/解码；MLX懒执行成本可能落入后续阶段。

进程RSS：baseline **2,087,469,056 B**，采样peak **2,437,054,464 B**，delta **349,585,408 B**。方法 `process-resident-mach-task-basic-info-10ms`：ModelStore安装完成后、warm-up前取baseline，独立Task请求每10ms采样，最后一轮结束后cancel/join并锁定快照；10ms是请求间隔而非硬实时保证，短峰可能漏采。baseline已经包含App及安装/校验的影响，delta不是模型独占内存。没有逐轮RSS分布；**没有RSS p50/p95**。该值不是Metal/GPU峰值、物理内存预算或设备全局资源，`metalGpuPeak`和`energy`均为 **unverified**。任何task_info失败都将整个RSS观察标为unverified，不能以0代填。

合成rubric `synthetic-red-square-v1` 为5/5（都含red和square），原始5次输出均为 ` A flag with a red square in the center.`。人工复核fixture只见近白背景与红方块，**“flag”是未由fixture证明的额外语义**；rubric没有检测该问题。因此仅记录最小概念命中，不声明完整合成描述准确，更不声明自然图片质量、OCR、计数、空间推理或幻觉率通过；`naturalImageQuality=unverified`。

新增验证：consumer/validator定向10/10、工具完整98/98（既有Ruby3.4 wrapper）、apple-mlx host12/12、固定缓存的签名iOS构建均通过。observer故障注入确实断言失败后恢复GREEN；RSS不可用与事件顺序使用真实Swift helper编译执行验证，不只检查源码字符串。基础Swift461/461为前包既有证据，本包未修改基础源码，未把旧计数称为新跑结果。

## 固定单图 1 warm-up + 30 samples（2026-10-25）

为补齐 P9/P11 规模化统计，本轮保留旧 `--sdk-mlxvlm-benchmark` 的 1 warm-up + 5 兼容行为，新增显式 `--sdk-mlxvlm-benchmark-30` 入口和 validator `--expected-samples 30`。从当前源码生成的独立 consumer 使用固定缓存、既有 Team `4X996UV8LC` 构建并安装到同一授权 iPhone；全程使用 `xcodebuild`/`xcrun devicectl`，无镜像、无坐标点击、无私人媒体。

- 原始设备报告：`artifacts/verification/apple-mlxvlm-benchmark-30-20261025/device-report.json`，SHA-256 `dd75f54cb84b821261e06639d032af2bba539fbdf1706023691479eb2e7c9f66`；汇总：同目录 `summary.json`，SHA-256 `3d970842aec57e0e79519f6588a49beb0b7ff3f9bdd776611aa3648d57fb7b83`。
- iPhone 16 Pro Max / iOS 27.0.1 (`iPhone17,2`)，`runId=D824ACA7-42E8-492D-B9A5-6FE3868551F5`，`sampleProfile=1-warmup-30-samples`，warm-up 成功，测量 30/30；模型/runtime/fixture identity 与固定 manifest 一致。
- nearest-rank：load p50/p95 `334.141875/372.181459 ms`；首 update `782.097334/819.266917 ms`；total `943.978709/981.462959 ms`。30 次输出均命中 synthetic rubric（30/30）。RSS baseline/peak/delta `34,127,872 / 1,186,676,736 / 1,152,548,864 B`，仅代表 `process-resident-mach-task-basic-info-10ms` 进程采样。

该报告只补齐固定合成图片的规模化延迟/RSS 统计；不代表自然图片质量、Metal GPU peak、energy、冷启动、跨设备预算或 P11 统一验收。`metalGpuPeak`、`energy`、`naturalImageQuality` 继续为 **unverified**。新增 validator/consumer 定向测试与构建通过，未提交或推送本包。

### Review 修复后的复核

独立 code review 的两个 MEDIUM 已修复：显式 30 样本现在必须携带匹配的 `sampleProfile`，benchmark 运行/报告写盘失败会显式反馈而不显示成功。当前源码重新构建并在同一物理设备重跑的证据为 `artifacts/verification/apple-mlxvlm-benchmark-30-20261025-fix2/`，raw SHA-256 `186a2077bf966dfb69cd89034d940c79a09b91a6d09d74b05172dc862c3c73d1`，summary SHA-256 `4cf8f2430d896addd2f2d821d815b795647924c83f09b9d825ed5987a6fa61d6`。validator/consumer 定向测试 **13/13**；架构 lane 因 429 未返回独立结论。

## 保留门槛

独立reviewer仍pending，作者自审不能代替独立APPROVE。未完成：短视频、Android实际推理、KMP/Demo、自然图片质量基准、纯首token/冷启动/P11规模性能统计、Metal/GPU峰值、最低系统设备及增强包binary分发。下一独立包选择单图KMP bridge（公共契约与平台adapter），Demo UI/短视频/Android新模型各自分包，不在本证据包扩张。根基础模块的十XCFramework无需重复打包，本包只验证新增source package消费。

没有push、发布、录音、私人媒体读取或新资源下载。两个保护文件hash未变且未暂存。整个P9/roadmap保持未完成。

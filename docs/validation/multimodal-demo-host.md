# 单图 Demo native host 生命周期验证

更新：2026-10-22。前置输入包2c2aaef，设计/计划cc99556，Android Task1 854d256，Apple Task2/检查点与本记录同次提交；最新HEAD以git为准。

## 交付

双端原生 MultimodalHost 组合既有 MultimodalViewModel、typed bridge 和 MultimodalInputStore；选择回调只接受调用者拥有的本地 URL/File，并在返回前复制。默认 UnavailableMultimodalBridge，不注册或伪造后端。host不重复生成协调器；adapter仍负责cancel/drain/lease释放。

每次选择带latest/once-only ticket，nil/重复/旧回调不替换输入；失败保留旧输入并给出typed INVALID_INPUT。成功替换或clear退役旧token，已有lease继续持有副本。clear/leave/default background/close使晚到选择失效。background取消VM，不重启；显式preservePicker=true只保留已有外部picker ticket，允许Activity恢复前收到本地文件回调，不启动推理。close封闭store新admission并只关闭一次VM/bridge；未drain lease继续存活，重复close可重试删除。

本包没有实际系统picker展示/URI读取、shared选择按钮、平台默认opt-in或session owner接线；这三项在下一包完成。Apple ModelStore/MLXVLM和真实KMP设备闭环随后另包。设计/计划见 `docs/superpowers/{specs,plans}/2026-10-22-multimodal-demo-host*`；ledger/final-review/日志保留于同名 `.superpowers/sdd/`。

## 新鲜验证

| 门禁 | 结果 |
| --- | --- |
| Android RED→GREEN | task-1-red.log缺host符号编译失败；实现后新host7/7、全Demo21/21（含input4/Speech5/Video3/Image2），0失败/跳过，task-1-green.log |
| Android APK/lint | 离线成功，0error/9既有warning；539tasks18执行521up-to-date，缓存任务不称新执行 |
| 生成器行为 | 实际生成Demo source集合缺2文件RED；增加Demo-only host/test sources后3/3，Binding sources/products不变；task-2-generator-{red,green}.log |
| Apple RED→GREEN | 生成新工程后缺host symbol RED；首实现漏写throws picker签名的编译失败保留task-2-apple-green.log；修正后新host7/7、完整Demo21/21，格式化后再次21/21，task-2-apple-{red,fixed,final}.log |
| Binding ABI | 新生成工程10/10，task-2-binding.log；不包含Demo host或新产品 |
| Swift格式/工具 | strict swift-format lint exit0；完整工具101/101（task-2-tools.log），已有Ruby3.4，无依赖下载 |

两平台7项host行为测试使用合成临时文件：选择显示名/替换/clear、多lease保留、nil/重复/旧ticket、输入与启动错误、clear/leave/background/close后晚到结果、preservePicker在foreground前回调、foreground不自动生成、受控drain时close一次与关闭后拒绝、真实默认unavailable。受控bridge仅在测试中，不证明真实模型或真实picker往返。

生成工程 `artifacts/verification/multimodal-host-{demo,binding}-20261022/` 保留。XCTest17和Demo ICU18.5的既有链接版本警告保留；iOS15 target现代simulator通过不等于iOS15设备通过。旧Kotlin/Native入口/VM/bridge源码未改，无新导出标签。

## 审查与后续

范围作者自审与最终单独作者审查完成，未发现本包剩余Critical/Important或新deferred Minor；按用户要求不派子代理，独立review仍pending，无APPROVE/发布结论。

Ruling：将host/picker继续拆为native lifecycle owner先行，实际system picker/shared actions/platform opt-in随后，代价是多一个小集成包；避免把文件/ticket测试当真实picker验收。下一包消费本host，保留默认unavailable和旧Face/Speech/Video行为。再分别接Apple既有固定模型和KMP真实设备生命周期。

未验：系统picker选中/取消往返、实际平台session接线和iOS UI、KMP→MLXVLM设备成功/首update后取消/drain/重入/后台不自动重启/close与lease释放、自然质量、Metal/GPU/energy、Android生产后端、最低系统、独立审查。旧Video自动化初始化超时、P6实录、Android ASR MISSING_ASSETS及其他门槛保留。roadmap仍5/12完成，7阶段未收口，P9 active。

全程仅本地提交；无push/merge/发布/模型或依赖下载/录音/私人媒体/签名变更。保护pbxproj仍tracked dirty、face-identity.md仍untracked，hash分别b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5、6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822，从未修改/暂存。所有workspace/artifacts/日志保留。

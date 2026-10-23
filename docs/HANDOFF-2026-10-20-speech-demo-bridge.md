# Handoff — Speech Demo Bridge

更新时间：2026-10-23（Asia/Shanghai；共享App-local AX活动取消/离页/同进程重入通过；物理触摸/systempicker/OS后台/独立审查待补）

## P9 Shared active Multimodal AX（2026-10-23，最新检查点）

- 接续69fae57，design54b7951、helper/report2339c5d、suite2915725，本检查点同次本地文档提交。见[活动UI验证](validation/multimodal-demo-active-ui.md)、spec/plan `2026-10-23-multimodal-demo-active-ui` 与同名ledger/final-review。
- 实际增强Demo/shared AX生成→首观察update→RUNNING取消、同PID重入成功、再次RUNNING update后speech-tab活动离页取消、返回300ms不自动重启、再次重入成功及close；11gates全部true。预算256仅给控件重组窗口，重入32；不是benchmark/同步firstcallback取消承诺。
- 最终source29157258921c324f2c08692e136ccb6da608d622，iPhone16ProMax/iOS27.0.1，PID21348/runId43C3DAB4-ACD8-4384-ACAC-198065BB7D58；raw/summary/install/launch在 `artifacts/verification/multimodal-demo-active-final-20261023/`。raw SHA25608dfd7005148935a6545c2fdb82902956ea2639853f82b01f4539ecd46d542bd。exact built metadata/lock先检查，再装机执行。
- 新helper2/2、全Demo40/40、tools118/118、generator Speech10/10、defaultsimulator/enhancedunsigned+signeddevicebuild与strictSwift0通过；实际RED日志/资产复核全部保留。仅validation build和第三runtimearg启用，无KMP/public ABI/默认backend变更。
- 下一独立包：本地mobile MCP已检查但真机 agent 未安装，foreground/元素读取失败；安装需额外 provisioning profile，暂不执行。**物理touch、真实systempicker文件选择、实际OS后台仍unverified**；AX不证明视觉像素或命中。待有授权 agent 后再验，不重试旧XCTest runner初始化门槛。短视频/Android真backend/自然质量/资源另包。
- 每Task/最终仅作者自审，独立reviewpending、无APPROVE；保护hash/state/index不变，只本地提交、无push/merge/发布/下载/录音/私人媒体/签名资产变更。所有旧Video/P6/ASR/最低系统/质量/资源缺口保留，仍5/12完成、7未收口，P9active。

## P9 Enhanced Demo bootstrap/shared AX integration（2026-10-23，历史检查点）

- 接续6136a62；design558b057、helperd498783、接线10d65b8、诊断7e6250e、AX等待修复51d3b72，文档检查点同次本地提交，最新HEAD以git为准。见[增强Demo集成验证](validation/multimodal-demo-integration.md)、spec/plan `2026-10-22-multimodal-demo-integration`、同名ledger/final-review。
- 实际增强DemoController准备→真实MLX available→App-local AX激活共享tab/select→真实pickerdelegate注入唯一合成fixture→AX生成→真实VM/SDK输出→共享AX文本/phase/identity→旧Speech/Video idle页签动作→hostclose通过。显式buildflag ODAI_MULTIMODAL_DEMO_VALIDATION=1（须全部增强flags）与双runtimeargs隔离验证；默认/Android仍unavailable，KMP/header/旧导出未改。
- 最终source51d3b7213cbdf41cdac5747409ac63b75b3a5615，iPhone16ProMax/iOS27.0.1，PID21217/runId6CEF2880-772D-4175-88BD-3CF919E68429；9gatespassed，raw/summary `artifacts/verification/multimodal-demo-integration-final-20261023/`。旧两次AXfalse报告保留；最终initialdisabled=true证实等待notEnabled解除必要，随后RUNNING→SUCCEEDED。不能把outputRendered门禁名当视觉像素/物理touch通过。
- helper5/5/全Demo38/38，tools114/114，generator3+Speech10，defaultsimulator/enhancedunsigned+signeddevicebuild通过。早期strictlint一个blockcomment被shell掩盖，最终corrected-format实际exit0，失败记录保留。模型14/fixture/lock复核，exactcommitmetadata检查后才运行，无下载/录音/私人媒体/签名资产变更。
- 下一独立包：**共享页面App-local AX首update后取消/活动离页与重入**，先设计/计划；不重做前包程序化host生命周期。物理触摸/实际OS前后台/真实systempicker文件选择仍未验；不重试已确认的旧Video runner初始化门槛。短视频/Android真backend/质量/资源另包。
- 每Task/最终仅作者自审，独立reviewpending、无APPROVE；保护hash/state/index不变，只本地提交、无push/merge/发布，日志/artifacts保留。旧Video/P6/ASR/最低系统/自然质量/MetalGPU/energy等缺口不被替代；仍5/12完成、7未收口，P9active。

## P9 Programmatic KMP host/VM real-device lifecycle（2026-10-22，历史检查点）

- 接续ab40fec；design671f2d0、report2e8eb1d、consumer a24866d、built provenance2868bee，验证/本检查点同次本地提交，最新HEAD以git为准。见[真机host/VM验证](validation/multimodal-demo-device.md)、同名spec/plan及`.superpowers/sdd/2026-10-22-multimodal-demo-device/` ledger/final-review。
- 独立bundle io.ondeviceai.multimodallifecycle，实际KMP VM/host→IOS bridge→真实client/MLXVLM/ModelStore，不替换runtime输出。授权iPhone16ProMax/iOS27.0.1，source2868bee、runId5514FD5C-44A9-4E1F-8CEB-EE154A50406D、同PID20852；10 gates/5请求成功：available、成功/实际身份、首update取消/释放后终态、drain后重入、host后台/foreground300ms不重启、活动close/input cleanup/modellease释放及VM/bridge关闭后typed拒绝。raw/summary在artifacts/verification/multimodal-device-provenance-20261022，严格validator通过。
- tools112/112、report5/5/generator3/3、新Swiftstrict0、unsigned/signed真实device build；缺suite RED与provenance失败保留。实际built plist先发现自定义generated key丢失，显式plist修复RED→GREEN后最终commit/hash绑定。模型14/14和fixture/lock复核，仅复制公开固定资产到独立bundle，无下载/录音/私人媒体/签名改动。
- **边界与下一包：共享Compose页面/增强Demo真实集成验收**。本包只程序化host/VM，不能称页面渲染/导航触摸/system picker/实际OS前后台/增强Demo async准备通过。下一包独立设计/计划，先补可执行页面/host接线与有界验证，已知旧Video runner外部初始化超时不反复重试。Android继续明确unavailable；短视频/自然质量/资源/生产backend另包。
- 每Task/最终仅作者自审；独立review仍pending，无APPROVE。保护hash/state/index精确不变，日志/artifacts/.superpowers保留，只本地提交，无push/merge/发布。旧Video/P6/ASR MISSING_ASSETS/最低系统/质量/MetalGPU/energy等缺口保留，仍5/12完成、7阶段未收口，P9 active。

## P9 Optional fixed Apple MLXVLM Demo host（2026-10-22，历史检查点）

- 接续picker1ece99d；设计/计划30e691b，Task1 5afb045，Task2与本检查点同次提交，最新HEAD以git为准。见[可选MLX host验证](validation/multimodal-demo-mlx-host.md)、同名spec/plan与`.superpowers/sdd/2026-10-22-multimodal-demo-mlx-host/` ledger/final-review。完成包不重做。
- 仅独立增强生成工程三flag `ODAI_VIDEO_DEMO=1 ODAI_MULTIMODAL_DEMO=1 ODAI_MULTIMODAL_MLX_DEMO=1` 链接apple-mlx，iOS18/独立bundle io.ondeviceai.multimodalmlxdemo/固定lock；运行另需 `--multimodal-mlx-local-model`。Documents/model canonical固定模型经ModelStore→lease→MLXVLMBackend→client→既有IOS bridge/host/VM，default/Android仍unavailable。准备取消/失败精确释放，销毁owner取消、晚到host关闭、inactive完成background、foreground不重启，准备期间延迟shared控件创建。
- 新factory6/6与准备owner3/3，完整Demo33/33；generator11/11，全tools104/104、strict新Swift0；default完整unsigned simulator与enhanced完整unsigned generic iOS device构建通过，既有cache/固定lock，无下载。现有XCTest/ICU/actor/C++ warning保留，不当最低系统验证。原模型14/14及fixturehash已复核，本包无设备安装/运行/复制。
- 下一独立包：**KMP页面到真实MLXVLM设备生命周期**，先独立设计/逐Task计划，再做成功/首update取消/drain/同进程重入/后台不自动重启/close/lease释放/关闭后拒绝。只已有固定模型/合成fixture与授权设备。iOS实际触摸/真实system picker仍未验；短视频/Android真backend/自然质量/资源另包。
- 每Task范围与最终仅作者自审；独立review pending，无APPROVE。保护hash/state/index不变，只本地提交，无push/merge/发布/下载/录音/私人媒体/签名变更。旧Video UI初始化超时/P6实录/Android ASR MISSING_ASSETS/最低系统/自然质量/Metal GPU/energy与独立review缺口保留。仍5/12完成、7阶段未收口，P9 active。

## P9 Single-image Demo picker/shared actions（2026-10-22，历史检查点）

- 接续29458bb；独立设计/计划7eda196，Task1 shared actions2cdf094，Task2 Android433121c，Task3 iOS/检查点同次提交，最新HEAD以git为准。见[picker验证](validation/multimodal-demo-picker.md)、同名spec/plan及`.superpowers/sdd/2026-10-22-multimodal-demo-picker/` ledger/final-review。已完成包不重做。
- shared可选选择/清除/离页动作、新专属iOS host入口；Android Intent extra `odai.multimodal.enabled`默认false，GetContent→IO bounded staging→host/store opaque token，后台/close接线；iOS独立生成Demo双flag `ODAI_VIDEO_DEMO=1 ODAI_MULTIMODAL_DEMO=1`，PNG/JPEG单文件picker、host/lifecycle，旧保护工程不变。默认仍UnavailableBridge，无模型/backend接线。
- Task1 shared60/60、双framework link、实际Swift五入口编译；API35 Compose actions/旧导航通过。Task2 copy4/host8/全Demo26新执行，APK/lint0error9既有warning；TECNO KM9/API35实际optOut/optIn unavailable/systemPicker取消/合成content回调copy/clear/后台不重启/目录清理status=passed，真实系统选文件及Android推理unverified。失败fixture/中断日志保留，最终Java test-only provider只提供固定公开三字节。
- Task3真实缺picker符号RED→Demo24/24（3picker+7host+4input+10bridge），格式化后再24/24；工具102/102，strict新Swift lint0，完整独立生成App unsigned simulator编译通过。iOS实际触摸UI/system picker往返仍unverified；旧minimumOS/XCTest/ICU警告保留，不称最低系统通过。
- 下一独立包：**Apple既有固定模型/ModelStore可选host接线**；先复核backend及benchmark设计/计划、固定资产和设备。Android没有生产backend继续unavailable。再另包做KMP页面到真实SDK成功/首update取消/drain/重入/后台不重启/close/lease释放/关闭后拒绝设备闭环。短视频、新Android推理、自然质量与资源测量另包。
- 每Task范围及最终仅作者自审；独立review仍pending，无APPROVE。Ruling：独立iOS生成工程保留原保护文件；Android异步failure共享ticket；synthetic content provider不替代真实系统文件选择。只本地提交，无push/merge/发布/下载/录音/私人媒体/签名变更；两保护hash/state精确不变且未暂存，日志/artifacts保留。
- 旧Video UI初始化超时、P6实录、Android ASR MISSING_ASSETS、最低系统、自然质量/Metal GPU/energy及独立审查缺口原样保留。仍5/12完成、7阶段未收口，P9 active。

## P9 Single-image native host lifecycle（2026-10-22，历史检查点）

- 输入包2c2aaef后独立设计cc99556、Android Task1 854d256，Apple Task2与本检查点同次提交，最新HEAD以git为准。独立spec/plan `2026-10-22-multimodal-demo-host`，同名`.superpowers/sdd/` ledger/final-review。见[host验证](validation/multimodal-demo-host.md)。工作树/index/保护hash复核；不重做完成包。
- 双端MultimodalHost只组合现有VM/typed bridge/input store，以注入本地File/URL选择建立latest/once-only ticket、替换/clear退役、晚到过滤、后台不重启、close一次与lease保留。默认真实UnavailableBridge。没有system picker/URI/shared选择按钮/平台opt-in/session owner或模型接线，不称真实picker/设备推理通过。
- Android缺符号RED后host7/7、全Demo21/21新执行，APK/lint0error9既有warning；Apple generator实际source-set RED→GREEN3/3、缺host符号RED，修正遗漏throws签名后Demo21/21（新host7+input4+bridge10），格式化后再次21/21。新生成Binding10/10、工具101/101与strict格式检查通过。生成工程和全部失败日志保留；既有XCTest/ICU链接版本warning不等于最低系统设备通过。
- Ruling：native lifecycle owner先行，实际picker/shared actions/platform opt-in另包，代价是再一个小集成包。preservePicker=true只保留已经在途的external-picker ticket，可在foreground前接受本地文件但绝不自动生成；下一包须明确接Activity/页面生命周期，不能用注入回调测试当system picker验收。
- 下一独立包：**双端system picker、shared选择/清除动作、平台opt-in与session生命周期接线**。Apple既有固定ModelStore/MLXVLM可选接线和真实KMP设备闭环随后另包，Android没有生产backend继续unavailable。只用固定资产和公开/合成fixture，不读取私人媒体。
- 每Task与最终仅作者自审，独立review仍pending，无APPROVE。全程本地提交、无push/merge/发布/下载/录音/私人媒体/签名变更，保护hash/state精确不变且未暂存；workspace/artifacts/日志保留。
- 旧Video UI初始化超时、P6实录、Android ASR MISSING_ASSETS、iOS实际UI、最低系统、自然质量、Metal/GPU/energy及独立审查缺口保留。仍5/12阶段完成、7未收口，P9 active。

## P9 Single-image Demo input ownership（2026-10-22，历史检查点）

- 接续55a425b；设计/计划0b0f851、Apple Task1 caab202，Android Task2与本检查点同次提交，最新HEAD以git为准。工作树/branch/index与两保护hash复核相符，无并发改动。见[输入验证](validation/multimodal-demo-input.md)、独立spec/plan `2026-10-22-multimodal-demo-input` 及同名 `.superpowers/sdd/` ledger/final-review。不重做UI/state/bridge/后端/benchmark。
- 双端MultimodalInputStore只实现输入所有权：新鲜私有目录、复制regular nonsymlink文件、opaque UUID、registry-only acquire、多lease、选择退役、close封闭、幂等release、最后lease清理。没有picker/host/模型或平台导航接线，默认unavailable仍保留。合成字节测试不是图片质量或真实picker验收。
- Apple新测试先发现旧SpeechInputStore误删foreign sentinel（13/14），必要范围修正为原子非递归Darwin.rmdir；代价是清理失败可留存目录，不接受误删外来文件。修复后Demo ABI14/14、Binding10/10，新鲜完整Speech39/39、Video9/9、生成器3/3和strict格式检查通过。日志保留失败证据。
- Android缺store symbol RED后实现，新输入4/4、全Demo14/14新执行；离线APK/lint通过，0error/9既有warning；工具101/101。最终Gradle539tasks（7执行、532up-to-date），缓存不计新测试。具体命令与日志见验证记录。
- 每Task范围与最终均为作者自审；按用户要求不派子代理，独立review仍pending，无APPROVE。只做本地提交，无push/merge/发布/下载/录音/私人媒体/签名变更。保护文件hash/state精确不变且均未暂存，scratch/artifacts/日志保留。
- 下一独立包：**两端单图host/picker与页面/后台/close生命周期接线**，消费现有VM/typed bridge/store；固定Apple模型/ModelStore和真实KMP设备闭环再拆包，Android无生产backend保持unavailable。真实模型host所需后端验证及backend/benchmark设计计划已读，进入时按实际资产/连接复核。
- iOS实际UI、host/picker往返、KMP→真实MLXVLM、自然质量/Metal GPU/energy/最低系统/Android生产后端与独立审查仍未验。旧Video初始化超时、P6实录、Android ASR MISSING_ASSETS及其他门槛保留；仍5/12完成、7阶段未收口，P9 active。

## P9 Single-image Demo UI（2026-10-22，历史检查点）

- 接手基线e0fc825，branch/worktree/index与保护hash均核对相符。设计/计划f88b43f；Task1共享UI/opt-in98a3891；Task2验证/交接同次提交，最终HEAD以git为准。见[UI验证](validation/multimodal-demo-ui.md)、独立spec/plan `2026-10-22-multimodal-demo-ui` 和同名`.superpowers/sdd/` ledger/final-review。
- 新MultimodalScreen消费既有VM，展示严格backend选择/availability、输入displayName、原始prompt、预算、生成/取消/phase、累计文本、最终实际backend/model/runtime与typed failure。FaceScreen可选model默认null，旧App入口不变；新独立iOS入口保留Face/Identity/Speech/Video参数与旧导出标签。无picker/native host/model/lease接线，默认unavailable。
- Android TECNO KM9/API35实际UI全部具名场景status=passed，含真实default unavailable与旧Speech/Video活动切页；其余生成是UI fixture，不能称推理成功。shared60/60新执行；旧/新Swift入口实际typecheck、含旧双host生成Demo编译、更新Demo ABI10/10、工具101/101通过。Android lint0error/9既有warning。早期AX节点/重组滚动失败与修正均留日志。
- 每Task与最终仅作者自审，独立review仍pending，不称APPROVE。iOS实际UI、KMP→MLXVLM真机、质量/Metal GPU/minimumOS等未验。旧Video UI初始化超时、P6实录、Android ASR及其余roadmap缺口不变，仍5/12阶段完成，P9 active。
- 下一独立包：**双端host/picker与opaque input/lease生命周期**；固定Apple模型/ModelStore可选接线、真实设备闭环继续另包，Android无生产backend保持unavailable。真实模型host之前读后端验证及backend/benchmark设计计划；不下载模型/依赖，不录音/读私人媒体或改签名。
- 仅本地提交，无push/merge/发布。两保护文件sha256与用户固定值精确相同，状态仍tracked dirty/untracked，均未暂存；.superpowers/artifacts/日志保留。

## P9 Single-image Demo State（2026-10-22，历史检查点）

- bridge三Task已提交 `2e96a26`、`b1e6873`、`1291bd2`。下一包按独立交付进一步拆为shared state先行、UI随后，避免导航/ABI/picker/model同时扩张。设计/计划 `5866196`；Task1状态机 `5db0083`；Task2验证/本检查点同次提交，最终HEAD以git为准。验证见[共享状态机](validation/multimodal-demo-state.md)，ledger/final-review `.superpowers/sdd/2026-10-22-multimodal-demo-state/`。
- MultimodalViewModel默认UnavailableBridge；原始prompt/严格选择/预算校验、递增累计update/最终身份、同步callback、cancel等待终态、旧callback过滤、编辑取消、background不重启与一次close已实现。只是shared state，无UI/host/picker/媒体读取/真模型连接。
- 新VM12/12、全shared60/60实际执行；binding21/21为既有缓存，双framework link与Android shared compile成功。最终Android Demo APK/lint560tasks（60执行）成功，0 error/9条既有warning；更新后Demo ABI bridge10/10实际重链/执行、工具101/101。具体日志与缓存边界见验证记录，不把缓存命中称新执行。
- 每Task与最终仅作者自审，独立review仍pending，无APPROVE。无push/下载/录音/私人媒体/签名修改；两保护文件hash不变且未暂存，worktree/artifacts/日志保留。基础iOS15/macOS12/API26与apple-mlx独立不变。
- 下一独立执行包：**shared单图Screen/UI与opt-in入口**。消费现有状态机，默认不可用，展示backend/输入名/prompt/预算/取消/update/实际输出身份；先独立设计/逐Task计划。两端host/picker、固定模型/ModelStore和真机闭环随后另包；短视频与Android真后端继续独立。
- KMP→MLXVLM设备端到端、自然质量、Metal/GPU/energy、最低系统、增强分发与独立review仍未验；旧Video UI初始化超时、P6实录、Android ASR等缺口原样保留，不重复已完成bridge/Foundation/后端/benchmark/state。

## P9 Single-image KMP Bridge（2026-10-22，历史检查点）

- 设计/计划 `88c2fa9`，Task1 `2e96a26` common契约，Task2 `b1e6873` Android adapter；Task3 Apple adapter/独立工程/本检查点同次提交，最终HEAD以git为准。[验证](validation/multimodal-bridge.md)，规格/计划 `docs/superpowers/{specs,plans}/2026-10-22-multimodal-bridge*`，ledger/final-review在 `.superpowers/sdd/2026-10-22-multimodal-bridge/`。不重做已完成 Foundation/后端/benchmark/bridge。
- 单图 opaque input、严格Default/Explicit、累计update和不可变model/runtime身份、UI callbacks、any-thread cancel、drain后lease释放、close/query join已实现。Apple只依赖base Multimodal，Android注入native client，无client明确unavailable。没有Demo UI、视频或Android真推理。
- 新鲜common21/21（新增4）、Android bridge10/native12、Video17/Speech25回归、lint/compile、Binding/Demo framework成功；Apple两种ABI各10/10；工具101/101。旧Video导出selector曾被generic入口改变，专属前缀修复后旧Swift6/6通过，旧Speech/Video源码未改。
- Apple生成工程保留于 `artifacts/verification/multimodal-{binding,demo}-20261022/`。默认系统Ruby2.6与3.4 gem混用的失败已定位，使用既有3.4 binary，无安装；XCTest17和Demo既有ICU18.5链接版本警告保留，不能当iOS15设备验证。
- 每Task和最终仅作者自审，用户要求单人执行；独立review仍pending，无APPROVE。基础iOS15/macOS12/API26与独立apple-mlx不变；无push/下载/录音/私人媒体/签名资产变更。两保护文件hash精确不变且未暂存，日志/artifacts不清理。
- 下一独立执行包：**单图Demo共享状态机与UI**，只消费typed bridge、平台默认unavailable，先独立设计/逐Task计划。双端host/picker、固定模型接入和真机生命周期随后另包；短视频、Android真实后端不一起扩入。KMP→真实MLXVLM设备端到端仍unverified，不用本包fixture替代。
- 自然图片质量、Metal/GPU/energy、最低系统、增强包分发、旧Video UI初始化超时、P6实录、Android ASR及旧独立审查缺口保留；整个P9/roadmap仍active。

## P9 Fixed-Image Benchmark（2026-10-22，历史检查点）

- 设计/计划提交 `fdf55e1`，Task1 validator `b97f0b3`，Task2 observer/consumer/测量 `f9512a0`。Task3验证/交接随本检查点提交，HEAD以git为准。设计/计划 `docs/superpowers/{specs,plans}/2026-10-22-apple-mlxvlm-fixed-image-benchmark*`，ledger与final-review在 `.superpowers/sdd/2026-10-22-apple-mlxvlm-fixed-image-benchmark/`。不重做已完成Foundation/后端/benchmark包。
- 真机 iPhone16ProMax/iOS27.0.1，固定模型/runtime/fixture、Debug build、temperature0、32tokens；1次warm-up＋5次新backend/client测量。严格校验后的加载p50/p95=311.094917/323.690167ms，首交付update=763.803375/774.153375ms，总耗时=924.392542/934.323334ms；不是冷启动、纯首token、Release SLA或P11验收。
- process RSS baseline/采样peak/delta=2,087,469,056/2,437,054,464/349,585,408B，10ms请求间隔，不能冒称Metal/GPU峰值或模型独占内存；Metal/GPU与energy仍unverified。红方块rubric5/5，但原文都多描述“flag”，不能算完整图片描述准确或自然质量通过。
- 原始 `artifacts/verification/apple-mlxvlm-fixed-image-20261022/device-result.json` 与 `summary.json`；runId `6A376940-9A43-4654-BCA2-33EDD3D2C453`，PID20276，绑定完整源commit `f9512a0f9fb0956a41e5e99eb4dd1e0a05011169`。raw/summary hash、边界与重校验命令见[后端验证](validation/apple-mlxvlm-backend.md#有界固定单图-benchmark2026-10-22)。忽略目录的资产/日志/.superpowers均保留。
- 新验证host12/12、工具98/98、定向10/10及缓存离线signed device build；基础461/461是前包证据，未重跑冒称新证据。作者自审修正nil RSS填0/未同步读取、异步close、事件顺序与未知source接受问题；observer故障注入RED、恢复GREEN已保留。独立review仍pending，无APPROVE。
- 当前会话不派子代理，无push/发布/依赖或模型下载/录音/私人媒体/签名资产变更；保护文件hash与用户固定值相同，不修改/暂存。旧Video UI初始化超时、P6实录、Android ASR、基础最低系统及旧包独立审查缺口不被新结果替代，整个P9/roadmap仍active。
- 下一独立执行包选择：**单图KMP bridge公共契约与平台adapter**。先复核现有Multimodal client及Speech/Video bridge模式，独立设计/逐Task计划，范围只含单图、类型化选择/错误/update、输入lease与cancel/drain/close；Apple可选MLX依旧独立，Android无已注册后端时明确unavailable，不下载新模型。共享Demo UI、短视频与Android真实推理分别后续分包；本检查点不宣称已实现下一包。

## P9 Optional Apple MLXVLM Backend（2026-10-22，历史检查点）

- 已提交cdb959a设计/计划、440af94独立package shell、815435b真实runtime与取消生命周期。Task3消费者/验证/交接随本检查点同次提交，最新HEAD以git为准。规格/计划为docs/superpowers/{specs,plans}/2026-10-22-apple-mlxvlm-backend*，ledger .superpowers/sdd/2026-10-22-apple-mlxvlm-backend/progress.md，不重做已完成项。
- 独立apple-mlx的ODAIKitAppleMLXVLM仅arm64 iOS18+物理设备推理，macOS/simulator明确unsupported；root apple不引MLX，基础iOS15/macOS12/API26不变。固定14文件canonical manifest、ModelStore lease、单图有界snapshot、maxTokens与真实producer join已接入；每请求重新load，无跨轮聊天历史。
- [后端验证](validation/apple-mlxvlm-backend.md)：host11/11、root Swift fresh461/461、工具90/90；device target、独立consumer unsigned/signed build与install成功。设备结果artifacts/verification/apple-mlxvlm-backend-20261022/device-result.json为success，首轮3625.341709ms、update后取消/同进程reentry/lease保护/close释放/关闭拒绝均通过。此为新SDK链路，不挪用旧probe成功。
- tools/generate_mlxvlm_consumer.py生成fresh独立consumer、源码与固定lock，拒绝覆盖非空输出；模型14文件与fixture hash复核。只用既有缓存，无下载/录音/私人媒体/push/发布/签名资产变更，两个保护文件hash不变且未暂存。
- 用户要求inline执行，范围/最终仅作者自审，独立review仍pending，不能声称APPROVE。未验证增强包binary分发、最低系统、质量/峰值Metal GPU资源/首token统计；其他Video UI/P6等旧缺口保留。
- 下一本地入口：有界固定单图质量/性能和资源证据设计，沿用已有固定资产与合成fixture，禁止泛化单fixture成功为质量通过；随后分包推进短视频、KMP/Demo与Android。P9/roadmap未关闭。

## P9 Multimodal SDK Foundation（2026-10-22，历史检查点）

- 设计/计划43929e4；Task1 cf3a0e6 Swift契约/actor client、Task2 8476dd8 Android对等模块、Task3 e1ff006分发/consumer、Task4为本检查点同次提交。最新HEAD以git为准，不重做。规格：[设计](superpowers/specs/2026-10-22-multimodal-sdk-foundation-design.md)；[四Task计划](superpowers/plans/2026-10-22-multimodal-sdk-foundation.md)；ledger .superpowers/sdd/2026-10-22-multimodal-sdk-foundation/progress.md。
- 验证：Swift新增契约4/4、client10/10，全量461/461；Android12/12+lint、总门禁716tasks（15执行）；工具88/88、Apple总门禁含新binary consumer通过。Android取消availability晚启动竞态已有真实失败断言和修复回归。详见[验证](validation/multimodal-sdk-foundation.md)。
- fresh十XCFramework在artifacts/verification/multimodal-foundation-20261022/distribution/；Core+Multimodal-only iOS15 device/simulator unsigned consumer、Android Maven local实际消费与project consumer/lint通过。父目录foundation-evidence.json记录binary/interface/POM证据，SDK源提交e1ff006。仅Core与系统/既有协程，不含MLX/模型；编译不等于最低系统设备执行。
- 用户要求当前会话执行，不派子代理。每Task范围自审及单独final-review.md保留，未发现自审范围阻断项；独立审查pending，不冒称APPROVE/可发布。本轮无push/远端发布/下载/录音/私人媒体/签名变更。保护文件hash不变且未暂存，scratch/日志保留。
- 下一独立工作包：可选Apple MLXVLM后端。先复核已选SmolVLM2/MLX固定revision、已有本地资产与model lease/加载/释放/取消，再独立设计/计划和RED→GREEN。基础iOS15/macOS12/API26不变，高版本runtime独立可选；不直接把probe当SDK、不下载资源。
- 完整P9仍需真实图片/短视频、ModelStore adapter、KMP bridge/Demo、Android另一平台、质量/性能/最低系统设备及独立审查，本包不关闭P9/roadmap。Video Demo UI初始化超时、旧Video/AppleVideo/音轨复审、P6实录/Android ASR及其余阶段门槛保留。下方历史入口不覆盖本检查点。

## Video bridge / Demo 检查点（2026-10-21，验收缺口保留）

- 用户最新要求：当前会话直接执行，不再派子代理；按 roadmap 推进，每完成一个阶段更新本交接。不 push/发布/下载/录音/读取私人媒体/修改签名资产，保护文件不覆盖/暂存。
- Task1–5 已提交：`9bad71d` common契约、`e522c9d` Android adapter、`fd19f2c` iOS adapter/ABI fixture、`aa0e837`共享 VM/视频页、`3b0560c`双端 host与独立工程 opt-in。不要重做。当前 Task6 的UI测试/验证文档及检查点随同次提交；最新HEAD以git为准。
- 本地证据：common4/4、Android adapter10/10/native client7/7、VM8/8；iOS Binding ABI6/6、Demo adapter+host9/9；Android host3/3。工具83/83（使用既有Ruby3.4 wrapper，默认系统Ruby2.6混用gem失败已保留并纠正）；Apple总门禁/Swift447/447、Android总门禁625tasks（14执行）通过；独立iOS生成工程的signed device/unsigned simulator build通过。原生SDK源码未改，不重打前包九XCFramework。
- Android TECNO KM9/API35 新 Video smoke：明确unavailable、真实GetContent打开后取消/无输入、后台返回仍IDLE/不启动处理均通过；真实处理/输出及实际选中文件的ActivityResult设备往返仍未验，不能用host3项单测替代。Android宿主不会读取选择的媒体。
- iOS iPhone16ProMax/iOS27.0.1：固定公开视频+合成AAC已provision到本项目Documents，UI target构建成功；两次runner均在 `Timed out while enabling automation mode.` 处失败，0视频用例通过。首次未完成验收仍是新Demo导入/成功/preserve/播放分享清理/活动取消drain/重入/后台不重启。不要用前包SDK harness设备成功替代新Demo UI。
- 普通设备包已恢复：`task-6-final-normal-{build,install}.log`成功，Info.plist不含两个sharing key。测试fixture留在本项目Documents；后续仅为显式UI复跑临时用忽略目录plist暴露，结束再次恢复。保护hash与交接固定值一致。
- 子代理执行/恢复因429/capacity启动前失败，未产生实现或审查；用户改为当前会话执行后仅作者独立步骤自审，不冒称独立APPROVE。旧Foundation/AppleVideo/音轨修复复审缺口保留，无push。
- 规格/计划：`docs/superpowers/{specs,plans}/2026-10-21-video-bridge-demo*.md`；ledger `.superpowers/sdd/2026-10-21-video-bridge-demo/progress.md`。详细命令、失败与分层证据见[视频 Demo 验证](validation/video-bridge-demo.md)。第一项未完成Task6验收/审查先保留，其他独立可执行入口为P9 VLM SDK foundation设计；不直接把探针当SDK后端。P7 Android/质量/传递函数/最低系统/native export中途取消、P6实录及其他roadmap缺口不关闭。

## 历史检查点（以下入口已被上述 Video bridge/Demo 检查点取代）

## AppleVideo 音轨检查点（2026-10-21，历史）

- `0902e8e`实际音频passthrough、`26dcd0f`默认preserve、`7dcb25d`审查后视频时间线校验。Swift447/447、mux8/8、工具79/79、Apple总门禁与fresh九XCFramework通过。默认preserve支持无音轨或单条零起点/等长/无空白编辑AAC（44.1/48kHz、单/双声道），discard保持显式丢弃；视频固定profile/系统下限不变。
- 设备suite成功PID19288/run`B06660C7-6E4D-4F13-AAED-F8DC83D2494F`：带AAC的两轮各48帧、第1帧取消无残留、无音轨默认preserve、close/lease/input不变均通过；导出视频PTS/HEVC/BT.709与完整decode通过，AAC压缩payload与输入逐字节相同。native export阶段中途取消尚未单独验，不得冒充。
- 分发使用`artifacts/verification/apple-video-audio-20261021/distribution-reviewed/`，早期`distribution/`为审查修复前，不替代最终证据。Core+Video-only设备/模拟器消费者、weak VT与两种harness构建通过。详见[音轨验证](validation/apple-video-audio.md)。
- 音频包独立审查1MEDIUM（导出视频时间线缺校验）已修复并回归，未复审/不声明APPROVE；基础包恢复审查capacity、VT首包恢复审查429，仍pending。保护文件hash未变；无push/下载/录音/发布。
- 下一本地入口KMP Video bridge/Demo；计划/ledger以`2026-10-21-apple-video-audio`包为最新已完成实现。P6真实录音、P7质量/Android/最低系统及其他roadmap缺口继续保留，不重复已完成切片。

## AppleVideo SDK 检查点（2026-10-21，历史）

- 独立 `ODAIKitAppleVideo` Task1–3 已提交：`4e77325` workspace/lease、`5c6ba01` backend协调、`e996d5d`真实VT引擎；`51d98bf`修复取消后晚到错误改变终态，回归RED→GREEN，Swift438/438通过。
- 当前首包仅支持非模拟器iOS26+、模型ready、固定320×180/24fps/1…48帧/BT.709 SDR、4×和显式discard；基础iOS15/macOS12不变，无模型下载。音轨preserve默认请求明确拒绝。
- 修复后fresh九XCFramework位于 `artifacts/verification/apple-video-backend-20261021/distribution-cancel-fix/`。Core+Video-only设备/模拟器消费者在iOS15 target编译链接通过；设备VT符号为weak，模拟器无VT专用符号。签名device与unsigned simulator harness均构建通过。
- iPhone16ProMax/iOS27.0.1修复后固定公开fixture套件 `state=success`，PID19252，runId `6BFB16C9-8000-44AF-9018-BD0F72F155CD`：48帧成功、第1帧后取消且无残留、同进程48帧重入、close保留已交付lease、release清理、关闭后拒绝新任务、输入未变。导出的单轨HEVC1280×720/24fps/2秒/BT.709、连续PTS及完整decode通过，见[验证](validation/apple-video-backend.md)。
- 计划/规格 `docs/superpowers/plans/2026-10-21-apple-video-backend.md` 与同名design，ledger `.superpowers/sdd/2026-10-21-apple-video-backend/progress.md`。独立reviewer容量失败，无APPROVE，保持pending；不push。
- 下一本地工作包：有界音轨preserve，随后KMP/Demo接入；先以新包规格/测试明确音频兼容、同步、取消清理边界。P7质量/传递函数/Android/最低系统仍独立，P6不自动录音。两个受保护文件hash保持不变，不暂存或覆盖。

## P7 检查点（2026-10-21，历史）

- 已继续 roadmap 的独立 Video SDK Foundation，未重做语音 Tasks1–5。提交 `17a330e` Swift契约、`fd2e537` Swift client、`112634d` Android模块；最新 HEAD 以 git 为准。
- 计划/规格：`docs/superpowers/plans/2026-10-21-video-sdk-foundation.md`、同名 design；ledger `.superpowers/sdd/2026-10-21-video-sdk-foundation/progress.md`。Task1–4本地实施/分发已验证，独立审查因服务容量失败待恢复。后续入口是独立 Apple VideoToolbox 后端包，不把探针直接当 SDK 后端。
- 已验证 Swift426/426、Android video7/7 + lint、工具78/78、Apple总门禁与Android610tasks通过；fresh八XCFramework以及仅Core+Video的设备/模拟器消费者编译链接通过。分发输出 `artifacts/verification/video-foundation-20261021/`，详细证据见[视频基础验证](validation/video-sdk-foundation.md)。独立审查因服务容量失败待恢复，未声明 APPROVE。
- 仍没有真实视频SDK处理器、KMP/Demo视频流、Android模型或新质量/最低系统验收。P7保持未完成。P6中文实录终态/取消/重入及修复独立审查、P9/P10/P11缺口继续保留；没有自动录音、资源下载或远端push。
- 两个受保护文件状态/hash保持一致，仍不得暂存/覆盖。继续从新 ledger 的首个未完成项恢复；不要按下方历史“当前入口”重复旧工作。

## Speech 恢复检查点（2026-10-21，历史）

### 本次接手后的更新（优先于下方上午记录）

- 接手基线 `0526988`；现新增 Demo locale admission 修复，最新提交以 `git log -2 --oneline` 为准。真实 `SpeechRecognitionClient` 回归复现：默认 `en-US` 不可用会在 registry 阶段阻断已就绪的 `zh-CN`。修复移除默认英语实例：无请求 locale 的列表查询只检查共享 Speech 授权/关闭状态，按实际请求惰性创建 canonical locale backend，再由该原生 backend 严格校验本机就绪和权限，不回退语言。
- `task-6-locale-admission-red.log` 记录上述真实 client 断言失败，退出65；GREEN `task-6-locale-admission-green.log` **39/39**，覆盖文件/麦克风、不可用请求不回退、canonical cache/close、拒权和关闭后拒绝启动。native iOS fixture `task-6-resume-native-ios.log` **43/43**。均为测试输入，不是自然麦克风验收。
- 接手后、此 Demo Host 修复前的完整门禁：Swift **414/414**、Apple 全脚本门禁与consumer/原App构建成功；Android **569 tasks（22 executed、547 up-to-date）**；Python **77/77**。新增修复只涉及 Demo Host/Host fixture，不改 SDK/shared/Android；之后完整生成 Host fixture 与 signed device build 已通过。
- 设备已装新普通包：生成工程仍为 `artifacts/verification/microphone-locale-router-20261021/`，最新 build 输出为 `artifacts/verification/microphone-locale-admission-device-20261021/`。build/install 证据 `task-6-locale-admission-device-{build,install}.log`；无诊断/合成源，无 sharing/background audio key。此次未自动开始录音，中文终态仍未收到。
- 独立 `code-reviewer` 与 `architect` 调用均在执行前因模型容量不足失败；路由/1110/locale 与本次 admission 修复均仍无新的独立 APPROVE。作者按独立步骤审读修改与回归，不冒充独立审查。Task6/P6/roadmap 未关闭；两个保护文件hash不变。
- 本次只读查询：origin 分支仍为 `472ca9351eb69c09d863ef7bc267c280b068b846`；极狐查询返回 `SSL_ERROR_SYSCALL`，无法确认当前远端相等。未push。当前七XCFramework `artifacts/P6/microphone-locale-20261021/` 仍对应最新 SDK；本次只改Demo Host，无需重打相同 SDK 产物。

当前待办：先使用用户已回复的中文终态；若没有，等待用户主动在 `zh-CN` 下完成固定句测试，仅报告状态/错误码。真实完成后继续显式取消、同进程重入、TTS往返、后台取消/前台不重启及拒权恢复。独立复审恢复后覆盖三项上午修复和本次 admission delta。不要用“继续”当作设备成功报告，不重复 Tasks1–5 或已通过的整包门禁。

工作树 `.worktrees/foundation` / `feat/sdk-foundation`，语言路由实现提交 `09f5f6e fix: route iOS Demo speech by requested locale`；路由修复 `29b388d`、失败复测记录 `83d5443`、1110→NO_RESULT 修复 `027c7ad` 均已本地提交。恢复时以 `git log -2 --oneline` 核对最新 HEAD。麦克风 Tasks1–5 已完成，不重做；Task6、P6 与整个 roadmap **未完成**。计划 `docs/superpowers/plans/2026-10-20-ios-microphone-asr.md`，ledger `.superpowers/sdd/2026-10-20-ios-microphone-asr/progress.md`。

- 原 Task1–5、旧 Task7 独立审查已 APPROVE；原分发/Android/KMP/host门禁见[验证记录](validation/ios-microphone-asr.md)。路由及新的 1110 修复独立审查仍因模型容量不足未执行，没有新的 APPROVE；不推送远端。
- 首次真机 `FAILED PLATFORM` 两次均为 categoryChange(reason3)被无条件判失效。`29b388d` 仅放过与启动健康快照完全一致的 categoryChange，其余仍失效。新普通包安装后用户回报一次 `SUCCEEDED ASR 完成`。
- 随后的“取消/重入”用户回报都 FAILED。成功接入诊断后，实际两轮日志均为 `finishInput`、没有 `cancel`，错误为 `kAFAssistantErrorDomain/1110`。先一轮0.77秒/24000frames，后一轮4.63秒/211200frames（4.4秒PCM），均有非零float样本。**不能把较长一轮失败仅归于录音过短，也不能将非零样本等同可识别语音。** 录音/转写未保存或导出。
- Apple SDK `SFSpeechRecognitionTask.h` 的官方错误表明确1110为“Failed to recognize any speech”；新的窄修复映射既有 `.noResult`，不伪报成功。显式取消保持取消、其他domain/code保持平台错误。新增2测试先断言RED再GREEN；Swift全套414项0失败，iOS native fixture43/43。日志 `task-6-no-speech-{red,green,swift-full-final,native}.log`。
- 自动合成对照不使用麦克风：同一固定CAF，文件识别成功；最初复用可变buffer的临时探针1110（探针实现问题），修正为独立buffer后，整段/实时、不保留数组、iOS27 readonly-copy和48kHz均成功。日志 `task-6-{synthetic-comparison,buffer-variants,buffer-copy}-events.log`。**不把原探针失败解释为系统buffer识别不支持。**
- 进一步在忽略目录的 SDK副本仅替换 Engine 为合成PCM源，保留生产backend/delegate/session/finish/drain/release：首次完成、取消、同进程重入和再次完成成功。日志 `task-6-native-synthetic{-lifecycle,}-events.log`。这是真实系统识别+合成源，不是实际麦克风或UI取消验收。
- 诊断工程 `artifacts/verification/microphone-reentry-diagnostic-20261021/` 内含 bridge/session副本、`apple-diagnostic/`和合成探针，均不得提交。最初工程引用替换未命中相对路径导致诊断未生效，已用xcodeproj真实引用修正并确认 `ODAI_MIC_DIAGNOSTIC ready`；可靠现场日志为 `task-6-reentry-buffer-events.log`。
- 结束自动对照后曾恢复普通生成工程 `artifacts/verification/microphone-live-fix-20261021/`。目前设备上改为新普通 locale-router 包 `artifacts/verification/microphone-locale-router-20261021/`，生产源码、无合成输入或诊断源码；构建/安装见 `task-6-locale-device-{build,install}.log`。原七XCFramework `artifacts/P6/microphone-20261021/` 已被新鲜分发 `artifacts/P6/microphone-locale-20261021/` 取代；新包两个 archive、七个 XCFramework 与 Speech binary consumer 均成功，见 `task-6-locale-{xcframeworks,speech-consumer}.log`。没有发布或上传。
- 两个受保护文件及hash见下方，未覆盖/暂存/提交。没有push、force-push、语言资源安装、签名账户资产/网络配置变更。iPhone UDID `00008140-0008703436D3001C`，沿用team `4X996UV8LC`，bundle `io.ondeviceai.ondeviceaidemo`。
- 用户确认失败轮次使用中文或中英混合；此前 Demo Host 却将 Apple 识别器固定为 `en-US`。现已加入 Demo 内部 `SpeechLocaleRoutingBackend`：保持稳定 `apple.speech.recognizer` ID，按请求 locale 缓存 Apple backend，共用同一个 `DemoSpeechAudioSessionProvider`，不回退语言；共享 UI 文案改为按请求语言。新增 locale 路由回归后生成桥接/Host/Session 测试 **36/36** 通过（RED 日志 `task-6-locale-router-red.log`，GREEN `task-6-locale-router-green-final.log`），shared framework/allTests 成功（`task-6-locale-shared.log`），真机包构建成功并安装（`task-6-locale-device-build.log` / `task-6-locale-device-install.log`）。

下一会话从这里继续：先核对 `git status`、HEAD、受保护哈希及设备连接，不重做已通过的 Tasks1–5。设备已安装并启动 locale-router 生产包；让用户在语音页把“语言 / locale”设为 `zh-CN`，主动开始麦克风 ASR、朗读固定句“今天我们测试手机上的语音识别功能”，结束录音后只报告状态/错误码，不发送转写文本。若本会话末尾的异步提问已有答复，先使用答复，避免重复要求朗读。忽略目录中文诊断包仅确认 `zh-CN` recognizer `available=true onDevice=true`（`task-6-zh-cn-events.log`），尚无现场中文朗读终态。随后以真实完成结果验证取消、同进程重入、后台取消与前台不自动重启；诊断须只记状态/标量，不保存音频/自然转写。当前路由/1110/locale 修复复审调用均因模型容量不足未执行，**没有新的独立 APPROVE**；可用时做一次针对 `29b388d`、`027c7ad`、`09f5f6e` 的独立复审。iOS15、Android API26/ASR MISSING_ASSETS、自然质量与断网、P7/P9/P10/P11缺口保留。无远端 push。

以下为旧 Speech Demo 包的历史交接，旧“当前入口/HEAD”不覆盖本检查点；旧Task7追加审查和原始证据仍有效。

## 恢复位置

- 仓库：`/Users/daubert/Github/ondevice-ai-kit`
- 工作树：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`
- 分支：`feat/sdk-foundation`
- Task 6 实现提交：`b272b76 feat: wire speech Demo hosts and isolated iOS project`
- 当前入口：Task 7 真机/本地门禁已通过；追加 UI/Done 独立审查因模型容量不足待恢复，尚不 task-done。不要从 Task 4 重新执行。
- 实施计划：`docs/superpowers/plans/2026-10-19-speech-demo-bridge.md`
- 规格：`docs/superpowers/specs/2026-10-19-speech-demo-bridge-design.md`
- 执行 ledger：`.superpowers/sdd/2026-10-19-speech-demo-bridge/progress.md`

Tasks 1–6 已提交到本地分支。Task 7 验收改动使用提交名 `test: validate speech Demo lifecycle and module isolation`；恢复时用 `git log -3 --oneline` / `git status --short` 查询最新提交和工作树，不依赖旧 HEAD 快照。受保护用户文件继续保留。尚未推送 GitHub 或极狐，远端同步待最终审查门禁。


## 已完成的计划任务

### Task 1 — 公共 Speech Bridge 契约

提交：`6788b1b feat: define typed speech bridge contracts`

已提供 Default/Explicit 后端选择、TTS/ASR capability、Microphone/AudioFile opaque 输入、后端描述、类型化请求/结果/回调、稳定 failure code、close callback 与 `UnavailableSpeechBridge`。Swift 导出 ABI 已通过实际 typecheck。

### Task 2 — 能力级后端查询

提交：`331de92 feat: expose capability-scoped speech backend queries`

Apple 与 Android 的 TTS/ASR facade 均可独立 `listBackends()`；结果按 backend ID 排序，保留 unavailable reason、minOS、offline，并继续由真实 operation admission 决定是否可执行。

### Task 3 — Android Speech adapter

提交：`8719a17 feat: adapt Android speech clients to KMP bridge`

新增 `AndroidSpeechBridge(tts, asr, scope)`，实现以下边界：

- TTS 与 ASR 共用一个音频任务槽；query 不占该槽。
- Explicit 选择严格执行，不回退 Default。
- 缺少单项 client 只影响对应 capability。
- Android ASR 只声明 `MICROPHONE`；`AudioFile` 在进入 native client 前返回 `UNSUPPORTED_INPUT`。
- cancel 等 native `finally`/drain 完成后清槽并发布一次 `cancelled`；drain 期间重入返回 `BUSY`。
- native 忽略取消后产生的迟到 success 不会覆盖 `cancelled`。
- adapter 的 worker Job 与宿主 scope 的 Job 解耦，宿主提前取消不会跳过 drain 或 client close。
- close 立即拒绝 query/TTS/ASR admission，等待所有 query/audio worker，再关闭拥有的 clients；并发 close waiter 共享结果。
- callback 交付在注入 scope 的 dispatcher；未知异常只输出稳定的 `android.speech/1`，不包含文本、转写或文件路径。

集成测试使用真实 `SpeechSynthesisClient` / `SpeechRecognitionClient` 和可控 backend，覆盖计划列出的九个生命周期、选择、输入和隐私用例。

## 最新验证

Tasks 4–6 已完成并本地提交：

- `7605511` iOS adapter/input lease，Binding/Demo ABI 各14/14；lease 等 native drain 后才释放，关闭 join 所有任务。
- `233873e` 共享 SpeechViewModel/页面，取消等待 terminal、分 capability 查询、严格 selection、离页清结果，旧 iOS 四参数入口保留。
- `b272b76` 双端 host/权限/独立 iOS 工程；不修改原受保护项目，实际系统后端仅由 Demo host 引入。

本轮 Task 7 的最新回归：

```sh
rtk proxy sh scripts/verify-apple.sh
rtk proxy sh scripts/verify-android.sh
rtk proxy ./gradlew :kmp-binding:allTests :demo:shared:allTests --console=plain
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -v
```

以上通过：Swift378项、工具76项、Android验证569tasks；iOS adapter/Host20项、Android Host5项。fresh七个XCFramework和Face/Speech/Identity binary消费者、Speech Maven消费者构建/裁剪检查通过。含 up-to-date 命中，不声称全部重执行。日志在 ledger 同目录的 `task-7-*-final.log` 和 `task-7-apple-*.log`；完整边界见[语音 Demo 验证](validation/speech-demo-bridge.md)。

独立 reviewer 覆盖 `d6af05a..b272b76` 及 Task7代码，提出三项 Host 问题：iOS Host/VM引用环、Android权限等待中的重试误报拒绝、factory异常被当缺模块。均已锁定回归并修复，定向复审 CLEAR。受保护 pbxproj 不属于本轮 diff，未采纳恢复该文件的建议。

## 受保护的用户文件

以下改动属于用户，本包任务没有暂存或提交，最新 hash 复查仍一致：

- `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj`
  - 状态：modified
  - SHA-256：`b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`
- `docs/validation/face-identity.md`
  - 状态：untracked
  - SHA-256：`6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`

后续不得覆盖、暂存或提交这两个文件。Task 4–7 的生成工程、framework、APK、音频、模型和真机报告继续放入已忽略的 `artifacts/`。不要把超过 1 GB 的文件交给 Git 或 Git LFS；需要模型或大型资产时使用下载脚本。

## Task 7 最新真机证据与下一步

Task7 已 task-start，base `b272b76e8428eeb78340bc623960691cbf08b224`，尚未 task-done；文件 ASR 与最终全套 UI 已通过，追加独立审查未产生结果前保留最后门禁。

1. **iOS TTS 已通过完整真实页面闭环**：原多行输入打开键盘、输入、Done 收起、完成、活动取消、终态后重入、后台取消/前台不自动重启。证据：`task-7-ios-slow-drag.log`、`artifacts/verification/speech-demo-ui-20261020/tts-slow-drag.xcresult`，1/1 passed，73.656 秒；输入单测 `tts-rendered-stable-retry.xcresult` 也通过。
2. **焦点旧结论已纠正**：Compose 的离屏 AX 节点保留陈旧坐标，滚动惯性还会使截图后的位置继续变化；历史 `isHittable`/完整 AX frame 不足以证明实屏位置。当前 UI helper 用本地 Vision OCR 定位固定标签、等待 0.6 秒稳定和慢速边缘拖动。临时焦点/触摸计数及单行实验均已移除。唯一新增产品修改为多行 TTS 字段提供 `ImeAction.Done`/clearFocus；缺 Done 的 RED 为 `task-7-ios-done-red.log`。
3. **iOS 文件 ASR 完整闭环已通过**：真实系统 picker 导入、重开/取消保留输入、首次识别、活动取消、同一输入重入完成、后台取消/前台不重启，`task-7-ios-asr-file-ui-fast-cancel.log` / `asr-file-ui-fast-cancel.xcresult` 1/1 passed，40.535秒。最终完整5项UI套件 `task-7-ios-asr-file-ui-full-suite.log` / `asr-file-ui-full-suite.xcresult` **5/5 passed，0 skipped，141.919秒**；已用 xcresulttool summary核对。
4. 文件测试 opt-in 为 `TEST_RUNNER_ODAI_SPEECH_FILE_UI_TEST=1`。为真实 picker 暴露固定 fixture，只在忽略路径 `artifacts/verification/speech-ui-fixture-info.plist` 中覆盖 `UIFileSharingEnabled`/`LSSupportsOpeningDocumentsInPlace`；未修改生产 plist 或生成器分享设置。设备 Demo Documents 中唯一测试文件为 `odai-speech-ui-fixture.caf`，不选择私人文件。系统显示“我的iPhone”；Speech 授权弹窗归属 SpringBoard，不能只查询 App alerts。最终已恢复无 plist 覆盖的普通构建并安装：`task-7-ios-restore-normal-build.log` / `task-7-ios-restore-normal-install.log`；成包两项 sharing key 均不存在。Documents 的固定合成 fixture 保留用于复跑，未删除其他内容。
5. **Android TTS 已通过**：完成/取消/重入/后台取消与前台不启动，`task-7-android-ui-final.log`。ASR 仍 `BACKEND_UNAVAILABLE MISSING_ASSETS`，未采集自然语音。此前英语资源下载为单次显式授权，没有确认就绪，普通“继续”不能重用它安装资源。
6. Done 修改后的新鲜本地回归：shared allTests、Android unit/lint/APK、simulator framework 共468tasks成功；Python76/76；device framework重建成功。日志 `task-7-ui-shared-regression.log`、`task-7-ui-python-regression.log`、`task-7-done-framework.log`。先前全包本地分发/裁剪/Host审查证据继续有效，不无故重跑。
7. 独立 reviewer 曾对 Task1–6及Task7 Host修复给出定向 CLEAR；本次新 UI/Done 追加审查因模型容量不足，未产生新审查结论。作者已独立过读最终源码及断言，但不冒充独立 CLEAR。

## 下一会话最小恢复步骤

### Roadmap 后续设计已启动

在 Task 7 追加独立审查仍不可用期间，已完成下一包 [iOS 麦克风 ASR 设计](superpowers/specs/2026-10-20-ios-microphone-asr-design.md)。它保留当前文件 API，提出独立 microphone task、finish/cancel 分离、显式 session lease、权限与 drain 门禁，并记录 Apple DocC/SDK 依据。按用户“逐项实施、自动进入下一项”的 roadmap 约定推进本地契约测试与实现，不把这当成人工逐字批准或验收通过。新包的录音、真机与独立审查分别记录；旧 Task 7 的审查与双远端同步门禁仍保留。恢复时读新包计划/ledger，避免重做已完成切片。

### 15:09 恢复检查点

- 实现 HEAD 仍为 `476d027`；本轮未修改产品或 UI 测试源码，Task 7 未 task-done。
- 新鲜回归：生成器 `test_speech_demo_project.py` **4/4**；`./gradlew :demo:shared:allTests :demo:androidApp:testDebugUnitTest :demo:androidApp:lintDebug --console=plain` **BUILD SUCCESSFUL，427 tasks（5 executed、422 up-to-date）**。日志：ledger 同目录 `task-7-resume-gradle.log`。未重跑此前完整五项真机套件。
- native `code-reviewer`、`critic`、`architect`、默认继承模型和显式 `gpt-6.1-sol`、`gpt-6-astra`、`gpt-6-luna` 独立子代理均在执行前返回 `Selected model is at capacity`，没有审查发现或 CLEAR。替代的 `omx ask claude` 退出 1：`Not logged in`，未修改登录状态。失败原始记录在 `.omx/artifacts/claude-read-only-independent-code-review-repository-worktree-users--2026-10-20T06-58-08-790Z.md`。恢复后只需一次可用的独立 UI/Done 审查，不重复已通过的 Host 整包审查。
- 两个远端 `feat/sdk-foundation` 实时查询均仍为 `472ca9351eb69c09d863ef7bc267c280b068b846`，是当前本地分支的祖先。没有推送；最终审查门禁保留，既有串行同步授权继续有效。
- 两个受保护文件的 SHA-256 与本文件固定值一致，`git diff --check` 通过。后续不得将它们纳入提交。

1. 读本文件/计划/ledger，核对 foundation 工作树和受保护 hash；不重做 Tasks1–6，不丢未提交 Task7 文件。
2. 优先恢复追加独立 review，只检查最终 UI/Done delta；先前整包 reviewer 的三项 Host 修复已定向 CLEAR，无需重做实现。测试 helper 不使用 AX 点击回退：固定标签 OCR，普通控件等待画面稳定；活动取消只取即时画面，避免等待期间任务已完成。OCR把“任务”读作“仟务”，因此匹配唯一片段“取消语音”。
3. review提出问题才按RED→GREEN修复并复跑相关门禁；否则不无故重复5项真机套件。原生/分发/Android及shared/Python门禁见上述日志；最终targeted Gradle427tasks（5执行）与生成器4/4也通过。
4. 审查通过后补Task7完成记录并串行核对/同步双方远端分支；此前用户已请求同步，本轮尚未执行，不force push。P6后续入口为独立iOS麦克风ASR设计，Android缺资源/自然质量/最低系统继续保留。

Task 7 文件分组（以恢复时 `git status` / 提交 diff 为准）：

- Host 修复和回归：Android `MainActivity.kt`、`SpeechHost.kt`、`SpeechHostTest.kt`；iOS `SpeechHost.swift`、`probes/ios-speech-bridge/SpeechHostTests.swift`；共享 `SpeechScreen.kt` 的 Done 动作。
- UI 验证/工程：新增 `demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/SpeechDemoInstrumentation.kt`、`demo/iosApp/UITests/SpeechDemoUITests.swift`；修改 `scripts/generate-speech-demo.rb`、`tools/tests/test_speech_demo_project.py`。
- 文档：README、本 handoff、ROADMAP、`docs/validation/speech-backends.md`、新增 `docs/validation/speech-demo-bridge.md`。不要把受保护的 pbxproj/face-identity.md 混入本任务暂存列表。

固定合成英语音频在 `artifacts/P6IOSASR/fixture/asr-fixture.caf`；仅供后续实际文件选择/识别验证，不选择用户私人文件，不把自然录音、转写或 TTS 文本写入日志。此前原生探针成功不等于本 Demo 已验收。

## 仍需保留的 roadmap 缺口

这些项目不能因 Tasks 1–6 与本地分发回归通过而标为完成：

- iOS 15 真机、Android API 26 真机。
- Android ASR 真实转写；当前 TECNO KM9 / API 35 的 en-US 返回 `MISSING_ASSETS`。
- iOS 麦克风 ASR 的受控真机录音与最终验收（本地实现现已提交，见顶部检查点）。
- 两端自然语音质量与断网验证。
- P7 Android 视频超分与完整 SDK/Demo 接入。
- P9 Android 第二平台与质量基准。
- P10 生产人脸模型、PAD、授权数据、阈值校准与最终独立审查。
- P11 统一 benchmark 与发布结论。

可用现代 Android 真机：TECNO KM9，API 35，ADB serial `15365375C9007986`。iPhone 16 Pro Max / iOS 27 已用于现有 iOS 验证；连接状态需在新会话重新检查。

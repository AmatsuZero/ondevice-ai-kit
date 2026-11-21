# 单图 Demo picker 与 shared action 验证

更新：2026-10-22。前置 native host29458bb；设计/计划7eda196；Task1 shared actions2cdf094；Task2 Android433121c；Task3 iOS/检查点与本文同次本地提交，最新HEAD以git为准。规格/计划 `2026-10-22-multimodal-demo-picker`，ledger/final-review/全部成功失败日志保留在同名 `.superpowers/sdd/`。

## 交付与入口

MultimodalScreen消费既有VM，可选actions仅转发选择/清除/离页；新增专属 `multimodalHostMainViewController`，旧Face/Identity/Speech/Video入口标签保留。默认没有multimodal入口/模型后端，启用后仍真实UnavailableMultimodalBridge。

Android仅Intent extra `odai.multimodal.enabled=true`创建host。GetContent(image/*)返回content URI后在IO dispatcher查询显示名并复制到新鲜cache staging；32MiB上限、空输入/读取错误/取消清理、路径样式显示名净化。Main线程回调到host/store，立即清理staging；bridge只收到opaque token。异步失败与成功共享latest/once-only ticket。onStart/onStop/onDestroy接foreground/background/close；外部picker/import在途可保留ticket，但离页/clear/close使晚到结果失效；foreground不自动生成。没有新增storage权限或Android推理后端。

iOS只扩展独立生成的Speech+Video Demo。`ODAI_MULTIMODAL_DEMO=1`要求 `ODAI_VIDEO_DEMO=1`，加入base ODAIKitMultimodal和host/store/picker/adapter源码；不链接MLX产品。MainActor UIDocumentPicker只允许PNG/JPEG单文件，nil/取消/重复/旧delegate/close均once-only处理；URL经既有security-scoped store复制，返回bridge opaque token。DemoController持有picker/host，转发后台/foreground和释放；旧工程不变。

生成示例（输出目录必须不存在，使用已有Ruby3.4与已构建双ABI framework）：

```sh
rtk proxy env ODAI_VIDEO_DEMO=1 ODAI_MULTIMODAL_DEMO=1 ODAI_SPEECH_DEMO_DIR=artifacts/verification/multimodal-picker-app-new /Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby scripts/generate-speech-demo.rb
```

## 实际验证

| 门禁 | 结果与证据 |
| --- | --- |
| Task1 RED→GREEN | 缺actions API真实编译RED；shared simulator60/60和双framework link成功（task-1-green-build.log）；五个实际Swift入口typecheck成功（task-1-entry-typecheck.log） |
| Task1 Android API35 | TECNO KM9 actual Compose点击选择/清除/离页过滤通过，`inputActions=passed-real-host-select-clear-leave-filter`；默认unavailable与旧Speech/Video导航通过，task-1-device.log |
| Task2 RED→GREEN | 缺copy helper/task-2-red.log；异步failure API/task-2-async-failure-red.log。最终新copy4/4、host8/8、全Demo26/26新执行，XML timestamp2026-10-22T13:37:59Z，task-2-final-gates.log |
| Task2 APK/lint | 离线成功；0error/9既有warning，539tasks6executed533up-to-date；缓存任务不称新执行 |
| Task2 API35实际Activity | task-2-java-fixture-device.log最终status=passed：optOut隐藏、optIn真实unavailable、systemPicker打开/取消且不读取媒体、后台不自动生成、owned目录清理；content://合成回调经过真实query/IO-copy/host/clear。实际系统文件选择unverified，推理unverified-no-android-backend |
| Task3 generator RED→GREEN | 新multimodal前置条件未校验RED（task-3-generator-red-fixed-ruby.log）；真实生成工程检查source/product/flag/default/双ABI路径/保护原工程。全工具102/102新执行（task-3-tools.log） |
| Task3 native RED→GREEN | 真实Xcode test缺MultimodalFilePicker符号RED（task-3-picker-xcode-red-exact-destination.log）；实现并格式化后Demo24/24新执行（3 picker+7 host+4 store+10 bridge），task-3-picker-final.log及xcresult |
| Task3 完整App | 新生成 `artifacts/verification/multimodal-picker-app-20261022/SpeechDemo.xcodeproj`，unsigned generic iOS simulator完整App及旧host/UI target编译成功，task-3-full-app-build.log；实际新入口/旧入口标签同时参与编译 |
| Swift格式 | 新picker与delegate测试strict swift-format lint exit0，task-3-format-final.log |

关键命令：`rtk proxy python3 scripts/gradle-local.py :demo:androidApp:testDebugUnitTest --rerun :demo:androidApp:assembleDebug :demo:androidApp:lintDebug -PodaiTestRunner=io.ondeviceai.demo.MultimodalPickerInstrumentation --offline --console=plain`；`rtk proxy env RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby python3 -m unittest discover -s tools/tests`；Xcode Demo测试使用已连接simulator id DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F，`-disableAutomaticPackageResolution -skipPackageUpdates CODE_SIGNING_ALLOWED=NO test`。全部构建使用已有缓存，无下载。

失败证据未删除：Android filter-monitor/中断运行没有终态通过；callback monitor能拦截但file:// fixture不满足真实content query；测试provider独立进程缺Kotlin runtime导致NoClassDefFoundError，改用仅返回固定公开字节的Java test-only provider后设备通过。provider只在test APK，无设备文件访问。iOS系统Ruby2.6/3.4 gem混用、手工XCTest导入与未指定OS的同名simulator选择失败均保留；固定已有Ruby3.4和具体destination后取得真实RED/GREEN。

## 边界、审查与下一包

Android合成provider只有三字节，不能称有效PNG解码、自然图片质量、实际用户文件选择或真实推理成功。iOS delegate XCTest/编译不等于触摸UI或系统picker往返；实际iOS UI仍unverified。Binding10/10、shared60/60等只引用各Task已有证据，不冒称本Task新执行。iOS15/API26最低设备、真实KMP→MLXVLM、自然质量、Metal/GPU/energy和Android生产backend仍未验；process RSS不等于GPU峰值。既有Video自动化初始化超时、P6实录、Android ASR MISSING_ASSETS等缺口保留。

Task范围及最终均为作者自审；按用户单人执行要求不派子代理，独立review pending，无独立APPROVE。Rulings：iOS只接独立生成Demo（原保护工程保持原状）；Android异步失败走同一ticket（避免旧错误污染新状态）；synthetic content provider只证明fixture链路（真实系统选择继续unverified）。无新增deferred Minor。

下一可独立包：Apple既有固定模型/ModelStore的可选host接线，默认unavailable保留，Android继续unavailable；随后独立执行KMP页面到真实SDK的成功/首update取消/drain/同进程重入/后台不重启/close与lease释放/关闭后拒绝设备闭环。短视频、Android新后端、自然质量及资源测量另包。Roadmap仍5/12完成、7阶段未收口，P9 active。

两保护文件仍原tracked dirty/untracked，未修改/暂存；SHA256分别b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5和6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822。仅本地提交，无push/merge/发布/下载/录音/私人媒体/签名资产变更，artifacts/.superpowers/日志保留。

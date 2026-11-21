# Optional fixed Apple MLXVLM Demo host — local validation

2026-10-22；base1ece99d，design30e691b，Task1 5afb045，Task2与本记录同次本地提交。当前包为可选host接线，不是设备端到端验收。独立spec/plan `2026-10-22-multimodal-demo-mlx-host`；证据目录 `.superpowers/sdd/2026-10-22-multimodal-demo-mlx-host/`，全部日志/生成工程保留。

## Behavior

默认iOS15/macOS12/API26图不变，Android和默认Demo仍unavailable。仅独立生成增强Demo需要三flag：`ODAI_VIDEO_DEMO=1 ODAI_MULTIMODAL_DEMO=1 ODAI_MULTIMODAL_MLX_DEMO=1`，target iOS18，bundle `io.ondeviceai.multimodalmlxdemo`，额外链接本地apple-mlx/ODAIKitAppleMLXVLM和ModelStore。运行还须显式 `--multimodal-mlx-local-model`。

固定来源为增强App的Documents/model；canonical14文件manifest，model revision fa57db46815177fbdfd65cc85a2b3416a8332268、MLXVLM revision6e338e5df49424fc0db7c6d58184d622f92ed18a。ModelStore验证安装到Documents/multimodal-model-store，acquire→MLXVLMBackend→native client→现有IOSMultimodalBridge/host/VM；不另写生成协调器。取消/失败释放当前资源；已验证安装可保留，host.close释放lease，不递归删除model目录。准备owner不被task捕获，销毁取消/晚到host关闭；准备失败保留unavailable，inactive完成立即background；foreground不重启准备或生成。shared控件在准备结束后才创建。

## Fresh evidence

- Task1实际生成XCTest缺factory符号RED→新factory6/6及Demo30/30；真实tiny ModelStore验证lease保留到close、builder throw、无效registration、安装前/取得lease后取消、完整性失败。日志task-1-red/green/generator。
- Task2生成器先2项失败RED→Speech8+bridge3=11/11；准备owner实际XCTest缺符号RED→完整Demo33/33（新增3准备owner测试）。分别见task-2-generator-red/green、task-2-preparation-red/green及xcresult。
- 完整tools104/104（task-2-tools.log），严格新Swift格式检查0（task-2-format.log）。没有KMP修改，既有导出标签不变；默认App及增强App实际编译也覆盖旧Swift调用入口。
- 默认非MLX独立生成App unsigned generic iOS Simulator构建成功（task-2-base-app-build.log）。增强独立工程 `artifacts/verification/multimodal-mlx-demo-20261022/SpeechDemo.xcodeproj` unsigned generic physical iOS构建成功（task-2-enhanced-build.log），使用既有SourcePackages，无自动resolution/update/download。
- 增强工程固定lock与apple-mlx/Package.resolved SHA256一致：e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858。构建参数 `-disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile CODE_SIGNING_ALLOWED=NO`。

现有XCTest17/ICU18.5链接、VideoHost actor、旧Speech sendability与MLX C++ extension等warning保留，不称warning-free或最低系统设备通过。仅作者范围/最终自审，无独立APPROVE。

## Unverified / next independent package

本包未安装运行增强App、未复制设备资产；真实KMP页面→MLXVLM成功/首update取消/drain/同进程重入/后台不自动重启/close/lease释放/关闭后拒绝须独立设备包。iOS触摸UI/system picker选真实文件仍unverified。固定SDK benchmark是旧证据，不能代替此闭环；自然质量/Metal GPU峰值/energy/最低系统/Android真backend/增强分发与独立review仍未验。旧Video UI初始化超时、P6实录与Android ASR MISSING_ASSETS保留。roadmap仍5/12完成、7未收口，P9 active。

只本地提交；保护project.pbxproj与face-identity.md的固定hash/state保留且未暂存，无push/merge/发布/下载/录音/私人媒体/签名资产变更。

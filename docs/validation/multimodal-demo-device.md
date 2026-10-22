# KMP Multimodal host/VM real-device lifecycle validation

2026-10-22；接续可选MLX host ab40fec。独立design671f2d0，严格报告门禁2e8eb1d，consumer a24866d，provenance修复2868bee；本记录随本地检查点提交。Spec/plan `2026-10-22-multimodal-demo-device`；ledger/final-review及全部日志 `.superpowers/sdd/2026-10-22-multimodal-demo-device/`。

## Scope and device evidence

独立validation bundle `io.ondeviceai.multimodallifecycle`，实际编译既有KMP ODAIKitDemo、MultimodalHost/InputStore/IOSMultimodalBridge，真实ModelStore→ModelLease→MLXVLMBackend→client→forwarding observer→host/VM。observer只转发真实callback并在首交付update后调用真实VM/host；没有合成成功路径或新生成协调器。默认Demo/Android仍unavailable，产品/KMP导出未改。该验证不是增强Demo的UI验收。

设备iPhone16ProMax/iOS27.0.1，UDID00008140-0008703436D3001C；源码snapshot `2868bee341cad6d9a58527af793c621451052fd9`，built signed Info.plist精确匹配；runId `5514FD5C-44A9-4E1F-8CEB-EE154A50406D`，同进程PID20852，physical标记。固定backend mlx.smolvlm2-500m、model fa57db46815177fbdfd65cc85a2b3416a8332268、runtime6e338e5df49424fc0db7c6d58184d622f92ed18a。14文件bytes/SHA及fixture60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606先复核，只复制这些公开/合成资产到本bundle Documents。

原始证据 `artifacts/verification/multimodal-device-provenance-20261022/device-result.json`，SHA256 `52c7b2f32a31ff8f697af85fb630acef19cd80fa4ec69454fdf0f0a8a2d3c0ac`；summary.json、install/launch.json及生成工程保留同目录。报告严格校验通过，10个门禁全部true，5个真实请求场景：

- availability实际available；固定单图成功，实际累计update和非空output，backend/model/runtime精确一致。
- 首个交付update后同步VM.cancel，观察CANCELLING→CANCELLED；退役选择时实际input仍由operation lease保留，释放后才交付terminal，无output。
- 取消终态/drain后同PID立即新请求成功，非自动重试。
- 首update调用host.background取消，host.onForeground后300ms有界观察generation counter不增加；不是实际OS切后台/挂起验收。
- 首update活动host.close，保留input直到释放，等待native drain/close完成；再次close不重复bridge close，私有input目录消失。ModelStore.remove在model lease活动时inUse，host.close后成功删除此suite自己安装的模型，原Documents/model来源保留。VM及原生bridge关闭后生成均typed CLOSED拒绝。

trace逐场景包含request→递增update→cancel/background/close（适用时）→inputReleased→terminal，取消场景无output；真实client.drain每轮显式等待。没有延迟/内存/质量数值结论。

## Fresh tests and regression

严格report缺module RED→5/5，含缺失/false/bool混淆、身份/source/hash/platform错、事件删除/乱序、sequence重复与取消伪成功mutation。独立generator缺module RED→3/3；实际App缺suite符号编译RED→unsigned/signed iOS GREEN。完整tools112/112，strict新Swift0。未重跑不相关Android/基础SDK测试，不提升旧证据等级。

实际产物检查暴露GENERATE_INFOPLIST_FILE忽略自定义INFOPLIST_KEY_ODAISourceCommit：旧build虽成功却不能绑定源码。未启动旧产物，保留初次install日志；显式XcodeGen info properties修复，生成plist测试RED→GREEN、fulltools112/112，再生成/签名/检查实际App plist→替换安装→运行。原失败/中间日志都保留，不作为通过证据。

最终签名编译使用已有team4X996UV8LC/profile与固定SourcePackages：`-disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile`，无allowProvisioningUpdates/下载/签名资产变更。生成lock与apple-mlx/Package.resolved一致（SHA256e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858）。SDK/MLX既有compiler/link warnings仍存在，不能称warning-free/最低系统通过。

重校验：

```sh
rtk proxy python3 tools/multimodal_lifecycle_report.py artifacts/verification/multimodal-device-provenance-20261022/device-result.json --source-commit 2868bee341cad6d9a58527af793c621451052fd9
```

## Remaining independent gates

真实共享Compose页面渲染/导航/按钮→同一真实host，以及增强Demo async准备/真实OS前后台与system picker文件选择仍unverified。本包采用独立程序化consumer，不把它称为KMP页面端到端或iOS UI通过；下一独立包优先补共享页面/增强Demo集成验收，必要时用有界App内测试驱动观察页面，避免反复重试已知Video UI runner初始化外部门槛。

自然质量/Metal GPU/energy/minimumOS/Android生产backend/短视频/增强分发与独立review仍未验。旧Video自动化初始化超时、P6实录与Android ASR MISSING_ASSETS保留；仍5/12阶段完成、7阶段未收口、P9 active。每Task及最终仅作者自审，独立review pending，无APPROVE。两保护文件hash/state不变且未暂存，日志/artifacts/.superpowers保留；仅本地提交，无push/merge/发布/下载/录音/私人媒体读取。

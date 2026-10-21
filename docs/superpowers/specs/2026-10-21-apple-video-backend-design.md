# Apple VideoToolbox 视频后端首包

基线 `bea82f2`，2026-10-21。按用户持续推进 roadmap 的授权，使用 brainstorming/writing-plans 明确下一包；不声称用户逐字审阅了新文档，也不重复要求本地可逆实施许可。Video Foundation 的独立审查仍因服务容量失败 pending。

目标是在真实 VideoProcessingClient 后注册一个可选 `apple.videotoolbox.sr` 后端，完成有限输入的实际处理、取消排空和文件 lease；不是一般视频编辑器或质量合格声明。选择独立 `ODAIKitAppleVideo`，只依赖 Core/Video 与 Apple 系统框架。把 AVFoundation 塞进契约模块会污染所有消费者；直接复用探针会携带硬编码报告、整文件hash、baseline与吞清理错误，均不采用。基础 iOS15/macOS12 保持；真实处理仅非模拟器 iOS26+，其他平台返回明确 availability。

## 官方边界

已读取 [Apple 配置文档](https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration) 的官方 JSON，以及本机 Xcode iPhoneOS.sdk 的 `VideoToolbox.framework/Headers/VTFrameProcessor{,_SuperResolutionScaler,Configuration}.h`。配置类在 iOS26+且排除模拟器；supported scale、device与configurationModelStatus必须运行时查询。调用startSession前必须ready；下载是显式独立API，本包不调用。source/destination attributes必须通过CVPixelBufferCreateResolvedAttributesDictionary合并。输入/输出及附件在process回调完成前不能修改；全部帧之后endSession以结束pending处理。首帧random，后续按PTS无跳帧sequential。官方头文件仍未确认模型传递函数，不能将RGBAHalf当成线性要求。

## 明确的首版输入与输出

首包沿用 probe-sRGB-v1 **实验色彩约定**：单视频轨、identity transform、320×180、24fps、PTS从0开始连续、1至48帧、SDR BT.709 primaries/transfer/matrix；本地可读regular文件≤32MiB。解码逐帧复查尺寸、色彩与时间戳；超帧数直接拒绝并清理。非该profile的旋转、HDR、可变帧率、额外视频轨或非本地文件明确报unsupported input，不静默裁切、补帧或转换HDR。profile限制是本次SDK首包范围，不是Apple系统能力上限。

首版只支持4×和显式 `VideoAudioPolicy.discard`，静态supportedAudioPolicies为[discard]；默认preserve由client明确拒绝，不会默默丢音轨。音轨preserve另包完成。输出唯一私有临时目录内的HEVC/MOV，1280×720、24fps、BT.709、8Mbps请求、无帧重排；所有帧成功编码并finishWriting后返回lease，metadata时长为实际帧数/24。不覆盖输入或用户已有输出。禁用整文件hash、probe报告、baseline和模型下载。

## 资源与职责

`AppleVideoOutputWorkspace` 只拥有自己创建的UUID子目录，内部固定output.mov；`AppleVideoOutputLease` 持有目录并幂等release，目录缺失视为已释放，其他移除错误抛出且可重试。backend.close不删已交付lease。

`AppleVideoToolboxBackend` actor提供标准VideoSuperResolutionBackend。直接调用也只有一个槽；query不创建临时输出，拒绝busy/closed/不支持选项。worker拥有workspace和处理引擎；成功仅转交lease，失败/取消先清理，清理失败不得吞。close立即拒绝新操作，取消并等待worker及外层清理，多个close join。内部 `AppleVideoProcessingEngine` 是系统资源边界，生产实现使用VT/AVFoundation，测试替代这部分外部硬件操作以验证真实backend/client与文件清理。

VT处理按帧await，取消只在安全点终止，不能提前释放尚在处理的缓冲区。reader/writer/processor在process返回前结束；失败路径cancelReading/cancelWriting/endSession并清理workspace。编码ready等待检查writer失败、取消和5秒deadline。API返回稳定类型错误，不把自然文件路径或第三方异常字符串写日志。

## 验证与后续

先测试workspace唯一性、并发幂等release、取消/失败无残留与已交付输出跨close，再验证真实backend→client选择/availability/close/drain。profile数值与时间戳拒绝用例独立测试。最后SDK设备/模拟器编译和可选链接检查；若已有设备/签名/系统模型ready，则仅使用固定公开fixture做成功、取消、同进程重入和ffprobe复核，不触发下载或使用私人媒体。新模块进入九XCFramework，Core+Video消费者继续不引入AppleVideo。真实GPU/最低系统/主观质量/音轨preserve/KMP/Demo/Android不在本包完成声明内。独立审查不可用如实pending，禁止push。

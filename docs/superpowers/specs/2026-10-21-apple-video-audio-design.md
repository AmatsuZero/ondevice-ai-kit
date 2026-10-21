# AppleVideo bounded audio preservation

基线563e1b6。延续用户roadmap自主推进授权，使用brainstorming/writing-plans拆分本地实施；不声称用户逐字审阅本规格。首版后端固定视频profile不变，补齐请求默认preserve，discard仍显式丢弃。基础iOS15/macOS12及实际SR仅非模拟器iOS26+保持。

## 范围与取舍

preserve支持无音轨或一条AAC-LC（kAudioFormatMPEG4AAC），单/双声道、44.1/48kHz、单format description；音轨timeRange必须numeric、start≥0、duration>0、end≤视频duration，保留全部音频range与原start，不裁剪、拉伸、混音或重编码。非兼容输入在启动SR前拒绝，最终按实际输出帧数/24再次检查音频边界。无音轨preserve直接把内部silent文件移到output。discard继续忽略音轨。保护用户输入/已有输出，只有调用内创建的workspace文件可清理。

选择先SR输出video-only.mov，再AVMutableComposition+AVAssetExportPresetPassthrough封装为output.mov：避免在同一writer串行写两种media导致backpressure，保持已编码视频与原AAC。把音频解码重编码会改变音质/容量；同writer并行lane增加当前小profile的协调成本，均不采用。composition只插入实际SR视频range至0，音频完整timeRange插到其原start；导出兼容性检查与MOV supported file type必须通过，导出后校验视频/音轨数量与音轨format，缺轨不能当成功。压缩payload与时间线在固定fixture上复核，不宣称容器metadata字节等同。

## 生命周期

内部AppleVideoAudioMuxer处理admission/封装，AVFoundation不进入Core/Video。实际export(to:as:)使用Apple SDK自带Task cancellation handler，等待export完成回调后检查status；外层取消不会提前释放composition/源文件。完成后再次Task.checkCancellation；失败移除本次创建的output，保留processed/source；后端仍负责完整UUID目录清理并等待drain/close。导出成功且video-only删除成功才交付lease，音轨count准确。无新依赖、下载、录音、私人媒体、签名或远端变更。

官方依据：本机iPhoneOS SDK AVAssetExportSession.h（passthrough与completion始终终态回调）、AVCompositionTrack.h（insertTimeRange按源range自然速率，atTime为目标呈现时间）及usr/lib/swift/AVFoundation.swiftmodule/arm64e-apple-ios.swiftinterface的export(to:as:)实现（withTaskCancellationHandler→await export→status，onCancel调用cancelExport）。研究子代理capacity失败无结论，以上为主代理实际读取。

## 验证与未包含项

使用ffmpeg本地生成无私密内容的固定2秒色块/440HzAAC fixture，真实AVFoundation host测试检查无音轨、AAC数量/格式/起始位置/时长、拒绝多轨/不兼容/超长/负或非法range、保护已有输出、预取消及压缩音频payload。backend/client真实默认preserve路径测试，以及已存在的取消/close/晚到失败回归。设备公开视频加合成tone固定fixture验证SR→mux、48帧与AAC payload/PTS、取消/重入/lease清理；实际export中途取消若无法可靠触发需明确未验，不以预取消代替。fresh九XCFramework、Core+Video隔离消费者与Apple/tools门禁。独立审查失败保留pending，不push。通用音频、HDR/VFR、质量/能耗、KMP/Demo/Android/最低系统独立后续。

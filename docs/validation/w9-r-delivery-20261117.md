# W9-R：交付材料、独立消费与Apple二进制接线（2026-11-17）

后续增量：旧R的Android Maven/AAR为修复前快照，不能作为当前安装候选。模拟器发现硬链接发布被App策略拒绝后已修复，并在新的隔离目录重新暂存AAR（SHA-256 `75b8f2829de7ef448ad8189280326c710ea66f458a5c4b11cd95a7c17e0f8138`），独立APK构建及AAR/JVM三预设消费通过。详见[模拟器验收](w9-simulator-acceptance-20261117.md)与其本地 `android-final-artifact-*-result.json`；本页旧工件不覆盖，Apple R证据保持。最终组合/通知、真机/资源/听感与独立复审仍open。

当前 **R材料与有界本地消费已完成 / release not-ready**。本轮从已提交 C 的 `abc67bb1df88baf88090ee140325e6585f8b59ce` 继续，未重复提交C。原 [C报告](w9-c-integration-20261117.md) 的设备/资源/独立复审缺口保持。新增 [候选清单](../release/2026-11-weekly-candidate-checklist.md)、[已知限制](../release/2026-11-weekly-known-limitations.md)、[设备验收步骤](../release/2026-11-w9-device-acceptance.md)。

## 本轮变更

实际检查发现 SwiftPM 已包含 W9，但 `scripts/generate-distribution-project.rb` 和 `scripts/package-apple.sh` 的旧产品列表仍只有10项。采用本地 Ollama 限定候选小替换：新增 `ODAIKitVoiceEffects`、`ODAIKitAppleVoiceEffects`；Apple后端仅依赖 VoiceEffects，并显式链接系统 AVFoundation。更新原 target 数断言并补真实生成工程/源码集合/依赖/框架/scheme回归。没有新增第三方依赖、模型、版本、lock或SDK算法修改。普通PBX不写；归档在完整源码的隔离副本执行。README已明确先重新生成SDK分发工程再打包，已有dirty内容时使用新源码副本，不能直接对旧10-target工程打包。

本地助手包装候选首稿错误加入Core依赖、遗漏AVFoundation并生成无效测试；拒收保留，限定订正版集成后由主代理加强测试的精确源集合检查。另两份消费示例候选先后存在WAV/路径/async/清理缺陷，均拒收且没有进入源码。主代理仅编写一次性验收driver调用已有公开API，不将其作为已交付消费示例，也不将助手自报作为验收。没有第三次示例生成、新云端/付费/native请求或已知429重试。

## 新鲜验证与证据

本地原始工件：`.omx/artifacts/w9-r-delivery-20261117T121322Z/`，未纳入Git。所有输入均为本轮合成1秒220Hz、16k单声道PCM16的32044B canonical WAV；没有麦克风或私人媒体。

| 项 | 实际结果 | 边界/原始收据 |
| --- | --- | --- |
| Apple SwiftPM独立源码消费者 | 隔离包编译并真实执行三预设；原声复制、输入保持、ID独立、close后输出保留、释放后目录空 | macOS本机，不是iOS真机；`apple-source-consumer-result.json`/log |
| Android隔离Maven暂存 | sdk-core和voice-effects共12个产物，固定既有0.1.0-SNAPSHOT；voice AAR65534B/SHA22d4cc94… | `-Dmaven.repo.local`仅指向本轮目录，不写用户Maven缓存、不远端发布；`maven-stage-result.json`、`maven-manifest.json` |
| Android独立Maven→APK | 独立settings仅以Maven坐标依赖SDK，离线34任务全部执行，APK1449248B，SHAca2facd17156c8ed6b026e3d351c5ccad8a8c51b68614d136ba3db6039d749a2 | 无SDK project替换；4DEX；Gradle默认Debug签名，未作分发签名/设备安装；`android-independent-apk.json`和dependencies日志 |
| Android AAR公开API运行 | 仅以暂存AAR的classes.jar编译/执行同样三预设与所有权检查，输入保持、输出目录空 | JVM17运行，不是ART/OEM；`android-artifact-consumer-run-result.json`/log |
| 分发回归 RED→GREEN | 旧脚本10!=12真实RED；最终生成器26测试通过，包含19项Speech Demo既有回归 | `packaging-red.log`、`packaging-green-r2.log`；另5项Multimodal分发邻接回归通过；精确来源/依赖/AVFoundation与重复生成保持 |
| Apple完整分发归档 | 隔离iPhoneOS与iOS模拟器Release归档，全部12个XCFramework生成成功，含W9双arm64切片 | `distribution-archives-result.json`、archives日志、`voice-xcframework-slices.json`、全部文件hash清单；未签名/上传，归档仅保留既有AppIntents元数据提取跳过警告 |
| W9独立二进制消费 | iPhoneOS15与iOS15模拟器目标均编译/链接，直接使用两个新XCFramework，无SwiftPM SDK源码编译 | `apple-binary-consumer-r2-result.json`及simulator收据；device Mach-O platform2/minOS15，LOAD_DYLIB为实际两framework；不是设备安装/运行 |

消费driver的三预设检查不是新增频率/听感验证；原声字节复制与所有权是真实检查，±4半音数值验收仍引用A/B已有范围，不冒称本轮重新做听验。

## 当前包与通知差异

本轮盘点C产物：Android Debug APK62153717B/SHAfaaec200…、Release unsigned APK48494911B/SHA3e8e7284…，均有5个名称命中LICENSE的成员及16个MLKit/AndroidX相关SO（四ABI各4）。W9 AAR没有名称命中license/notice/copyright的成员，也没有SO；不能由此得出无通知义务。iOS本次voice opt-in Speech App仅5文件，没有名称命中通知的成员；不是旧完整MLX/OpenCV通知App。完整成员与hash保存在 `candidate-zip-inventory.json`、`ios-app-inventory.json`。

W9没有新增模型/第三方库，但实际Android消费仍解析既有Kotlin2.4.0与coroutines1.10.2、sdk-core；完整Demo还包含Compose/Skiko、MLKit、CameraX等。Apple W9后端用系统AVFoundation，voice opt-in Demo另含KMP/Compose及既有Speech/Face组件。历史Sherpa/KMP/MLX/OpenCV通知有各自固定产物；本轮未将其全部重打入当前App，适用性/完整最终组合通知仍open。以上只是成员盘点与来源差异，不是法律意见或独立许可批准。

## 首错和环境边界

- 首次GREEN在源第47行显示12却报12!=10；保留失败。已核timestamp型pyc的header mtime/size与新源相同，缓存bytecode的LOAD_SMALL_INT仍为10，新源编译为12。使用本轮独立 `PYTHONPYCACHEPREFIX` 后26测试通过；无删除其他缓存或改全局配置。
- 首次二进制消费的外层xcrun未指定SDK，clang警告macOS sysroot但目标iOS；保留该输出。改为外层 `xcrun --sdk iphoneos swiftc` 且同样显式 `-sdk` 后无该警告，检查Mach-O的iOS15与framework加载项，不靠exit0推定平台正确。模拟器对应使用iphonesimulator。
- 一次只读ADB清单为空，未重置server/尝试USB恢复/安装/开麦。iOS物理设备没有执行；不借历史配对或旧单次权限自动签名安装。

## 剩余与下一步

R的交付文档、Apple二进制接线/归档及双端有界独立消费已完成；可复制、持续维护的独立消费示例尚未交付，临时driver保留本地证据。仍需固定最终功能/可选flag组合、补齐实际包通知、真机文件/录音/试听/导出回读/异常恢复与人声听感、同候选资源及预算裁定、最低系统设备和独立最终复审。当前没有最终APPROVE，没有推送、发布、上传或tag。W3/W6继续暂停；11/20候选、11/22验收是目标而非自动通过。

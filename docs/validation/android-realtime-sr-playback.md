# Android 播放时超分：覆盖与验证记录

2026-11-20。首轮用与 iOS 相同的 CuNNy 1×8 BOX 做 2× 增强。在 Android API 36 模拟器上，独立 Demo 已能边解码 H.264/AAC 边做 GLES 增强；原画达到片长附近，强制 CuNNy 在软件 GPU 上明显跟不上。另以独立参考验证了整帧 1080p → 4K 亮度。真机覆盖、动态盲评和持续实时性分别验收。

| 环境或方案 | 已验证 | 尚无证据 |
|---|---|---|
| API 36，Android Emulator / ANGLE SwiftShader，GLES 3.1 | 有声 30 秒片 A/B/B/A 四场完整解码；原画和 CuNNy 均有 EGL 提交。离屏整帧和四个时刻的有参考重建 | 手机 GPU 性能、真实显示面上的颜色、逐帧上屏及长期表现 |
| API 37，Android 17 / Google Play 16 KB 系统映像，SwiftShader GLES 3.1 | 同一个普通 APK 的自动模式解码 900 帧，900 次原画提交，首末帧间隔 30.28 s；Google 验证包探测不支持 | 手机 GPU 性能及显示延迟；不能把软件模拟器结果推广到 Android 17 手机 |
| API 35，TECNO KM9 / MT6835 / Mali-G57 MC2 | 现场曾读到 GLES 3.2、1080×2400、有 GMS。已有独立的 LiteRT 离线结果 | 本次播放测试时未连接；CuNNy 和 Google Upscale 均未跑 |
| API 26/27、其他 Adreno/Mali 手机 | API 26 是实验模块的 minSdk | 真机兼容性、GLES 3.1 能力、增强可用率与观感 |
| LiteRT 1.4.2 / ESRGAN 4× | [既有 Android 文件导出](android-video-sr-demo.md)；实机同编码条件下，[飞鸟](f11-android-sr-controlled-20261106.md)和[瀑布](f11-android-sr-waterfall-20261106.md)的平均 PSNR/SSIM 均低于双线性 | 不据此推断同场景 CuNNy 2× 或实时播放 |
| Google Play services Media Enhancement | 单独的 API 30+ 实验包在无 GMS 的 API 36 返回 MISSING_SERVICES；在有 GMS 的 API 37 返回 UNSUPPORTED。默认包不含 Media Enhancement SDK | 是否支持这条片源、模型是否可用、实际倍率与耗时 |

## 30 秒的视频实际怎样播放

固定片 1920×1080、30 fps、BT.709 SDR、有声 H.264/AAC，900 帧。两种模式使用相同解码、音频时钟、窗口和显示大小（模拟屏幕 1080×2340）。CuNNy 的 4K 是内部亮度计算尺寸。下表为原画／强制增强／强制增强／原画四个独立运行，均解码序号 0–899，音频末位置 30.009–30.016 秒。[逐场记录、APK/片源摘要和计时证据](android-realtime-sr-playback-emulator-20261120.json)。

| 模式 | 首末帧进入渲染的时间差 | 提交增强 | 提交原画 | 无成功提交记录 |
|---|---:|---:|---:|---:|
| 原画 A1 | 30.00 s | 0 | 900 | 0 |
| 强制 CuNNy B1 | 116.66 s | 67 | 827 | 6 |
| 强制 CuNNy B2 | 121.11 s | 61 | 831 | 8 |
| 原画 A2 | 29.92 s | 0 | 900 | 0 |

强制增强两场都无法持续按原速播放。原画回退是同一源帧，不能把回退算进按时增强率。增强作业分别启动且完成 790、796 次，但没有有效 GPU 耗时样本；四场均没有有效实际呈现时间，不能把 EGL 提交数当作上屏数、把未确认上屏当成物理丢帧，或报告显示延迟 P95。模拟器报 Vulkan 是 SwiftShader/ANGLE 的内部实现，应用后端始终调用 GLES。这组结果没有验证更换应用后端为 Vulkan 能改善性能。短测没有通过，不启动 30 分钟性能长测。

**自动模式。** 连续三次尝试仍未取得增强输出时，暂用原画 4 秒；再次失败时退避增至 8 秒、最多 16 秒。这个判断只用于调度。独立自动模式场次启动了 14 个增强作业，900 帧有成功提交：1 帧增强、17 帧尝试后回退、882 帧未请求增强；首末帧进入渲染的时间差为 30.27 秒。该机上的收益是避免强制超分拖慢整片，不能称为持续按时增强。

**暂停同帧。** 实际点击原画→CuNNy→原画，三次使用源帧序号 64、PTS 2.133 秒，播放位置保持 00:02。用 PixelCopy 读取整个 1080×1828 视频 Surface，再由主机逐像素比较：两次原画 RGBA 字节完全相同，CuNNy 与原画有 46,060 个 RGB 像素不同。恢复后时钟继续前进；拖动到 00:15 后，新代次的首个验收解码 PTS 为 15.000 秒，随后播放到 00:17。应用退到后台再恢复（同一 Activity 的 stop/resume）后，播放位置保持在 17 秒，并继续到 19 秒；新代次解码 PTS 为 17.733 秒。[实际播放器同帧切换页面（本地证据）](../../artifacts/verification/android-realtime-sr-20261120/playback/paused-final/compare.html)。这项验证包含窗口的彩色合成，既不是物理屏测量，也不是动态主观盲评。

在 API 37 的测试中，退后台时上一代窗口最初仍有两次失败的 EGL 提交。播放器改为在 Activity 暂停和 Surface 销毁时先停交新帧，再释放窗口。复测保留原有严格判定：没有失败的提交，暂停对照的两次原画完全相同；切到 CuNNy 的 45,782 个像素有变化。拖动后可续播，实际退后台和恢复后，解码在新的代次继续。修正后的同一 APK 也播放了完整固定片：自动模式 30.33 秒，0 帧增强／900 帧原画提交；强制模式 136.24 秒，36 帧增强／859 帧原画提交。两种模式都没有有效呈现时间。API 36 的完整四场与 API 37 窗口修正后的实测 APK 摘要分别保存在 JSON，未把前一版的成绩记到后一版。

## 手机上的官方 API 能做什么

Google Play services 的 [Media Enhancement 接入文档](https://developer.android.com/media/ai-enhancement/get-started)和 [Surface 视频模式](https://developer.android.com/media/ai-enhancement/surface-mode-lifecycle)提供可选路径。实验包锁定 `com.google.android.gms:play-services-media-effect-enhancement:16.0.0-beta09`。检查实际 AAR 的公开签名：`EnhancementOptions.isUpscaleVideoEnabled` 对应**请求**的视频放大效果；设备支持和模块就绪是两项独立检查。`onSurfaceProcessed(long)` 没有报告该帧实际启用了哪一种效果、放大倍率或内部 GPU 用时。实验只做能力探测，不把一般支持或会话回调标成已启用超分。API 36 模拟器无 Google Play services，实测得到 `MISSING_SERVICES`。另建隔离的 Android 17/API 37 Google Play 模拟器，确认已装 GMS 26.32.37；同一实验 APK 的设备支持查询返回 false，记为 `UNSUPPORTED`。两次均未下载模块。需要带 GMS 且通过支持检查的真机，并有可归因的输出后，才比较 Google 和 GLES 的同片效果。

## 复跑播放器实验

从 foundation 工作树运行，准备 JDK 17、Android SDK/NDK、`ffprobe`，并把 Android build-tools 的 `aapt` 与 platform-tools 的 `adb` 放入 PATH。`SERIAL` 必须是明确选定的测试设备，`FIXTURE` 是下文 SHA-256 对应的固定视频，`OUT` 是新的验收目录。

```sh
rtk proxy ./gradlew -PenableRealtimeSrDemo=true :examples:android-realtime-sr:assembleDebug :examples:android-realtime-sr:assembleDebugAndroidTest :examples:android-realtime-sr:testDebugUnitTest :examples:android-realtime-sr:lintDebug
rtk proxy python3 tools/probes/android_realtime_sr_device.py --serial "$SERIAL" --fixture "$FIXTURE" --case short-abba --out "$OUT/abba"
rtk proxy python3 tools/probes/android_realtime_sr_device.py --serial "$SERIAL" --fixture "$FIXTURE" --case auto --out "$OUT/auto"
rtk proxy python3 -m tools.probes.android_realtime_sr_paused --serial "$SERIAL" --fixture "$FIXTURE" --out "$OUT/paused"
rtk proxy ./gradlew -PenableRealtimeSrDemo=true -PenableGoogleSr=true :examples:android-realtime-sr:assembleDebug
rtk proxy python3 tools/probes/android_google_sr_device.py --serial "$SERIAL" --out "$OUT/google.json"
```

不带 Google 属性时生成 `io.ondeviceai.realtimesr.demo`（minSdk 26）；带属性的实验 APK 是另一个包 `io.ondeviceai.realtimesr.demo.google`（minSdk 30）。这两个属性构建在本机使用相同 APK 输出路径；切回普通包前应重新构建。测试驱动校验包名；手机上两包互不覆盖。手动观看普通 Demo 时用 **Pick** 选择片源，播放中可选择 **Original / Auto / CuNNy**。本地验收：35 个 Android JVM 用例、16 个播放报告/驱动用例通过，Debug 构建及 lint 通过（0 errors，9 warnings）。这次未向远端 Wiki、制品仓库或正式 App 发布。

## 像素有没有算对

同一固定 30 秒 H.264/AAC 片，SHA-256 `df677798a8604a7a6289fcf50a0fef3844a047631e0041ae7d5563c55f3746ec`。MediaCodec 在 PTS 8,000,000 µs 输出 1920×1080；探针直接读解码器 Y 平面，将同一组字节交给 GLES 和独立标量参考。整帧中 **8,276,882/8,294,400** 个 8 位亮度输出完全相同，余下 **17,518** 个相差 1。和双线性不同的有 505,633 个；差异个数不等于质量收益。另有六个小样本，涵盖黑场、脉冲、斜边及不同解码方式的裁剪，最大差不超过 1。

小裁剪曾经全部通过，旧版按归一化坐标做硬件采样却在全尺寸上最大差 14。保持相同输入、编译器、系数和输出尺寸，只改第一段为整点读取时最大差 4，只改最后一段为显式双线性时为 13，同时修改后为 1。这个对照定位到该机上的采样路径；没有跨驱动归因。[机器可读逐组结果、两处采样的四路对照及哈希](android-realtime-sr-pixel-emulator-20261120.json)。

## 画面有没有更接近原片

取同一视频 2、8、17、25 秒的完整解码帧，将原始亮度以 2×2 像素平均降到 960×540，然后用双线性或 CuNNy 还原到 1920×1080。四个时刻均用原帧作参考，无二次编码、无亮度范围扩展。四帧等权平均如下；[每帧指标、误差平方和及图像哈希](android-realtime-sr-static-quality-20261120.json)。

| 重建方法 | 亮度 PSNR（dB） | 亮度 SSIM |
|---|---:|---:|
| 双线性 | 36.6491 | 0.982712 |
| GLES CuNNy 2× | 39.5447 | 0.991465 |

这说明在该片的四个合成降采样帧上，CuNNy 重建更接近原始亮度。没有原生 4K 真值，不据此评估新增细节；也未测运动闪烁、人眼偏好、屏幕颜色或实时性。灰度读回的 alpha 固定为 255，不代表播放器完成了色度合成。本站工作树本地留有四个时刻的[同帧观看页](../../artifacts/verification/android-realtime-sr-20261120/still-comparison.html)，不随 Git 分发片源。

## 在独立设备上复算

以下从 foundation 工作树运行，需要 JDK 17、本机 Android SDK/NDK，以及显式选定的测试设备。设置 `SERIAL`、`FIXTURE`、`ANDROID_HOME`、`JAVA_HOME`；`HOST` 是 NDK 的 host tag（本次 `darwin-x86_64`）。此例为 arm64，不安装或替换应用。

```sh
TMP=$(mktemp -d)
D=examples/android-realtime-sr
R=/data/local/tmp/odai-sr-$(uuidgen)
NDK="$ANDROID_HOME/ndk/27.0.12077973"
JAR="$ANDROID_HOME/platforms/android-36/android.jar"
rtk proxy mkdir -p "$TMP/classes"
rtk proxy "$JAVA_HOME/bin/javac" --release 8 -cp "$JAR" -d "$TMP/classes" "$D/reference/SrFixtureCrop.java"
rtk proxy "$ANDROID_HOME/build-tools/36.0.0/d8" --min-api 26 --lib "$JAR" --output "$TMP/crop.jar" "$TMP/classes/io/ondeviceai/realtimesr/validation/SrFixtureCrop.class"
rtk proxy "$NDK/toolchains/llvm/prebuilt/$HOST/bin/aarch64-linux-android26-clang++" -std=c++17 -O2 "$D/reference/pixel_probe.cpp" -lEGL -lGLESv3 -o "$TMP/pixel-probe"
rtk proxy adb -s "$SERIAL" shell mkdir "$R"
rtk proxy adb -s "$SERIAL" push "$D"/src/main/assets/sr/*.comp "$TMP/crop.jar" "$TMP/pixel-probe" "$R/"
rtk proxy adb -s "$SERIAL" push "$FIXTURE" "$R/fixture.mp4"
rtk proxy adb -s "$SERIAL" shell chmod 700 "$R/pixel-probe"
rtk proxy adb -s "$SERIAL" shell "CLASSPATH=$R/crop.jar app_process /system/bin io.ondeviceai.realtimesr.validation.SrFixtureCrop $R/fixture.mp4 8000000 0 0 1920 1080 $R/input.y"
rtk proxy adb -s "$SERIAL" shell "$R/pixel-probe" "$R" 1920 1080 "$R/input.y" "$R/output.y"
rtk proxy adb -s "$SERIAL" pull "$R/input.y" "$TMP/input.y"
rtk proxy adb -s "$SERIAL" pull "$R/output.y" "$TMP/output.y"
rtk proxy python3 -m tools.probes.verify_cunny_pixels --metal "$D/reference/CuNNy.metal" --width 1920 --height 1080 --input "$TMP/input.y" --output "$TMP/output.y"
```

播放器门槛见[设计与指标定义](../superpowers/specs/2026-11-20-android-realtime-video-sr-design.md)：必须区分提交和实际呈现、增强与同帧原画回退；30 秒短测后才做 30 分钟持续验证。此记录中的离屏测试包含强制回读和 CPU 校验，不可作为播放耗时。

# Android 播放时超分：覆盖与验证记录

更新至 2026-11-21。独立播放器可手动选择轻量 Spatial 或与 iOS 相同的 CuNNy 1×8 BOX，做 2× 亮度增强。首台 Android 真机 TECNO KM9 的 1080p/30 强制增强未确认增强帧上屏；新增的 540p/30 实播也未通过增强门槛，自动模式会回退原画。本页分别列出真机、模拟器和离线导出的证据。

[KM9 固定刷新率：直出、EGL 原画和 Spatial 的实际呈现](android-motion-cadence-km9-20261121.md) · [在手机上观看运动 A/B](android-motion-pairs-km9-20261121.md)。

[KM9 540p：Spatial、CuNNy 与原画的十场对照](android-spatial-playback-km9-20261121.md)。轻量 Spatial 已在播放中确认增强上屏；调试对照缩短了原画显示延迟，但严格的按时增强门槛仍未通过。

| 环境或方案 | 已验证 | 尚无证据 |
|---|---|---|
| API 36，Android Emulator / ANGLE SwiftShader，GLES 3.1 | 有声 30 秒片 A/B/B/A 四场完整解码；原画和 CuNNy 均有 EGL 提交。离屏整帧和四个时刻的有参考重建 | 手机 GPU 性能、真实显示面上的颜色、逐帧上屏及长期表现 |
| API 37，Android 17 / Google Play 16 KB 系统映像，SwiftShader GLES 3.1 | 同一个普通 APK 的自动模式解码 900 帧，900 次原画提交，首末帧间隔 30.28 s；Google 验证包探测不支持 | 手机 GPU 性能及显示延迟；不能把软件模拟器结果推广到 Android 17 手机 |
| API 35，TECNO KM9 / MT6835 / Mali-G57 MC2 | GLES 3.2 真机 1080p 与 540p 播放、540p 轻量 Spatial 及提前提交对照、逐帧上屏时间、暂停同帧、整帧 CPU 对照；1080p 与 540p 实时增强均未达标。Google SDK 返回 UNSUPPORTED | 30 分钟、运动盲评、其他机型或驱动；Google 540p 未测试 |
| API 26/27、其他 Adreno/Mali 手机 | API 26 是实验模块的 minSdk | 真机兼容性、GLES 3.1 能力、增强可用率与观感 |
| LiteRT 1.4.2 / ESRGAN 4× | [既有 Android 文件导出](android-video-sr-demo.md)；实机同编码条件下，[飞鸟](f11-android-sr-controlled-20261106.md)和[瀑布](f11-android-sr-waterfall-20261106.md)的平均 PSNR/SSIM 均低于双线性 | 不据此推断同场景 CuNNy 2× 或实时播放 |
| Google Play services Media Enhancement | 单独的 API 30+ 实验包在无 GMS 的 API 36 返回 MISSING_SERVICES；在有 GMS 的 API 37 模拟器和 API 35 TECNO 真机均返回 UNSUPPORTED。默认包不含 Media Enhancement SDK | 是否支持这条片源、模型是否可用、实际倍率与耗时 |

## TECNO KM9 真机 540p→1080p：2026-11-21

在同一台手机上，960×540、30 fps 片源放大到 1920×1080（内部亮度尺寸）。540p 文件 SHA-256 为 `e698bee8c98c3158eb5e99e26f1cbe13334b52557720b2129a3e31dd2fa67dcb`。它与 1080p 长测片的 900 个视频帧时间戳逐个相同，AAC 压缩音频逐字节相同。五场均完整解码。表中上屏数只计能配对到实际显示时间的帧，包括迟到显示；缺少确认不等于物理丢帧。

| 模式 | 首末取帧间隔（秒） | 确认上屏原画 / 900 帧 | 确认上屏增强（帧） | 有效 GPU P95（ms） | 取帧至上屏 P95（ms） |
|---|---:|---:|---:|---:|---:|
| A1 原画 | 29.861 | 899 | 0 | — | 62.50 |
| B1 强制 CuNNy | 29.859 | 865 | 0 | 44.24 | 73.82 |
| B2 强制 CuNNy | 29.865 | 883 | 0 | 44.52 | 79.52 |
| A2 原画 | 29.872 | 899 | 0 | — | 61.59 |
| 自动 | 29.853 | 894 | 0 | 46.38 | 61.81 |

强制模式的首末取帧间隔从 1080p 的 55.33–55.56 秒缩至约 29.86 秒，有效 GPU 耗时 P95 从约 127 ms 降至约 44 ms。两场各有 899 次成功提交，全部因 `enhancement_not_ready` 提交同帧原画。按源帧配对，取帧开始到计划选择原画或增强的时刻（目标呈现时间前 2 ms），可用时间中位数分别是 20.22 和 19.86 ms。合计 945 个有效 GPU 样本全部超过各自帧的可用时间。两场各缺一个有效计时（471/472、474/475），全作业 GPU 门槛记为未验证；按时增强率两场均为 **0/900**，明确未通过。原画的显示 P95 也超过 33⅓ ms，不能把所有显示延迟归因于增强。

自动模式提交 899 帧原画，其中 13 帧请求增强后回退、886 帧未请求增强。暂停在源帧 65（PTS 2.167 秒）切换原画→CuNNy→原画，62,624 个 RGB 像素发生变化，切回时全部 RGBA 字节与第一次一致。跳转到 15 秒后可续播；退后台前和恢复时均为 17 秒，随后前进到 19 秒。**此机播放 540p/30 仍建议用原画。** 未启动 30 分钟长测，未复测 540p 的 Google 路线，也未做运动画质盲评。

[五场数据、片源与包哈希](android-realtime-sr-km9-540p-20261121.json) · [可复算的逐帧事件 ZIP](android-realtime-sr-km9-540p-20261121-events.zip)。

## TECNO KM9 真机 1080p→4K：2026-11-21

Android 15 / API 35，MT6835，Mali-G57 MC2，GLES 3.2 r38p1，1080×2400 屏幕。用同一普通 APK 播放同一段 30 秒 H.264/AAC、BT.709 视频，五场均解码 900 帧。4K 指内部 2× 亮度输出尺寸。**建议这台手机播放 1080p/30 时使用原画。**

| 运行 | 首末帧取帧间隔（秒） | 确认上屏原画 / 900 帧 | 确认上屏增强（帧） | 取帧至上屏 P95（ms） |
|---|---:|---:|---:|---:|
| A1 原画 | 29.883 | 892 | 0 | 61.84 |
| B1 强制 CuNNy | 55.563 | 573 | 0 | 147.07 |
| B2 强制 CuNNy | 55.327 | 575 | 0 | 146.27 |
| A2 原画 | 29.882 | 898 | 0 | 62.20 |
| 自动 | 29.898 | 882 | 0 | 62.82 |

每个确认的显示时间均通过 EGL frame ID 与源帧配对；未确认不等于物理丢帧。原画两场也超过取帧至上屏 P95 ≤33⅓ ms 的标准，不能把全部延迟归因于 CuNNy。两场强制增强的有效 GPU 计时分别为 426/427、423/424 个作业，样本 P95 为 **127.32、127.11 ms**（目标 ≤25 ms）；各缺一次计时，全作业 GPU 门槛记为未验证。实际上屏的按时增强率均为 0/900，短测不通过，不启动 30 分钟长测。

自动模式启动 7 个增强作业，成功提交 899 帧原画：15 帧尝试后回退、884 帧未请求增强，882 帧能确认上屏。暂停到源帧 66（2.200 秒），原画→CuNNy 有 47,454 个 RGB 像素变化，切回原画的 RGBA 字节完全一致。跳转至 15 秒后续播，退后台前为 17 秒、恢复时仍为 17 秒，随后前进到 19 秒。

**画质单独验证：** 在手机上解码固定片第 8 秒完整亮度帧并放大到 4K，同一输入的独立 CPU 参考中，8,276,885/8,294,400 个像素完全一致，其余 17,515 个仅差一个灰阶。另将四个固定参考帧的 540p 输入交给手机 GPU，重建至 1080p：CuNNy 平均亮度 PSNR/SSIM 为 **39.5447 dB / 0.991465**，双线性为 **36.6491 dB / 0.982712**。这是暂停或离屏的结果，未做运动盲评。

Google 实验包装在同一台手机上，GMS 为 25.10.36，官方 SDK 返回 `UNSUPPORTED`，因此没有 Google 增强输出可比较。此结果只覆盖这台 KM9。[逐场读数、画质和 SHA-256](android-realtime-sr-km9-20261121.json) · [五场原始时间记录（ZIP，不含视频或截图）](android-realtime-sr-km9-20261121-events.zip)。解压后可用 `python3 -m tools.probes.android_realtime_sr_report assess --index index.json --run b1 --out b1-result.json` 复算强制增强首场（从仓库根运行，按解压位置填写路径）。

## 模拟器：30 秒的视频实际怎样播放

固定片 1920×1080、30 fps、BT.709 SDR、有声 H.264/AAC，900 帧。两种模式使用相同解码、音频时钟、窗口和显示大小（模拟屏幕 1080×2340）。CuNNy 的 4K 是内部亮度计算尺寸。下表为原画／强制增强／强制增强／原画四个独立运行，均解码序号 0–899，音频末位置 30.009–30.016 秒。[逐场记录、APK/片源摘要和计时证据](android-realtime-sr-playback-emulator-20261120.json)。

| 模式 | 首末帧进入渲染的时间差 | 提交增强 | 提交原画 | 无成功提交记录 |
|---|---:|---:|---:|---:|
| 原画 A1 | 30.00 s | 0 | 900 | 0 |
| 强制 CuNNy B1 | 116.66 s | 67 | 827 | 6 |
| 强制 CuNNy B2 | 121.11 s | 61 | 831 | 8 |
| 原画 A2 | 29.92 s | 0 | 900 | 0 |

强制增强两场都无法持续按原速播放。原画回退是同一源帧，不能把回退算进按时增强率。增强作业分别启动且完成 790、796 次，但没有有效 GPU 耗时样本；四场均没有有效实际呈现时间，不能把 EGL 提交数当作上屏数、把未确认上屏当成物理丢帧，或报告显示延迟 P95。模拟器报 Vulkan 是 SwiftShader/ANGLE 的内部实现，应用后端始终调用 GLES。这组结果没有验证更换应用后端为 Vulkan 能改善性能。短测没有通过，不启动 30 分钟性能长测。

**自动模式。** 连续三次尝试仍未取得增强输出时，暂用原画 4 秒；再次失败时退避增至 8 秒、最多 16 秒。这个判断只用于调度。独立自动模式场次启动了 14 个增强作业，900 帧有成功提交：1 帧增强、17 帧尝试后回退、882 帧未请求增强；首末帧进入渲染的时间差为 30.27 秒。该机上的收益是避免强制超分拖慢整片，不能称为持续按时增强。

**暂停同帧。** 实际点击原画→CuNNy→原画，三次使用源帧序号 64、PTS 2.133 秒，播放位置保持 00:02。用 PixelCopy 读取整个 1080×1828 视频 Surface，再由主机逐像素比较：两次原画 RGBA 字节完全相同，CuNNy 与原画有 46,060 个 RGB 像素不同。恢复后时钟继续前进；拖动到 00:15 后，新代次的首个验收解码 PTS 为 15.000 秒，随后播放到 00:17。应用退到后台再恢复（同一 Activity 的 stop/resume）后，播放位置保持在 17 秒，并继续到 19 秒；新代次解码 PTS 为 17.733 秒。[同帧统计随源码保存](android-realtime-sr-playback-emulator-20261120.json)；带图页面仅在验收机的 `artifacts/verification/android-realtime-sr-20261120/playback/paused-final/compare.html`。这项验证包含窗口的彩色合成，既不是物理屏测量，也不是动态主观盲评。

在 API 37 的测试中，退后台时上一代窗口最初仍有两次失败的 EGL 提交。播放器改为在 Activity 暂停和 Surface 销毁时先停交新帧，再释放窗口。复测保留原有严格判定：没有失败的提交，暂停对照的两次原画完全相同；切到 CuNNy 的 45,782 个像素有变化。拖动后可续播，实际退后台和恢复后，解码在新的代次继续。修正后的同一 APK 也播放了完整固定片：自动模式 30.33 秒，0 帧增强／900 帧原画提交；强制模式 136.24 秒，36 帧增强／859 帧原画提交。两种模式都没有有效呈现时间。API 36 的完整四场与 API 37 窗口修正后的实测 APK 摘要分别保存在 JSON，未把前一版的成绩记到后一版。

## 手机上的官方 API 能做什么

Google Play services 的 [Media Enhancement 接入文档](https://developer.android.com/media/ai-enhancement/get-started)和 [Surface 视频模式](https://developer.android.com/media/ai-enhancement/surface-mode-lifecycle)提供可选路径。实验包锁定 `com.google.android.gms:play-services-media-effect-enhancement:16.0.0-beta09`。检查实际 AAR 的公开签名：`EnhancementOptions.isUpscaleVideoEnabled` 对应**请求**的视频放大效果；设备支持和模块就绪是两项独立检查。`onSurfaceProcessed(long)` 没有报告该帧实际启用了哪一种效果、放大倍率或内部 GPU 用时。实验只做能力探测，不把一般支持或会话回调标成已启用超分。API 36 模拟器无 Google Play services，实测得到 `MISSING_SERVICES`。另建隔离的 Android 17/API 37 Google Play 模拟器，确认已装 GMS 26.32.37；同一实验 APK 的设备支持查询返回 false，记为 `UNSUPPORTED`。两次均未下载模块。需要带 GMS 且通过支持检查的真机，并有可归因的输出后，才比较 Google 和 GLES 的同片效果。

## 复跑播放器实验

从 foundation 工作树运行，准备 JDK 17、Android SDK/NDK、`ffprobe`，并把 Android build-tools 的 `aapt` 与 platform-tools 的 `adb` 放入 PATH。`SERIAL` 必须是明确选定的测试设备，`FIXTURE` 是上文 540p 或 1080p 的固定长测视频，按所选文件的 SHA-256 校验，`OUT` 是新的验收目录。

```sh
rtk proxy ./gradlew -PenableRealtimeSrDemo=true :examples:android-realtime-sr:assembleDebug :examples:android-realtime-sr:assembleDebugAndroidTest :examples:android-realtime-sr:testDebugUnitTest :examples:android-realtime-sr:lintDebug
rtk proxy python3 tools/probes/android_realtime_sr_device.py --serial "$SERIAL" --fixture "$FIXTURE" --case short-abba --out "$OUT/abba"
rtk proxy python3 tools/probes/android_realtime_sr_device.py --serial "$SERIAL" --fixture "$FIXTURE" --case auto --out "$OUT/auto"
rtk proxy python3 -m tools.probes.android_realtime_sr_paused --serial "$SERIAL" --fixture "$FIXTURE" --out "$OUT/paused"
rtk proxy ./gradlew -PenableRealtimeSrDemo=true -PenableGoogleSr=true :examples:android-realtime-sr:assembleDebug
rtk proxy python3 tools/probes/android_google_sr_device.py --serial "$SERIAL" --out "$OUT/google.json"
```

不带 Google 属性时生成 `io.ondeviceai.realtimesr.demo`（minSdk 26）；带属性的实验 APK 是另一个包 `io.ondeviceai.realtimesr.demo.google`（minSdk 30）。这两个属性构建在本机使用相同 APK 输出路径；切回普通包前应重新构建。测试驱动校验包名；手机上两包互不覆盖。手动观看普通 Demo 时用 **Pick** 选择片源，播放中可选择 **Original / Auto / CuNNy / Spatial**。本地验收：35 个 Android JVM 用例、16 个播放报告/驱动用例通过，Debug 构建及 lint 通过（0 errors，9 warnings）。这次未向远端 Wiki、制品仓库或正式 App 发布。

## 真机复测

11 月 20 日复查：Mac 的 USB 清单可见 TECNO，针对该机的 `adb get-state` 返回 `device not found`。11 月 21 日重连后已完成上述真机短测。选择已开启 USB 调试的测试手机 `SERIAL`，沿用上节固定片和 APK 执行：

```sh
rtk proxy python3 -m tools.probes.android_realtime_sr_handset \
  --serial "$SERIAL" \
  --fixture "$FIXTURE" \
  --apk "$APK" \
  --test-apk "$TEST_APK" \
  --google-apk "$GOOGLE_APK" \
  --out "$OUT"
```

普通 debug 构建后，APK 示例路径为 `examples/android-realtime-sr/build/outputs/apk/debug/android-realtime-sr-debug.apk` 和 `examples/android-realtime-sr/build/outputs/apk/androidTest/debug/android-realtime-sr-debug-androidTest.apk`。先复制这两个 APK，再构建 Google 版本，因为构建路径会重叠。确保 `ffmpeg`、`ffprobe`、`adb`、`aapt` 在 `PATH` 中。`--google-apk` 是可选的，仅用于 API 30+；`--preflight-only` 可只做预检，但须使用不同的全新 `--out` 目录。

脚本成功退出不等于验证通过：各门槛须分别有证据。`$OUT` 应包含 `summary.json`、`abba`、`auto`、`paused`，以及可选的 `google.json`；30 分钟测试不会自动运行。先测 Mali TECNO KM9（API 35），再测 Adreno；若有设备，也测 API 26/27。

入口新增后，22 个主机用例通过；用留存的 API 36 报告复核，四场 A/B/B/A 和一场自动模式均完成解码，但因缺少显示计时，短测仍判为 `unverified`。在主机上用错误包名的真实 APK 测试，两个驱动均在首次 ADB 操作前拒绝。

Android Demo 面向 API 26+，用自写的 GLES 3.1 CuNNy 做 2× 增强，来不及时回退同帧原画；Google 是独立的可选能力探测，Vulkan 尚未实现。KM9 的 1080p 与 540p 实播短测均未通过实时增强门槛；默认建议此机使用原画，30 分钟测试未启动。快速对照可参见 [iOS demo 交接说明](ios-realtime-sr-demo-handoff.md)：iOS Demo 的部署目标为 15.1；支持系统 2× 的设备和片源优先用系统，其余按轻量 Metal → 优化 CuNNy → 原画选择；已在 iPhone 16 Pro Max、iOS 27.2.1 验证。

## 像素有没有算对

同一固定 30 秒 H.264/AAC 片，SHA-256 `df677798a8604a7a6289fcf50a0fef3844a047631e0041ae7d5563c55f3746ec`。MediaCodec 在 PTS 8,000,000 µs 输出 1920×1080；探针直接读解码器 Y 平面，将同一组字节交给 GLES 和独立标量参考。整帧中 **8,276,882/8,294,400** 个 8 位亮度输出完全相同，余下 **17,518** 个相差 1。和双线性不同的有 505,633 个；差异个数不等于质量收益。另有六个小样本，涵盖黑场、脉冲、斜边及不同解码方式的裁剪，最大差不超过 1。

小裁剪曾经全部通过，旧版按归一化坐标做硬件采样却在全尺寸上最大差 14。保持相同输入、编译器、系数和输出尺寸，只改第一段为整点读取时最大差 4，只改最后一段为显式双线性时为 13，同时修改后为 1。这个对照定位到该机上的采样路径；没有跨驱动归因。[机器可读逐组结果、两处采样的四路对照及哈希](android-realtime-sr-pixel-emulator-20261120.json)。

## 画面有没有更接近原片

取同一视频 2、8、17、25 秒的完整解码帧，将原始亮度以 2×2 像素平均降到 960×540，然后用双线性或 CuNNy 还原到 1920×1080。四个时刻均用原帧作参考，无二次编码、无亮度范围扩展。四帧等权平均如下；[每帧指标、误差平方和及图像哈希](android-realtime-sr-static-quality-20261120.json)。

| 重建方法 | 亮度 PSNR（dB） | 亮度 SSIM |
|---|---:|---:|
| 双线性 | 36.6491 | 0.982712 |
| GLES CuNNy 2× | 39.5447 | 0.991465 |

这说明在该片的四个合成降采样帧上，CuNNy 重建更接近原始亮度。没有原生 4K 真值，不据此评估新增细节；也未测运动闪烁、人眼偏好、屏幕颜色或实时性。灰度读回的 alpha 固定为 255，不代表播放器完成了色度合成。四个时刻的带图观看页仅在验收机的 `artifacts/verification/android-realtime-sr-20261120/still-comparison.html`；Git 中保留上表对应的逐帧指标。

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
rtk proxy "$NDK/toolchains/llvm/prebuilt/$HOST/bin/aarch64-linux-android26-clang++" -std=c++17 -static-libstdc++ -O2 "$D/reference/pixel_probe.cpp" -lEGL -lGLESv3 -o "$TMP/pixel-probe"
rtk proxy adb -s "$SERIAL" shell mkdir "$R"
rtk proxy adb -s "$SERIAL" push "$D"/src/main/assets/sr/*.comp "$TMP/crop.jar" "$TMP/pixel-probe" "$R/"
rtk proxy adb -s "$SERIAL" push "$FIXTURE" "$R/fixture.mp4"
rtk proxy adb -s "$SERIAL" shell chmod 700 "$R/pixel-probe"
rtk proxy adb -s "$SERIAL" shell chmod 444 "$R/crop.jar"
rtk proxy adb -s "$SERIAL" shell "CLASSPATH=$R/crop.jar app_process /system/bin io.ondeviceai.realtimesr.validation.SrFixtureCrop $R/fixture.mp4 8000000 0 0 1920 1080 $R/input.y"
rtk proxy adb -s "$SERIAL" shell "$R/pixel-probe" "$R" 1920 1080 "$R/input.y" "$R/output.y"
rtk proxy adb -s "$SERIAL" pull "$R/input.y" "$TMP/input.y"
rtk proxy adb -s "$SERIAL" pull "$R/output.y" "$TMP/output.y"
rtk proxy python3 -m tools.probes.verify_cunny_pixels --metal "$D/reference/CuNNy.metal" --width 1920 --height 1080 --input "$TMP/input.y" --output "$TMP/output.y"
```

播放器门槛见[设计与指标定义](../superpowers/specs/2026-11-20-android-realtime-video-sr-design.md)：必须区分提交和实际呈现、增强与同帧原画回退；30 秒短测后才做 30 分钟持续验证。此记录中的离屏测试包含强制回读和 CPU 校验，不可作为播放耗时。

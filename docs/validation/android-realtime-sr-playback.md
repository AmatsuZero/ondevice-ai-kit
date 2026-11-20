# Android 播放时超分：覆盖与验证记录

2026-11-20。首轮用与 iOS 相同的 CuNNy 1×8 BOX 做 2× 增强。在 Android API 36 模拟器上，已验证从实际视频取帧、三段 GLES 计算，以及一整帧 1080p → 4K 的亮度结果。真机播放、动态观感和持续实时性需要独立验收。

| 环境或方案 | 已验证 | 尚无证据 |
|---|---|---|
| API 36，Android Emulator / ANGLE SwiftShader，GLES 3.1 | MediaCodec 解码 H.264；离屏 CuNNy，含整帧、边缘和裁剪；四个时刻的有参考重建 | 手机 GPU 性能、真实显示面上的颜色、逐帧上屏及长期表现 |
| API 35，TECNO KM9 / MT6835 / Mali-G57 MC2 | 现场曾读到 GLES 3.2、1080×2400、有 GMS。已有独立的 LiteRT 离线结果 | 本次播放测试时未连接；CuNNy 和 Google Upscale 均未跑 |
| API 26/27、其他 Adreno/Mali 手机 | API 26 是实验模块的 minSdk | 真机兼容性、GLES 3.1 能力、增强可用率与观感 |
| LiteRT 1.4.2 / ESRGAN 4× | [既有 Android 文件导出](android-video-sr-demo.md)；实机同编码条件下，[飞鸟](f11-android-sr-controlled-20261106.md)和[瀑布](f11-android-sr-waterfall-20261106.md)的平均 PSNR/SSIM 均低于双线性 | 不据此推断同场景 CuNNy 2× 或实时播放 |
| Google Play services Media Enhancement | [官方 Surface 路线及设备探测计划](../superpowers/plans/2026-11-20-android-realtime-video-sr-google.md) | 是否支持这条片源、模型是否可用、实际倍率与耗时 |

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

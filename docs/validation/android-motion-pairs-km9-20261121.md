# 在 KM9 上看原画和 Spatial：运动 A/B

测试手机上打开 **运动 A/B**。每段先后点「播放 A」「播放 B」，可重播，按正常速度观看。比较哪边更清晰，也留意哪边的边缘有光晕、细线闪烁、横移卡顿，或左上角闪块与短音明显错开。两边播完后点「提交偏好」，选 A、B、一样或分不出；备注可写「B 字更清楚，但移动时有闪烁」。共三段。看不出差异是有效结果。

| 片段 | 看哪里 |
|---|---|
| 1 · 快速横移 | 栏杆间隙、窗框的边缘，以及移动时的流畅感 |
| 2 · 字幕细线 | 小号 VIDEO 30、30 FPS 的笔画和背景细线 |
| 3 · 人物轮廓 | 合成人物侧脸与颗粒背景的交界，移动时是否闪烁或带亮边 |

三段都是从程序生成的原画图板取移动视窗，再缩为 960×540 的 SDR 视频；每段 6 秒、180 帧。每秒闪块和音调仅供感受明显错位，不能量出物理音画同步误差。剪辑不含摄像头、人声或真实人物素材，不代表自然片源总体。

素材在 KM9 上逐个经 Android MediaExtractor 验证为 960×540、30 fps、BT.709、有限范围、SDR。初版只有码流/工具端标记，MP4 容器缺少颜色条目，播放器会退回原画；修正素材并核实真机读取后才用于盲评。

**比较的是实际播放策略。** 原画和手动 Spatial 都通过同一 EGL 播放器、同一就绪即提交设置播放同一编码文件。每个会话、每个片段随机决定 A/B 对应关系，播放页不显示模式；Spatial 来不及时允许同帧原画回退。只有两边都到达片尾、且各自至少有一帧请求的模式被确认显示，才可评分；这不代表全片每帧都增强。每票保留两边确认上屏的各后端帧数（重播会累计）和启动次数，可区分「比较了哪种策略」与「实际有多少增强」。三段最好在同一亮度、方向和观看距离下一次完成。离开页面会结束本次会话；再次打开将重新随机。多次重放同一段不算独立观众。

**数据只写在独立 debug 包的私有目录。** 每个会话是 `files/motion-pairs/<随机会话>/`，其中 `scene-1.json` 到 `scene-3.json` 分别保存选择、可选备注、随机映射、播放次数和实际显示计数；三个文件都存在才是完整会话。不要在参与者评分前揭示映射。公开报告不含个人备注或主观投票。自动化验收票不作为人的选择。

真机流程验收覆盖三组的六次实播、禁止提前投票、保存后切到下一段、最后回到新的空白会话。三段增强侧分别实际呈现 167、164、168 帧 Spatial（每段 180 源帧）；自动化测试票已清除，尚无人的偏好结论。

## 重新准备一台调试手机

在 foundation 根目录用标准 Python、FFmpeg（含 libx264）生成本地的三段视频，构建普通独立 debug 包。不要开启 Google 实验属性。指定已经选定的设备，使用新的素材输出目录。仅在目标手机尚未准备素材时执行写入，已有素材先核 SHA，勿覆盖未知文件。

```sh
python3 -m tools.probes.android_motion_pair_clips "$NEW_CLIPS_DIR"
./gradlew -PenableRealtimeSrDemo=true :examples:android-realtime-sr:assembleDebug
adb -s "$SERIAL" install -r -t examples/android-realtime-sr/build/outputs/apk/debug/android-realtime-sr-debug.apk
PKG=io.ondeviceai.realtimesr.demo
adb -s "$SERIAL" shell run-as "$PKG" mkdir -p files/motion-pairs
for i in 1 2 3; do
  adb -s "$SERIAL" shell "run-as $PKG sh -c 'set -C; cat > files/motion-pairs/$i.mp4'" < "$NEW_CLIPS_DIR/$i.mp4" || break
  adb -s "$SERIAL" shell run-as "$PKG" sha256sum "files/motion-pairs/$i.mp4"
done
adb -s "$SERIAL" shell am start -n "$PKG/io.ondeviceai.realtimesr.MotionPairActivity"
```

[同机播放时序与验收记录](android-motion-cadence-km9-20261121.md)

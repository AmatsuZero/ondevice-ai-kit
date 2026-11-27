# 离线变声：文件与实时试听原型

在本目录运行 `python3 serve.py --port 8765`，再用 Safari 或 Chrome 打开 `http://127.0.0.1:8765/`。页面默认显示文件变声。内置示例和实时监听不依赖模型或网络。

- **文件变声**：在同一句话上切换原声、升调或降调 4 个半音，以及 OpenVoice 音色 A、B、C。可导入 WAV，或录制最长 30 秒的音频；播放器下方可下载当前结果。导入文件的「原声」下载保留原始 WAV 字节。自定义升降调需要本机 FFmpeg；自定义 A/B/C 需要启动下面的本机模型服务。内置 A/B/C 随原型提供，无需临时跑模型。
- **实时变声**：先点「播放示例」，边播边比较原声、大叔、少女、播音员；也可以「按住试说」，在耳机或系统当前的扬声器上监听，松开即停。首次允许麦克风后再按住。此模式只使用浏览器 AudioWorklet 做变调、均衡等 DSP 处理，不调用文件转换服务。浏览器无法确认回声消除开启时，会停止麦克风监听，仍可播放示例。
- 切换模式或导航会停止原来的播放和麦克风。文件、录音和转换结果仅为当前页面临时保留，除非手动下载。此原型只绑定本机回环地址。

## 让自己的声音也能转成 A/B/C

已有按本仓库 `demo/openvoice/OpenVoice.cpp` 构建的动态库，以及包含 `ref_encoder.onnx`、`converter.onnx`、`voice{A,B,C}.f32` 的资源目录时，从原型目录运行：

```sh
python3 serve.py --port 8765 --library "$voice_lib" --assets "$voice_assets"
```

在 Apple Silicon Mac 上也可复用原生 Demo 已缓存的 ONNX Runtime 1.23.2 发行包。以下从仓库根目录运行，`cache_dir` 指向已有 `ort-1.23.2.nupkg` 和两个 ONNX 模型的目录（默认是当前检出的缓存）；不会将动态库或模型写入源码树。

```sh
cache_dir="${cache_dir:-$PWD/demo/openvoice/build/downloads}"
voice_build="$(mktemp -d)"
unzip -q "$cache_dir/ort-1.23.2.nupkg" 'build/native/include/*' 'runtimes/osx-arm64/native/*' -d "$voice_build"
ort_lib="$voice_build/runtimes/osx-arm64/native"
ln -s libonnxruntime.dylib "$ort_lib/libonnxruntime.1.23.2.dylib"
clang++ -std=c++17 -O2 -dynamiclib demo/openvoice/OpenVoice.cpp \
  -I "$voice_build/build/native/include" -L "$ort_lib" -lonnxruntime \
  -Wl,-rpath,"$ort_lib" -o "$voice_build/libdemo-voice.dylib"
mkdir "$voice_build/assets"
cp "$cache_dir"/{ref_encoder,converter}.onnx demo/openvoice/assets/voice{A,B,C}.f32 "$voice_build/assets/"
python3 docs/superpowers/prototypes/2026-11-27-realtime-voice/serve.py \
  --port 8765 --library "$voice_build/libdemo-voice.dylib" --assets "$voice_build/assets"
```

此处模型运行在本机 CPU。原生 iOS Demo 的 CPU/Core ML 选择仍在原 App 中，不是浏览器开关。

## 试听素材与验收范围

普通话来自 Google FLEURS 的 cmn_hans_cn 测试样本 1698（CC BY 4.0），原声长 9.72 秒。内置 A/B/C 使用此前同一句话的 OpenVoice WAV；原声增加 20 dB，A/B/C 分别调整播放素材电平以便对听。两条固定升降调样本由 FFmpeg 从该原声渲染，时长不变。原始及试听素材的 SHA-256、增益和变调参数见 [clips/sources.json](clips/sources.json)，许可与来源见 [Demo 素材说明](../../../../demo/openvoice/assets/CREDITS.txt)。

已在桌面 Chrome 用系统提供的虚拟麦克风验证两个模式切换、内置试听、导入、录完回听、下载内容和轨道释放。此版用于交互与听感评审；手机端实时链路、扬声器回授、端到端延迟以及角色盲听需要单独实测。

# Optional Android ESRGAN video Demo

Build with `-PenableLiteRtSr=true`. Default Demo omits `android:backend-litert-sr` and LiteRT from its runtime graph. This option may be combined with `-PenableLiteRtLm=true`. Models are never downloaded by Gradle or packaged in the APK.

Runtime: `com.google.ai.edge.litert:litert:1.4.2` (standalone CPU Interpreter, Apache-2.0). The 1.x Interpreter API is chosen for the legacy official model; no Google Play services or GPU delegate is required. Google Maven metadata also lists the newer CompiledModel 2.x line; this prototype does not claim to exercise that API.

Model: TensorFlow's official [Android ESRGAN example](https://github.com/tensorflow/examples/tree/master/lite/examples/super_resolution/android). Its README identifies the [TF Hub ESRGAN model](https://github.com/tensorflow/tfhub.dev/blob/master/assets/docs/captain-pool/esrgan-tf2/1.md), whose model card explicitly declares **MIT**. Do not confuse the model license with the sample code's Apache-2.0 license.

Download the exact official conversion to a local file, then select **导入超分模型** in the opt-in Demo. The importer checks the following exact identity before atomic installation; wrong files cannot replace the existing model.

- URL: `https://storage.googleapis.com/download.tensorflow.org/models/tflite/esrgan/ESRGAN.tflite?generation=1603787445777842`
- Bytes: `4993712`
- SHA-256: `1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b`
- Storage MD5: `023aaf9c005dda701ab4ef770c2f27d0`
- Input: `[1,50,50,3]` float32 RGB, 0–255. Output: `[1,200,200,3]` float32 RGB, clipped to 0–255.

The frame adapter uses 50×50 tiles, 8-pixel replicated halo, central 34×34 regions and exact 4× assembly. This is per-frame learned SR, not a temporal model. No input resizing or bicubic fallback is used. Tiling artifacts, temporal consistency, natural quality and long-resource budgets remain unaccepted.

Supported prototype input: local H.264 MP4, even dimensions 16×16 through 320×180, SDR BT.709 limited range, at most 2 seconds / 48 decoded frames / 30 fps, and at most one AAC track with nonnegative packet PTS inside the clip duration when preserving audio. AAC priming/edit-list handling is deferred and rejected explicitly; no packets are silently dropped. Actual codec support is checked on-device. Decode/encode prefer a software codec that advertises the requested format. RGB conversion follows decoder-reported BT.709; unknown/HDR/unsupported color fails explicitly. Frame PTS and compressed AAC packets keep original timestamps. Cancellation waits for a blocking model tile, then stops and releases codecs before returning.

Media imports are bounded to 256 MiB and stored behind opaque input leases. Output leases survive SDK client close and are explicitly released. Open/share creates an independent private cache copy exposed through a read-granted FileProvider URI; these copies remain for one day and are pruned on the next export. No Photos write or cloud upload occurs.

Headless validation runner: `io.ondeviceai.demo.VideoSrInstrumentation`. Build test APK with `-PodaiTestRunner=io.ondeviceai.demo.VideoSrInstrumentation`, stage owned model/fixture under `files/video-sr-validation/`, install matching opt-in app/test APKs and run `am instrument -w io.ondeviceai.demo.test/io.ondeviceai.demo.VideoSrInstrumentation`. Inspect the returned `status` and code, not just adb exit status. This runner does not launch an Activity or unlock a device; it is separate from picker/playback/share UI acceptance.

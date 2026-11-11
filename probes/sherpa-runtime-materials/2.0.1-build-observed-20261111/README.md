# Android 构建日志对应的补充来源材料

本目录补充固定 Sherpa2.0.1 AAR（SHA256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`）的构建依赖证据。原 [50文件候选材料](../2.0.1-a8d09d1d/RUNTIME-NOTICES.md)保持原字节；本目录另有4份固定Sherpa构建源、9份许可/声明、manifest及本文，共15文件，不含二进制或模型。

| 对象 | 官方job观察与补充文本 |
| --- | --- |
| Eigen | 四ABI日志均下载5.0.1。补 [LICENSE](licenses/eigen-5.0.1/LICENSE)、[COPYING.README](licenses/eigen-5.0.1/COPYING.README)、[MPL2](licenses/eigen-5.0.1/COPYING.MPL2)、[APACHE](licenses/eigen-5.0.1/COPYING.APACHE)、[BSD](licenses/eigen-5.0.1/COPYING.BSD)、[MINPACK](licenses/eigen-5.0.1/COPYING.MINPACK)；来自5.0.1 tag当前解析commit `bc3b39870ecb690a623a3f49149a358b95c5781d`，逐文件GitBlob核验。原3.4.0材料属于递归源码候选，不能据此描述该job使用3.4.0。 |
| websocketpp | ARM64/ARMv7的日志开关ON且下载固定commit `b9aeec6eaf3d5610503439b4fae3581d9aff08e8`；x86/x86_64开关OFF。保留完整 [COPYING](licenses/websocketpp/COPYING)，含主库与bundled库声明，不能压缩成单一标签。 |
| asio | 上述ARM构建下载`asio-1-24-0`。保留 [COPYING](licenses/asio/COPYING)及其指向的完整 [Boost许可](licenses/asio/LICENSE_1_0.txt)。 |

固定Sherpa commit `af2d8d81dbfa61c4cea4c568da78f3982edca005`的构建源为 [eigen.cmake](source-evidence/sherpa-onnx/cmake/eigen.cmake)、[kaldi-decoder.cmake](source-evidence/sherpa-onnx/cmake/kaldi-decoder.cmake)、[websocketpp.cmake](source-evidence/sherpa-onnx/cmake/websocketpp.cmake)、[asio.cmake](source-evidence/sherpa-onnx/cmake/asio.cmake)。Sherpa主CMake先登记自身cmake路径，之后decoder才附加其路径；decoder的`include(eigen)`因此不能只按decoder目录下的3.4.0模块解读。父源码顺序与上述job实际5.0.1下载/configure日志相符。

[manifest](material-manifest.json)含13份来源的size/SHA256/GitBlob、原始job日志摘要、20条精确行号摘录及四ABI开关表。job为 [121661567095](https://github.com/k2-fsa/sherpa-onnx/actions/runs/40348432396/job/121661567095)。许可原文保持上游字节；文本适用范围以原文为准。

这些证据证明job报告的下载/配置版本，并补齐对应来源文本。下载或全局开关不证明代码最终留在每个`.so`，当前tag解析也不证明历史归档逐字节不变。没有下载归档或重新构建；精确source-to-prebuilt、全部静态内嵌组成、最终生产通知保留和独立发布复审仍未完成。本补充不替代ORT自身完整组件清单，不放行生产或关闭W3。

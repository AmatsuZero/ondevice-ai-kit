# W3 运行时构建依赖补核

2026-11-11。已用现存官方Android job日志和固定Sherpa构建源补核依赖版本，新增 [15文件补充材料](../../probes/sherpa-runtime-materials/2.0.1-build-observed-20261111/README.md)。**W3仍open，实际静态内嵌组成、最终生产通知保留及独立复审未通过。**

| 已核事实 | 证据与限定 |
| --- | --- |
| Eigen版本 | 同commit官方job四ABI均下载/configure5.0.1，下载行911/1713/2471/3266；原50文件中的3.4.0来自kaldi-decoder递归源码候选。新取得的Sherpa `cmake/eigen.cmake`明确指定5.0.1和归档SHA256，不能把子依赖目录的默认模块当成父工程实际选中模块。 |
| 模块路径 | 已有Sherpa主CMake521–522先登记自身路径、546调用decoder；固定decoder构建模块再APPEND其路径，decoder主CMake57 `include(eigen)`。这与日志5.0.1一致，不需要修改或重建依赖。 |
| WebSocket相关分支 | arm64-v8a/armeabi-v7a日志开关ON并下载websocketpp/asio，x86_64/x86开关OFF；开关行801/1603/2361/3156。不能将一个ABI的值泛化到整个AAR，也不能仅凭下载认定最终库成员。 |
| 许可文本 | Eigen5.0.1六份许可/声明、websocketpp完整COPYING、asioCOPYING及其指向的Boost许可，共9份；另4份固定Sherpa构建源。全部13来源size/SHA256/GitBlob核对，原字节集成，未将websocketpp的bundled声明省略。 |

补充manifest记录官方job `121661567095`的原始日志SHA、20条带行号摘录及四ABI表。新Eigen原文来自官方5.0.1 tag当前解析commit `bc3b39870ecb690a623a3f49149a358b95c5781d`；不将当前tag解析当成历史归档内容未变的证明。原50份材料的全部摘要保持，原108冻结文件/旧APK未变；无新助手、设备/模型调用或二进制下载。

这项排除了“该job使用Eigen3.4.0”这一错误推断，收窄了此前websocket/asio未追踪分支。它没有穷尽ORT递归静态组件，没有证明来源与每份预编译库一一对应，也未修改生产通知。机器收据见 [JSON](w3-runtime-build-dependency-reconciliation-20261111.json)；本机原始API、GitBlob收据和完整性结果位于 `.omx/artifacts/w3-runtime-materials-20261111/build-dependency-reconciliation/`。外部heap Kotlin探针仍缺、免费接续线路问题未答，原额度线路未重试。

# Foundation 验收记录

执行日期：2026-10-15。工作分支 `feat/sdk-foundation`；代码提交以 git 历史为准。构建日志与本机产物放在忽略目录，不提交模型权重、照片、设备标识或签名材料。

| 项目 | 证据 | 结论 |
| --- | --- | --- |
| Swift SDK | `scripts/verify-apple.sh` 的 XCTest 和原生消费者日志 | 以脚本本次结果为准；macOS 测试不替代 iPhone |
| KMP export | Swift bridge typecheck、KMP simulator tests | 跨语言接口编译与注入验证 |
| iOS Demo | iOS27 模拟器安装/启动、合成空白图输入 | 启动已实际验证；Vision Code9 推理上下文失败，未生成有效 benchmark |
| Apple 二进制 | 两个平台 archive succeeded；BinaryConsumer 不链接 KMP 的 XCFramework build succeeded | arm64 iOS/simulator 本地编译通过，无发布/签名承诺 |
| Android | JVM tests、lint、APK、本地 Maven consumer | 以脚本本次结果为准；没有连接 Android 设备，JNI 推理未验收 |
| Benchmark | Python schema/分组/百分位测试；原生 Demo 采集入口 | 无设备实测结果时不生成虚假样本 |
| 同步 | 两端 refs 新鲜读取并比较 | 仅已提交分支；未提交文件不属于远端交付 |

原始证据目录：`.superpowers/sdd/2026-10-15-ondevice-ai-kit-foundation`（本机任务记录）与 `artifacts/verification`；SDK/APP 产物为 `artifacts/xcframeworks`、`artifacts/iOSDemo`、`artifacts/BinaryBuild`、`demo/androidApp/build/outputs/apk`。

## 尚未验证

iOS15、现代 iPhone、Android API26 和现代 Android 的启动/权限/相机/无脸/旋转/取消/后台/断网均需设备单项签收。持续摄像头背压、峰值内存、热状态和能耗没有实测。iOS27 模拟器 Vision 的错误不被计为成功空脸结果。没有公开或内部制品仓库发布，没有自动镜像 CI。

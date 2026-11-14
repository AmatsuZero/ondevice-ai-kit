# W3 ORT 发布成员与 Sherpa AAR 对应核验

四个 ABI 的 `libonnxruntime.so` 已确认逐字节一致；补齐了原来仅有成员大小的证据缺口。W3、R4/R5 仍未完成。

本次从 [`csukuangfj/onnxruntime-libs` 固定发布 URL](https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-android-1.28.2.zip)下载 ORT `v1.28.2` ZIP，重新读取 GitHub asset `544593317` 的大小与摘要，再核完整文件：33,765,715 B，SHA-256 `01518867f78241138b6aa25925802e843a4fa9085af8d303d49e35bbb52aff4d`。本地 Sherpa AAR 重新核为 49,441,341 B，SHA-256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`，与冻结清单一致。未安装、执行或替换二进制。

| ABI | 成员大小 B | 两归档成员的相同 SHA-256 |
| --- | ---: | --- |
| arm64-v8a | 22,249,560 | `33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1` |
| armeabi-v7a | 15,359,592 | `7a8f17a707e52e0ac3a026be319272876d9eae61f4bb19585292772d785f7744` |
| x86 | 26,491,148 | `fcb6cd80415f7951e58fdd8158a238946dce3710d5fee492b67174d5131f9eee` |
| x86_64 | 25,581,120 | `087e84297cad64acb823c3d901d6e0cc934e91add7cdb20da802cd1c0bea5c3e` |

父验收先核两个完整归档摘要，再以 Python `zipfile` 读取成员、核大小/SHA-256/完整字节；独立 Info-ZIP `unzip -p` 再读取四个精确 `jni/<abi>/libonnxruntime.so` 路径，同样核验通过。ZIP 共36条、AAR 共26条；没有解包到文件系统。机器材料见[成员收据](../../probes/sherpa-runtime-materials/2.0.1-ort-release-members-20261114/member-verification.json)和[独立读取收据](../../probes/sherpa-runtime-materials/2.0.1-ort-release-members-20261114/independent-unzip-verification.json)。

首次下载在300秒时限后以 curl 28结束，收到23,739,891 B，原错误和残片摘要保留。一次显式区间恢复返回 HTTP206、`bytes 23739891-33765714/33765715`，收到10,025,824 B；合并后完整摘要匹配，才进行 ZIP 读取。[恢复收据](../../probes/sherpa-runtime-materials/2.0.1-ort-release-members-20261114/download-range-recovery.json)。这不是模型运行或供应商额度重试。

免费 hy3 138.562秒返回 `revise`：指出脚本允许嵌套路径后缀匹配，以及成功字段依赖前置断言。原复审不改写为 accept。父端核实本次固定归档四条实际路径均为精确路径，独立 Info-ZIP 也按这些精确路径读取，全部通过；未关闭断言、未改脚本。仅接受本次固定归档的观测，不把脚本当通用路径校验库。[父裁定](../../probes/sherpa-runtime-materials/2.0.1-ort-release-members-20261114/parent-review-disposition.json)。

设备另有只读复核：三次短清单/型号查询同 transport271且成功，随后完整 preflight 首次 getprop 又报 `error: closed`。先前另有 transport124→128抖动及 `device not found`。这仍是 ADB 传输不稳定，不能归因为锁屏或调试授权；本次没有新 prepare、安装、模型传输或解码，新 run `w3-qwen-terminal-20261114-01` 未消费。103个既有 dirty/untracked 文件与161文件冻结守卫在成员核验后保持。

本项只证明当前固定发布 ZIP 与现有固定 AAR 的四个 ORT 成员相同，不证明 Sherpa 当时下载的历史字节、完整源码到 AAR 对应、其余 ABI 构建配置、递归静态组成、生产通知保留或最终发布接受。旧构建工件 ZIP 与发布 ZIP 属不同容器，仍不直接比较它们的归档摘要。完整真机质量、资源预算和生产余门继续；不重开 AiAi/麦克风，不降低门限。

完整原始证据：`.omx/artifacts/w3-ort-release-members-20261114/`；接受的公开参考包：`artifacts/verification/w3-ort-release-members-20261114/onnxruntime-android-1.28.2.zip`。验收后确认两份下载片段与完整包对应区间字节一致、未跟踪且无打开进程，再精确清理33,765,715 B可重建片段；完整包、原错误与片段摘要保留，[清理收据](../../probes/sherpa-runtime-materials/2.0.1-ort-release-members-20261114/task-cleanup.json)。本轮仅新增核验材料和文档，未改变生产代码、依赖、模型、APK或冻结协议。

# W3 ORT 四 ABI 构建日志与工件传递核对

补核此前未审阅的 armeabi-v7a、x86、x86_64 日志后，四个 ABI 均有同一源码 commit 和选定构建参数的直接日志证据，各自上传工件 ZIP 的摘要也与 collect 下载记录一致。此项补充运行时出处材料，W3/R4/R5 仍 open。

固定上游运行：[33892225094](https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094)，实际工作流头 `bb069649de9edc4689b2c5f9609e5499ae0bee9d`，collect job `101094996702`。沿用此前已核 arm64 原始日志，新取其余三个 job 日志；没有下载二进制、构建或执行模型。

| ABI | job | 源码 commit | API / NNAPI / LTO / mimalloc |
| --- | --- | --- | --- |
| arm64-v8a | 101086389995 | `33ca9628233dc8f002435e868d4c2e9f82766ca1` | 27 / ON / ON / OFF |
| armeabi-v7a | 101086389604 | 同上，逐日志核对 | 27 / ON / ON / OFF |
| x86 | 101086389863 | 同上，逐日志核对 | 27 / ON / ON / OFF |
| x86_64 | 101086389779 | 同上，逐日志核对 | 27 / ON / ON / OFF |

每份日志的源码行370、实际 build 参数行452和 CMake 命令行455均已记录完整原文与原始日志 SHA-256。NNAPI、LTO、mimalloc 取实际 CMake 行；没有由 arm64 参数推广其余 ABI。所见其他选项不证明在具体 CPU 上实际启用、执行或产生内存收益，亦未审完所有构建配置。

| ABI | 上传与 collect 下载相同的工件 ZIP SHA-256 |
| --- | --- |
| arm64-v8a | `4a63146d05fe87c3a6f9df6a03ee6a86a90e59da146ff9c98dd5559dc562a30a` |
| armeabi-v7a | `3bfd9d59018f4cdc43affe1205db5698a678c4111163293ead1ca6b2282fc199` |
| x86 | `ea012b742a5c2029fa08bdd8a7a2c488af89171711af5cc97739d286c808ee59` |
| x86_64 | `a184e6e97a86905df25a7e2f896f9251bfa1826755738f3bcbabf26a708fe1b4` |

这四个摘要分别比较同一 ABI 的工件 ZIP 上传/下载记录，不与组合发布 ZIP 的摘要直接比较。[上一项四个 SO 字节对应](w3-ort-release-members-20261114.md)独立保留。两项合起来补强当前发布成员及观测构建路径，仍不证明完整源码到 AAR 的加密对应、Sherpa 历史下载字节、递归最终静态组成、生产通知保留或最终发布接受。

首次三个 `gh api` 读取均 exit1、stdout0，stderr 明确为 `the response contains terminal escape sequences; pass --allow-escape-sequences to output it anyway`。保存原失败后，只对这三个已知公开日志使用该选项，并捕获到文件，未向终端渲染原始转义内容；三份完整日志分别为841,169 / 818,038 / 841,069 B，exit0、stderr0。不是远端 job 失败、额度错误或登录变更。

免费 hy3 在19.133秒返回限定复核 accept，仅依据供应的日志摘录，没有执行检查。父端另核20条原始行、四份日志摘要及源码哈希前一行确为 `git log -1 --format=%H`；没有由助手自报替代原始证据。[复核原文](../../probes/sherpa-runtime-materials/2.0.1-ort-all-abi-20261114/hy3-review.json)。验收后只清理已逐字核对、已归档且无打开进程的三份临时提示/输出副本，原日志和首错误全部保留。

当前真机 ADB 清单为空，新 terminal run 仍无 prepare 或消费；不重放旧 run，不把日志研究或模拟器结果当手机验收。原103个工作区文件及161文件冻结合同保持。完整近场英文/AMI/挑战手机质量、R5资源预算及生产余门继续。

公开机器材料：[四ABI日志摘录与摘要](../../probes/sherpa-runtime-materials/2.0.1-ort-all-abi-20261114/observations.json)。本地原始日志、读取失败和父核验保留于 `.omx/artifacts/w3-ort-all-abi-20261114/`；此前 arm64/collect 原日志保留于 `.omx/artifacts/w3-runtime-materials-20261111/ort-provenance-followup/`。

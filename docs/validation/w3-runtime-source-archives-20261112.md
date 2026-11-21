# W3 ORT 源码归档与许可证文本补核（2026-11-12）

本项补强固定 ORT arm64 构建依赖的来源证据。它只核对已观察到的 13 条源码依赖 Fetch 声明和归档字节，未下载 host `protoc`、运行时二进制或模型，也没有设备调用、推理、生产依赖修改或发布动作。

## 结果

- 13 条源码依赖与固定 `33ca9628233dc8f002435e868d4c2e9f82766ca1` 映射逐项绑定；11/13 个归档实际下载并以完整字节重算 SHA1，均与 `deps.txt` 声明一致。独立 SHA256、字节数和最终 codeload URL 已记录。
- `pytorch_cpuinfo` 和 `onnx` 的归档读取分别因 `TimeoutError` 终止；保留 partial、错误和终态，未自动重试或重复请求。两者只补充固定 commit 的 `LICENSE` Git blob（cpuinfo commit `4628dc060ce4e82345dc166bbac875609db4ff69`；ONNX tag `v1.22.0` 当前解析 commit `2bb50465112feca9003e1ed654d77f01ff1415ca`），明确标记 `archiveSHA1Verified=false`。这不是归档通过，也不证明历史 tag 不可变。
- 从 11 个已验证归档读取并逐字节保留 21 个根级许可证/作者文本；mp11 归档没有独立 LICENSE 文件，保留 README、主头文件和版本头文件中明确的 Boost Software License 1.0 声明作为 3 份补充源码证据；两个固定 commit blob 作为另外 2 份补充文本，共 26 个保留文本。
- 归档只在隔离证据目录中保存；脚本不解压整棵树、不执行源码，拒绝路径穿越、符号链接、过大文件和非 GitHub HTTPS 重定向。单归档上限 64 MiB，总归档上限 256 MiB。

## 独立验证

父端 `check_materials.py` 新鲜通过：13 项依赖映射、11 项归档 SHA1、30,858,504 B 已验证归档、26 份文本、原有 68 份运行时材料哈希、arena108/heap120/emulator327/KV288-448 冻结守卫均通过；物理 heap run 仍未消耗，设备/推理调用为 0。

机器入口：[source-notice-manifest.json](../../probes/sherpa-runtime-materials/2.0.1-ort-source-notices-20261111/source-notice-manifest.json)；完整原始收据位于 `.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives/`，包括两次超时、partial、下载进度、逐归档哈希、mp11/ONNX/cpuinfo blob 和父验证日志。

## 边界

本项仍不能证明历史 patch 实际执行、ORT 递归静态组件闭包、源码到固定 AAR 的逐字节对应、最终二进制成员归属、生产通知集成、法律审批或发布就绪。旧映射中的 `finalBinaryMembershipVerified=false`、`exhaustiveRecursiveCompositionVerified=false`、`sourceToAARVerified=false` 保持；`productionReady=false`、`w3Complete=false`。W3/R4/R5 继续开放，TECNO 真机、true peak/GPU/energy/生产预算和实体 heap 单片仍是独立门。

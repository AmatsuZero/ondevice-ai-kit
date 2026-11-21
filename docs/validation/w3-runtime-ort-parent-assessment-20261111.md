# ORT 嵌套构建出处：父验收与剩余边界

本项确认官方实际执行记录，W3、R4/R5 仍 open。免费 hy3 订正版原字节集成，6034 B，SHA-256 `a58c5d96cdf48df739702739e1b11c247301d7749c86989a01e7debcf5d110db`。首稿因工作流参数写反等事实错误拒收；没有将其用于验收结论。入口：[研究复核](w3-runtime-ort-build-observed-20261111.md)、[机器收据](w3-runtime-ort-build-observed-20261111.json)、[独立补充材料](../../probes/sherpa-runtime-materials/2.0.1-ort-observed-20261111/observations.json)。

实际 [执行头工作流](https://github.com/csukuangfj/onnxruntime-libs/blob/bb069649de9edc4689b2c5f9609e5499ae0bee9d/.github/workflows/android-shared.yaml) 与 [release tag 工作流](https://github.com/csukuangfj/onnxruntime-libs/blob/5cc3d2e84d9eade2562cf29a93fa3a520a75ca57/.github/workflows/android-shared.yaml) 不同。前者默认 1.28.2/test-ci/overwrite=true，后者默认 1.27.1/release-v1.27.1-2/overwrite=false；collect 日志的 true 与实际执行一致。

仅审阅 arm64 构建日志：checkout `33ca9628233dc8f002435e868d4c2e9f82766ca1`，实际 NNAPI/API27/LTO，CMake `onnxruntime_USE_MIMALLOC=OFF`。其余 ABI 只核 collect 的替换/大小，不推广完整 source/config。arm64 工件上传/下载 ZIP 摘要均 `4a63146d05fe87c3a6f9df6a03ee6a86a90e59da146ff9c98dd5559dc562a30a`；发布组合 ZIP 元数据摘要 `01518867f78241138b6aa25925802e843a4fa9085af8d303d49e35bbb52aff4d`。两种容器不同，摘要不同没有矛盾。

collect 可见 Maven 1.26.0 AAR 回退并覆盖四 ABI 的 ORT .so，再上传 release ZIP；四个成员大小与现有 Sherpa AAR 记录相符。这只提供构建/归集路径印证，尚未核 release ZIP 的逐成员摘要、Sherpa 当时实际下载的历史字节或源码到 exact AAR 的完整对应。所见两条 sed 删除版本属性不证明最终包没有其他补丁。HF 首次推送被拒、重试因源已移动而失败后空提交；job success 不能证明 HF 上传成功。此次 HF 查询仅列 .gitattributes。

Sherpa attestation 接口 404 及工作流仓库 LICENSE 路径 404 均只代表具体查询结果；不能推出不存在 attestation、签名或许可。递归静态组成、完整二进制出处、生产通知保留和独立发布复审仍未通过。构建参数不能证明当前手机实际分配器或内存根因，不能据此替换现有 runtime 或放宽资源门。

父验收实际核了 8 份只读 context 摘要、OS 写范围预检、完整 result/工具事件、2 GitBlob/50 日志摘录、release 元数据和四成员大小；原 50 材料/15 补充、arena 108/heap 120 冻结摘要及结构守卫保持。工具实际 7 Read/1 Write，1 次错误是候选创建前 Read File does not exist；最终自报误指 original-rejected.md，已按 tool ID 纠正。外层退出 0 和助手自报体量均不作为验收依据。父脚本旧 dict 比较错误和本次文字断言误写均保留说明，修正后全部通过，未伪称材料漂移。

本轮只新增文本补充与研究文档；没有重跑模型、Android 构建或完整 tools 测试。设备只读清单仍空，未做安装/推理/下载；heap 唯一 run `w3-qwen-heap-20261111-01` 未消耗，仍无 prepared/run-started/collection。连接恢复后按原冻结合同 fresh preflight→prepare→唯一诊断→同 run 收取/评分/恢复，固定资源门和原 App/模型保持。

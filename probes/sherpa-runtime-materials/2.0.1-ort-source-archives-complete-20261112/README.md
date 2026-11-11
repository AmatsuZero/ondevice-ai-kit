# ORT 源码归档证据叠加层

## 叠加定位
目录 `probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/` 为基座 `../2.0.1-ort-source-notices-20261111/` 的增量层（基座清单 SHA256 `679cae84...f254` 不可变）。固定 ORT 提交 `33ca9628233dc8f002435e868d4c2e9f82766ca1`。

## 增量历史
- 原 11 归档（30,858,504 字节）完整保留，历史失败/残片未改。
- 独立恢复 cpuinfo（3,857,758 字节）、onnx（18,911,779 字节）：各一次直连 codeload GET，仅调用级 no-proxy 传输，无自动重试、无全局代理/配置变更。
- 合并 13 归档共 53,628,041 字节；13 声明 SHA1 均匹配，独立校验 SHA256 与尺寸。Linux 主机 protoc 排除（未下载，非运行时库）。
- 本层仅新增 3 文本：cpuinfo LICENSE、onnx LICENSE、onnx NOTICE；其余仍存基座。基座 2 既有 GitBlob LICENSE 字节等于新全量 LICENSE，不重复计入。

## 声明计数
27 归档支撑文本 = 24 root/root-LICENSES 文本 + 3 mp11 源码声明。mp11（boost-1.82.0）无独立 LICENSE 文件，保留 3 处真实 README/头声明，不虚构。

## 复检入口
`complete-manifest.json` 映射全部 13 归档。基础仓库根执行：
`rtk proxy python3 probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/check_complete.py`
先复检不可变前 11/13 包，再校验合并 13/13、声明、原 68 物料文件及 108/120/327/448 冻结守卫。原始 ZIP 为本地忽略证据（非提交），位于 `.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives/` 与同级 `ort-source-archives-recovery-20261112/`；复验需就位。

## 限制
仅完成归档完整性，非生产/发布验收。源码声明不证明：最终 AAR 二进制归属、递归组合、源码到精确 AAR 同一性、生产声明集成、历史补丁执行、发布就绪或 W3/R4/R5 验收。W3/R4/R5 仍开放。无麦克风/AiAi/设备推理/模型/运行时下载/依赖变更/物理堆运行。

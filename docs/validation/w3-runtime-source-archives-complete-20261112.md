# W3 ORT 源码归档完整性补充（2026-11-12）

本项是 `docs/validation/w3-runtime-source-archives-20261112.md` 的增量证据，不覆盖原始超时、partial 或 11/13 归档历史。固定 ORT 源提交仍为 `33ca9628233dc8f002435e868d4c2e9f82766ca1`；基座清单 SHA256 为 `679cae84ea146460162434c6d3fa8d3bd44fc3a6c9e2e17b2b7a1b048035f254`。

## 受限恢复

在确认原 partial、失败终态和全局代理状态后，分别对 `pytorch/cpuinfo` 固定 commit 和 `onnx/v1.22.0` 发起一次独立 codeload GET。两次都使用调用级 `ProxyHandler({})`，HTTP 200，分别得到 3,857,758B 和 18,911,779B，并与声明 SHA1 匹配；没有自动重试、没有改全局代理或配置。该结果证明本次直连传输有效，不把所有历史超时唯一归因于代理。

恢复证据位于 `.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives-recovery-20261112/`，包括固定 URL、状态、字节数、SHA1/SHA256、耗时和收据。原 `ort-source-archives/` 中的 3,145,728B cpuinfo partial 与 `unattempted/downloads/` 中的 6,291,456B onnx partial 均保留且独立 SHA256 重核通过。

## 合并材料与父验收

新增目录 `probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/` 只提交 manifest、校验脚本及三份新增通知文本；ZIP 归档仍为本地忽略证据。合并后的 13/13 源码归档均满足固定声明 SHA1，独立 SHA256 与文件尺寸也匹配，总计 53,628,041B。归档支撑文本共 27 份：24 份 root/root-LICENSES 文本与 3 份 mp11 包内 README/头文件声明。mp11 没有独立 LICENSE 文件，因此没有补造文件。

新鲜执行：

```text
rtk proxy python3 probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/check_complete.py
```

结果为 `acceptedWithinStatedScope=true`、`verifiedSourceArchives=13`、`archiveBackedTextFiles=27`、`oldMaterialFilesUnchanged=68`；108/120/327/448 冻结守卫均通过，新增设备/推理调用为 0，物理 heap run 未消耗。校验脚本先重跑不可变的旧 11/13 材料检查，再重核合并的 13/13 归档和 27 份文本。

## 边界

这只关闭源码归档的可核验性缺口，不证明历史 patch 执行、递归最终二进制组成、源码到精确 AAR 的同一性、最终 AAR 成员归属、生产通知集成或发布就绪。W3、R4、R5 仍为 open；TECNO 真机完整质量/资源、true peak、GPU、energy 和生产验收仍未由模拟器或本项替代。

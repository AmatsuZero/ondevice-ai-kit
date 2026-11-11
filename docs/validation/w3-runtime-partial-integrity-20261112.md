# W3 历史 partial 的可重复摘要验收（2026-11-12）

本项修正完整归档校验器的一个证据保护缺口，不改变任何识别报告或归档原件。此前父端另核过两个旧 partial 的 SHA256，但 `check_complete.py` 自身只检查 3,145,728B / 6,291,456B 的长度，不能独立拒绝同长度的内容替换。

回归将完整归档作为只读 hardlink，在独立临时目录复制两份 partial，仅翻转目标 partial 的第一个字节并保持尺寸不变。旧脚本对两种篡改都没有抛错，真实得到 `FAILED (failures=2)`，原始证据正向检查通过。随后校验器保留原尺寸检查，并加入两份固定 SHA256；同一回归 3/3 通过。

从 foundation 根执行：

```text
rtk proxy python3 probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/test_check_complete.py
rtk proxy python3 probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/check_complete.py
```

两个保留摘要分别为 cpuinfo `088ecd3fad94789f753c8f2abccb4710c78a79bca03fe5aef28b43d8d9327386`、onnx `3d34ecc291ac74f88bb0dd9003ed4b2c5bbddeb5fb84448da936542ee8372294`。完整归档仍为 13/13、53,628,041B、27份归档支持文本；旧68材料和108/120/327/448守卫保持，物理heap run未消耗，新增网络/设备/推理调用0。

本项不证明 source-to-AAR、递归二进制组成、生产通知或发布就绪；W3/R4/R5仍open。原始失败、负向回归与验收收据保留在 `.omx/artifacts/w3-runtime-materials-20261111/partial-integrity-20261112/`。

免费hy3对提供的完整脚本与回归源码进行只读复核，79.445s终态接受，无阻塞发现；路径常量重复及内部未知key测试为非阻塞建议。该复核只覆盖本次父验收脚本和测试，不作为运行时、完整再分发或发布的独立批准。

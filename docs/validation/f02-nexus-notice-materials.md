# Nexus notice材料字节保留核对 — 2026-11-07

新增`tools/nexus_notice_materials.py`，以调用方提供的冻结reference index原始bytes、外部SHA256及
candidate version核对实际collector目录。它核对index本身与全部索引文件，复用GET核对器的
逐层dirfd/nofollow/nonblocking读取、常规文件/大小/streamed hash及读取前后FD元数据检查。
不扫描/认证未索引额外文件；不提供整个目录在并发写入下的原子快照保证。

Copilot个人额度40.77秒完整返回候选，父先保留6项缺模块RED，再审查/集成。
父原提示误把真实`.6`的七字段index规定为两字段；实际74材料首次失败已保留。
补schema回归后接受冻结reference的provenance metadata，严格可选`schemaVersion:1`，
并始终绑定外部SHA/版本/index及全部材料字节。没有删除或重写历史metadata来迎合工具。
final symlink负测修正为存在且字节匹配的目标，避免只是证明缺文件拒绝。

32聚焦检查通过；实际当前`.6`索引SHA为
`1c21ec59da4e2605622699cdbc3699695f657fb486d7595d01941aaeec69bbe1`，
73个索引材料加index合计**74材料**通过。仅读取既有collector，不重新构建或运行远端消费者。
完整tools **545/545、56.503秒**通过；所有tools源码、九保护路径与生产Apple lock保持。
首次受限545测试的三个Swift缓存权限错误保留，正常主机复验通过，未为此修改源码。

```python
from pathlib import Path
from tools.nexus_notice_materials import validate_materials
result = validate_materials(reference_bytes, frozen_sha256,
                            "0.144.6-ondevice-f02.6", Path(collected_root))
```

输出`validationStatus=pass-bounded`；`legalApproval`、`remoteConsumerValidated`与
`networkPerformedByThisTool`恒为false。该工具证明与冻结reference的保留一致性，
不证明材料许可完整/适用、真实网络来源、Gradle variant解析/编译或发布。
实际剩余门槛见[Nexus消费者清单](f02-nexus-consumer-checklist.md)。
证据在`artifacts/verification/f02-nexus-notice-materials-20261107/`；
原候选、RED与父处置在`.omx/artifacts/copilot-nexus-notice-materials-20261107/`。
最终限定源码复核状态以[助手台账](agent-task-ledger-20261107.md)为准，不计完整分支批准。

最终Copilot限定源码审查25.75秒完整返回、0工具/0finding；父核对四份输入摘要保持。
不替代独立完整分支或许可批准，证据`.omx/artifacts/copilot-nexus-notice-final-review-20261107/`。

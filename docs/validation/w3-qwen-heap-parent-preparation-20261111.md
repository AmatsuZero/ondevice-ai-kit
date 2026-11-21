# W3 heap 父端报告验收准备

新增只读父端 [verify_report.py](../../probes/android-qwen-heap/verify_report.py) 与 [check_parent.py](../../probes/android-qwen-heap/check_parent.py)。它们不实现 Android 探针、不触发设备操作；当前外部 wrapper 仍因免费 hy4free 的 429 零交付而缺失。**父端离线准备有进展，W3 仍 open，实包、独立复核和设备门均未通过。**

父端只允许原未执行的 index25 `fleurs-en_us-1776` 一项，核原 arena 报告精确 SHA 及 planned/null 状态；核新 run/source、65 输入清单、六模型、arena-off 政策、最多1构造/2stream/2decode和真实释放/teardown。原1.5GiB RSS、512MiB available、120s 门保持。原 arena/FILE 的源码、实包、报告及已消耗运行不改、不重放。

heapEvents 要求有界、顺序和整数类型正确，失败 telemetry 阻止通过；0 是合法值，三项非原子读取不要求相加恒等。边界标签须由后续实际源码复核确定并写入冻结合同，不能从任意非空 label 推断采样覆盖。单片诊断即使通过，也始终输出 `engineeringQualityAccepted=false`、`productionReady=false`、`nativePolicyEffectVerified=false`、`truePeakMeasured=false`。

收取入口核报告双读、政策与报告 SHA 绑定、六个精确模型名、关键冻结文件、原未执行输入和固定 run-plan；从原始 RSS JSONL 重算 count/max/min available，并绑定 PID/startTicks。旧 host 摘要不含 min available，不能用默认值补成通过。保留旧 guard 对 `/proc/uptime` 取整的 `windowAgeSeconds >= -1` 容差。此准备只校验 heap 事件顺序和 RSS 采样摘要，**尚未建立两个时钟间的精确对齐**，后续新设备工具仍须实现并验收。

验证结果：新父端21项、原arena父端22项、原FILE父端8项、原speech metrics31项，共82项通过。新测试使用合成报告和占位 APK/source 文件，只证明父端拒收行为；不证明真实包有效、手机成功或独立复核通过。集成测试同时读取原真实报告来核所选项未执行，未调用模型或设备。

保留的 RED：缺 evaluator 时12失败；扩展加载时间/类型/观测中断后7失败；收取合同与真实 trace 绑定前7失败；uptime 容差/极大数异常前1失败1错误；遗漏传递 evaluator 必需摘要时1失败。各修正后的完整21项通过，原失败日志保留。本次只修改父验收脚本和文档，主代理没有代写外部 wrapper。

原始日志、父端裁定、源码摘要和保护检查见本机 `.omx/artifacts/w3-qwen-ort-lifecycle-20261111/heap-parent-preparation/`。本轮仅自审；原 native 429 不重试，新免费 helper 仍等待此前接续选择。下一步取得外部候选后，先对齐实际标签和收据接口并通过真实 Kotlin host 检查，再构建/核包/独立复核；此文不能替代冻结设备合同。

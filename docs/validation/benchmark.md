# Benchmark protocol v1

人脸：固定输入 SHA256，至少5次预热+30次测量；冷启动单独记录。输出最近秩法 p50/p95 与样本数，未测阶段为空数组。系统模型 revision=null；禁止把下载或桥接往返误标成纯模型加载/推理时间。

按设备、OS、SDK commit、能力、实际后端、模型revision、预处理、输入、计时方法、证据类型和内存测量方法分组。跨组不合并分数。质量保留逐 run 人工可复核字段；不同能力不输出总分。

当前 schema v1 能耗固定 null/unmeasured，后续有可靠仪器后另行扩展 schema；热状态可记录原始平台标签。峰值内存没有可靠采集时为null，当前 resident size 不冒充峰值。

导出后：`python3 tools/benchmark_report.py run.json --output artifacts/report.json`。输入和设备原始报告默认仅留本地，不提交照片或设备唯一标识。

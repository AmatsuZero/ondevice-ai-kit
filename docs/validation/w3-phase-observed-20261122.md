# W3 实测：两次解码的原生堆最高读数均位于 prefill

2026-11-22，在一台独立 Android API 37、arm64、16 KiB 页的模拟器上，用带阶段标记的提前释放候选识别同一条 9.91 秒合成英语。先自动识别语言，再强制英语，两次转写均与参考句逐字相同。强制英语再次采到较解码入口基线高 **317.86 MiB** 的读数，完整读取区间位于处理整段输入的 `prefill_forward` 内，距该调用返回约 13.61 ms。自动语言的最高读数也在该阶段内。[冻结合同](../../probes/android-qwen-prefill-release/phases/run-contract.json) · [可离线复核的原始记录](w3-phase-observed-20261122.zip)

> When the morning train arrived at the quiet station, the conductor opened the doors and waited for the last passenger to step aboard.

| 解码模式 | prefill 起止（距解码入口，ms） | 最高样本的读取区间（ms） | 原生堆最高增量 | 次高增量及并列数 | 调用内样本 / 保守最大间隔 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 自动语言 | 669.918–1,092.019 | 1,081.602–1,081.606 | 314.18 MiB（1 笔） | 232.05 MiB（7 笔） | 176 / 18.46 ms |
| 强制英语 | 694.485–1,309.024 | 1,295.408–1,295.412 | 317.86 MiB（1 笔） | 234.05 MiB（11 笔） | 261 / 22.30 ms |

两次各自的堆基线为 1,031.20 和 1,031.66 MiB。表中次高按原始字节数排序，两阶段并列次高的读数也都落在 prefill 内。上轮无阶段标记的候选在进入强制英语解码 723.67 ms 时采到 317.86 MiB。本次复现了相同量级；阶段归属只针对本次两条有标记的读取区间。

## 阶段覆盖与进程内存

每次解码均收齐同一线程的 18 个原生时间标记。一笔读取从开始到结束都严格落在阶段两端之内才计入该阶段。下表使用调用内样本，归档中的 `analysis.json` 保留全部阶段的计数和最高字节数。

| 调用内样本归属 | 自动语言 | 强制英语 |
| --- | ---: | ---: |
| 音频卷积 / 编码器 | 43 / 19 | 35 / 29 |
| 创建空 KV | 1 | 0 |
| prefill 前向 | 41 | 57 |
| 首次逐 token 前向 | 4 | 6 |
| 其余具名阶段（含 KV 更新、首 token 选择、logits 释放） | 0 | 0 |
| 未归入具名阶段 | 68 | 134 |

未归类样本中，分别有 67 和 133 笔在最后一个阶段标记之后。两次最高样本严格位于 prefill 返回前，采样精度仍无法分辨其内部算子、短暂分配或真实峰值。解码耗时字段 `decodeNanos` 分别为 1,854.14 和 2,898.43 ms。

整进程共采样 31 次，RSS 最高 **1,306.92 MiB**，设备可用内存最低 **896.39 MiB**。启动前可用内存为 1,704.52 MiB，27 次常规轮询的相邻间隔为 404–486 ms。没有采到触发停止条件的读数（RSS 高于 1,536 MiB 或设备可用内存低于 512 MiB）。RSS 是进程级采样，时间粒度与调用内堆采样不同。

## 原件与复核

运行号 `w3-station-phases-20261122-01` 已消费一次。一次构造、两次解码、两条 stream 及识别器释放均完成。六份模型和两份已安装 APK 的 SHA-256 匹配合同，终态双读一致，ACK 已收取且 instrumentation 成功结束。两个诊断包均已卸载，临时模拟器已关闭，前后 ADB 清单逐字节相同。归档保存了清单摘要及差集，原清单保留在本地运行目录。

ZIP 共 69 项，SHA-256 为 `feeb3c7577ce7ffcb7f86b2a64f7aa14c375d414d1a1b09a9924a5e4ef6756c3`。解压后在归档根目录运行：

```sh
shasum -a 256 -c SHA256SUMS
python3 -B probes/android-qwen-prefill-release/phases/analyze_phase_capture.py \
  run-contract.json observation/host observation/captured observation/phase.log
```

已从解压副本复核全部摘要，重算的窗口、阶段统计、进程采样和逐字比对均与留存结果一致。`ranked-samples.json` 列出各模式全部并列最高、次高样本及其纳秒级读取区间。

本轮覆盖一条合成英语。TECNO 真机原批次在完成 65 条中的 24 条后触发内存停止条件。[真机原批次](w3-qwen-arena-observed-20261111.md)

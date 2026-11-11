# W3 TECNO CPU arena-off 文件诊断结果

唯一 run `w3-qwen-arena-20261111-01` 完成 **24/65** 项，在第 25 项期间采样 RSS 超过固定 1.5 GiB 后停止。中文 20 项 CER **40/590=6.78%**；完整执行、内存与全量质量验收仍未通过，W3 保持 open。旧默认 FILE 失败及其原件保留，没有重跑或提高资源门槛。

| 项目 | 实际证据 |
| --- | --- |
| 身份与输入 | TECNO KM9/API35/arm64/currentUser0；提交前 Awake/unlocked，available 3173683200B；原 65 份完整原幅度 PCM，固定顺序及两阶段配置不变 |
| 新配置 | 精确 20B `EnableCpuMemArena=0\n`；请求 provider 绑定 framework canonical 路径，未改变默认服务、生产包或模型 |
| 构造 | 1 次，成功返回，load 9.075710385s |
| 父监控 | 258.937074625s，413 个 RSS 样本；sampled max **1637896192B=1.525410GiB**，超过 1610612736B 门 27283456B；系统 available 最低 1864785920B；无观察断点 |
| 覆盖 | 24 行完整且阶段一致，1 行中断 running，40 行 planned；未执行行不评分 |
| 中文 | 20/20 非空，CER **40/590=6.78%**，低于事前 20% 阈值；只证明该 cohort 的转写指标满足门槛 |
| 近场英文 | 4/20 非空完成，子集 WER **9/67=13.43%**；覆盖不足，不能判全量通过 |
| 远场与挑战 | AMI 0/12，annotation-negative 0/9，静音 0/4；全部未执行，不能引用 Mac 结果填充手机验收 |
| 完整两阶段成本 | 完成子集每片 7.251810078–12.209642847s，RTF 1.040806–1.678660；包括持久化/门检查，不是 native-only、warm 或持续预算 |
| 报告 | 同 run 两次原字节读取与设备 SHA 一致：`3a5d9c4c13f91768b073fc98591b4bc88ecd5df7ab82c22bc1aa188d89d7140d` |
| 恢复 | 六 Qwen 文件与普通 App/原模型后核通过；只卸本次两个 alias、删除清单绑定的 staging；原 PID、instrumentation 与 alias 已不存在，普通 Demo 实际恢复前台 |

强停后的报告非终态。最后持久化 decode49/stream50 是观察值，**实际最终调用数未知**；没有显式 recognizer release 或 Activity destroy ack，不能用 force-stop 代替。24 个已完成片段的两个阶段各有 release 成功记录，但整 recognizer 与当前中断片段的清理不能据此补记成功。RSS 为采样最大值，没有测 true peak、GPU 或能耗。

新配置让批次从旧默认的 0 个完成项推进到 24 个，但两次停止发生于不同进度，不能把两个停止时的 RSS 差作为受控节省量，也不能据较低采样值证明 ORT 内部设置已消费。报告和父裁定均保持 `nativePolicyEffectVerified=false`。本次已消耗，不自动重放或扫参。

## 同 run 政策补核与原失败保留

冻结父收取器将 shell `readlink -f no_backup` 的 `/data/user/0/...` 与 framework canonical 的 `/data/data/...` 字面值比较，原结果因此为 receipt/file false。首次补核也错误要求两种绝对路径的 shell 解析值相同，断言在写新收据前失败；诊断原件保留，不能据此说配置文件不存在或 native 未消费。

后续只读补核分别访问 relative、framework 和 shell 三个精确路径：目录具有相同 device/inode，配置文件也具有相同 device/inode；各配置文件路径非软链，最多读取 21B，实际两读均为同一精确 20B，并与设备全文件 SHA、报告 receipt 和实际 provider 相符。五个错 inode/错路径变异均拒绝。原失败评分、原收据、108 项冻结文件和两 APK 不改；补充收据与新评分另存，没有安装或新模型调用。

补核仅排除了父端路径字面值误判；新评分仍因 terminal、host、release、teardown、rowsComplete、rssSamples 六项失败而拒绝完整验收。生产通知保留、运行时传递性许可、独立发布复审和剩余 W3/W8 资源项继续独立，不因本子集通过而关闭。

[机器收据](w3-qwen-arena-observed-20261111.json)包含 24 对公开 reference/hypothesis、耗时与证据摘要，便于复算。冻结合同见[诊断协议](w3-qwen-arena-diagnostic-protocol-20261111.md)；本机全部原件位于 `artifacts/verification/w3-qwen-arena-wrapper-20261111/`，补核父脚本位于 `.omx/artifacts/w3-qwen-arena-wrapper-20261111/collect-framework-policy.py`。本机目录不保证随 Git 分发。

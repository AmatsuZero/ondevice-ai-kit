本次模拟器诊断已完成并通过父端20项检查（全部 20 项检查值均为 true）。

# W3 Qwen Heap 模拟器实测观察（2026-11-11）

## 结论（先行）

本观察仅记录**模拟器侧**已完成的一次有界验证，不等于 TECNO 真机验收、不等于工程质量接受、也不等于发布接受。W3 / R4 / R5 状态仍为 open；发布未就绪。

## 范围与环境

- 现有 Android36 / default / arm64 revision2 基础镜像。
- 隔离 AVD：W3_Qwen_Heap_20261111，序列 emulator-5578，4GiB / 2CPU，无音频、无相机、无快照。
- 无模型或二进制下载；沿用同一 APK / 源码 / 模型，原始预算未变。
- 真机批次 w3-qwen-heap-20261111-01 保持未消耗（UNCONSUMED），120 个冻结文件完好。
- 新增运行仅 w3-qwen-heap-emulator-20261111-01：固定 index25 fleurs-en_us-1776 / 4.8s，1 构造器 / 2 流 / 2 解码，无麦克风、无 AiAi、无网络请求。
- 新增 841 文件补充冻结。

## 校验集合与独立复核

- 四组适配器：9 项身份否定 + 72 项继承父端检查。
- 新全量工具套件亦通过：691 项测试、无失败，Ruby3.4.11 仅调用（invocation-only）环境；该证据独立于上述 4 组适配器 / 9 项身份否定 / 72 项继承父端检查，不可与 Android 构建或全新 native 验证混为一谈。
- hy3 独立复核接受；一项次要项延后：继承全局在 exec 后更新；今日接受，不做加固。
- 源码指纹 a79adaa583e3707be20055e2fc6dd01f6ca58a4da5da686cf21196666d0ce7e1。
- 合同 SHA dcd11f4643048f6baa42e16d6d58c441acf5b02c4eb4163b572832b7d24292fc。

## 采集与一次 adb 失败（保留）

- 实际采集首遇 adb 远程 shell 参数拆分问题：stat 格式 '%u %d %i %s'。
- 保留该失败：未加引号的真实读取 exit1，加引号的真实读取 exit0，确认根因。
- 同一运行的 READ-ONLY 补充脚本 collect_policy_quoted.py 对 shell token 加引号；原始冻结父端 / APK / 报告未变，无 native 重放。
- 同 UID / 设备 / inode，有界 21B 读取精确 20B / SHA / receipt 通过。
- 双读、六模型后 SHA、alias + staging 精确恢复、Launcher、精确关闭 AVD 均通过。

## 边界内存解读（仅边界读数与解释）

- 构造器返回后分配约 1027.171MiB。
- 采样最大 RSS 1.353702545GiB；设备可用最小 1535139840B；23 个 RSS 样本；无 stop、无 terminal gap。
- 识别器释放后分配 22.842MiB；Activity 拆除后 22.440MiB。
- 以上为三个非原子 Debug 读数、18 个边界事件，无求和不变式，不能推出连续 native 峰值。
- uptime 间隔容差 ±1 秒；preciseClockAlignmentVerified=false、nativePolicyEffectVerified=false。
- 观测到构造期间大块分配、释放后下降；不能证明泄漏 / 无泄漏、ORT 内部归因、策略节省或 TECNO 成因。
- 无基线新加载重放。完整 W3 / R4 / R5 仍 open；物理堆、完整 65 项质量 / 手机内存 / RTF、运行时精确 AAR 溯源 / 组成 / 生产告知 / 独立发布复核仍分别 open。

## 质量（同一文件 auto 与强制 English 不同）

- 严格沿用现有 NFC + casefold + 空白（保留标点 / 数字）归一：
- auto：词 4/15 = 26.67%，字 9/75 = 12%。
- English：词 8/15 = 53.33%，字 27/75 = 36%。
- 差异含 Through→For、数字 150/200→one fifty/two hundred、标点等。
- 不改归一规则，也不以单文件作为语料定论或自动算法变更。
- 仅模拟器 DecodeRTF：auto 0.2280 / 强制 0.2048，非真机 warm RTF。
- 总计：case 2.161738668s，load 1.930013126s，host 6.448792709s。

## 产物与同步状态

- 根目录 artifacts/verification/w3-qwen-heap-emulator-20261111/，全部原材料本地忽略，不假定经 Git 发布。
- 报告 SHA256：3831a2cae37af09554fb76da089af4a5bd77fe6110280ef7dd3a44f15234ffc2。
- 机器可读摘要见 [w3-qwen-heap-emulator-observed-20261111.json](w3-qwen-heap-emulator-observed-20261111.json)（同 docs/validation 目录）。
- 起草时尚处同步待定（synchronization pending at drafting），不就此断言后续动作。

## 阻塞与范围变更

- 单片未验证完整质量；W3 仍 open，未新增阻塞，但不可据此判定无遗留问题。
- 范围限定为模拟器有界验证，不含真机 / TECNO / 发布验收，不含 native 根因证明。

# W3 本地终态文件原语验证（2026-11-12）

隔离目录 `probes/android-terminal-observation/` 新增本地有界稳定读取与
staging/fsync/无覆盖原子发布原语。新鲜 Python 套件 **67/67** 通过：45 项文件
检查与原22项纯校验；语法检查通过。旧120冻结文件、原实体heap报告与false verdict
摘要保持。本项没有设备、模型、麦克风、AiAi或下载操作，W3/R4/R5仍open。

## 实际改动与证据

- `terminal_file_io.py`：保留目录FD链，读取后核对命名root/父目录和文件inode；
  exact int/str/bytes、非空/1MiB上限、nofollow与ns元数据检查。写入完整暂存内容、
  fsync文件后无覆盖发布，最后fsync目录，处理短写与零进度失败。
- `test_terminal_file_io.py`：外部候选32项；`test_file_io_parent_acceptance.py`：
  主代理13项独立验收，包括真实root/父目录置换、发布碰撞、短读、错误清理及
  发布后故障。源码由免费hy3候选及精确单块候选补丁集成，主代理未替代实现。
- 本机原件、每次失败、审查与检查输出在
  `.omx/artifacts/w3-terminal-observation-20261112/io-*/`；可提交摘要见
  [机器收据](w3-terminal-file-primitives-20261112.json)。本机忽略材料不是远端可取归档。

先前30 skipped的测试候选拒收；直接导入后真实missing-feature RED。初始源码
31项有1 error（父目录symlink的ENOTDIR分类），修正后32项绿色，父端11项仍6fail：
子类类型、root symlink祖先与root/父目录置换。该6fail在修正后转绿；中途候选漏
staging循环break，43项4fail/21error/EMFILE，原件拒收。随后一次完整修正只返回
伪Read标记，零源码；独立最小补丁任务交付实际break，精确集成后65项通过。
再补两条发布后错误路径，最终67项通过。没有调整全局fd限制、重试配额或重放旧run。

## 独立复核与父裁定

免费hy3初审372.345s将命名链/root别名缺口评为low；父端真实6fail证明其违反
明确合同，未接受该评级。最终源码复核227.484s确认目录链与循环修复，另提四项：

1. 发布后目录fsync错误为medium：实测会抛OSError且完整target可见。payload未丢失；
   当前没有自动重试调用方。保留通用文件系统异常，在契约中要求未来协调器遇错即停、
   保留同次证据、不得重试。此为本地组件裁定，不代表真实driver已满足该门。
2. macOS `/tmp`别名拒绝为low：这是既定canonical-root输入合同；用真实`/private/tmp`
   fixture，保持拒绝，不放宽任意symlink祖先。
3. 发布后staging unlink失败为low：实测抛原错误，完整target及自有staging hardlink
   可留下；明确此失败边界，不广泛清理、不冒充持久化或设备成功。
4. 审查称max+1超限分支不可达，父实际增长100→150B、请求/读到101B并进入该拒绝分支，
   已否证；保留原审查与实测收据，源码不删此检查。

最终源码SHA256 `f117ab1dff49e9f256aa64df38b890c169bafde4e24fac227d52c3a9944ea8b4`。
审查后的新增仅为两项父端故障测试与文档，非第二次完整独立复审或发布批准。

## 可复核入口与未完成门

```sh
rtk proxy python3 -B -m unittest discover -s probes/android-terminal-observation -p 'test_*.py' -v
```

本地私有单写者范围不提供恶意同UID并发下的原子compare-and-unlink保证；未做断电、
跨文件系统或Android文件边界验收。实际host协调器仍须取得并记录同PID/startTicks的
before/read1/read2/after，先持久化报告和观察收据再发布ACK；新Kotlin wrapper、Android
build/lint/实包/复审、新冻结合同及真机质量资源仍未完成。旧heap run保持已消耗和19/20，
不能用本项清空terminalObservationGap或关闭W3。

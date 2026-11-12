# W3 外层设备冻结守卫验证（2026-11-12）

免费hy3候选和精确补丁已集成为 `terminal_device_contract.py`。仓库内独立守卫 **13 方法/1.808s**、既有终态观察 **194 方法/25.637s** 均通过，共207个方法；35个Python文件AST、旧120冻结/原失败报告及false verdict、95其他dirty路径、81准备源和两APK摘要均通过。**这是纯本地守卫，完整outer生命周期、新真实冻结和同run设备验证尚未完成；W3/R4/R5仍open。**

守卫在未来调用者执行ADB前验证固定run/alias/runner/sourceFP、严格类型预算/一文件计划、必需源码及每个copied-source直接冻结摘要、根源/打包副本、三份host收据、包收据、生产保护清单和两个当前Python-driver复核收据。JSON最多读取4MiB+1后拒绝超限，拒绝重复key/NaN/Infinity/指数溢出、非规范路径和观测到的符号链接（包括合同本身）。APK只核对既有验包收据与实际文件摘要/字节数，不重新解析或声称新的验包。

首完整候选17462B自然461.405s交付，首10方法0FAIL/122ERROR（含子例）均受json.loads错误allow_nan参数遮挡。最小API复现确认该选项只适用于编码；仅在测试内临时替换坏decoder做故障隔离，又真实发现合成保护文件名被硬编码及顶层合同symlink被接受。候选原字节未改、未集成，原结果保留。外部5组exactcount补丁修复decoder/有界读/保护动态清单/合同入口，完整12方法GREEN。

独立supplied-source复核原结论为revise。父端采纳一项固定包合同加强：当前已验包记录必须保留19个唯一非空字符串类名和三类必需子集；3类、重复19、含非字符串19、20类四个实际RED后，外部单块精确补丁使完整13方法GREEN。其他意见通过实际UnicodeDecodeError继承ValueError、包qualified class与完整组件名一致、11个Python源码的明确复核范围及producer真实fingerprint算法裁定。最终父端验收accept，保留原revise，不宣称外部重新复核最终字节。

低优先级暂缓：正确runner类名字面量与完整组件重复声明；非法中间目录保留原NotADirectoryError/OSError。二者不产生误接受或ADB调用。前者未来改身份时须同步声明；后者仅影响无效目录的错误呈现。此守卫依赖私有不可变输入、单写者快照，不声称抵御恶意同UID并发文件置换。

独立夹具使用81份真实准备源、65条输入清单、合成小APK和未执行的未来driver占位材料；它不证明真实freeze、外部模型字节、当前设备身份/解锁、安装、启动、收取或部分准备失败恢复。未来完整driver必须在每次实际ADB前调用守卫；其实现与生命周期故障验收仍待完成。当前ADB清单为空，无新安装/推理/麦克风/AiAi/模型下载，旧consumed run保持拒绝且不重放，源码未push。

机器摘要见[同名JSON](w3-terminal-device-contract-20261112.json)。原提示/完整CLI流/源码/失败/精确补丁/源码复核与裁定保留在 `.omx/artifacts/w3-terminal-observation-20261112/outer-freeze-*` 及本机唯一helper任务目录。

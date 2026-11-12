# W3 发布后错误信号修正（2026-11-12）

最终源码复核提出的medium已进一步在代码中解决：`PublishedButNotDurableError`
继承`OSError`，明确`published=True`、`durability_verified=False`，保留原错误cause。
发布后的staging unlink失败另置`staging_may_remain=True`。发布前错误仍为原普通
文件系统错误，不能把任何异常解释为target不存在、自动重试或发布持久化成功。

主代理两条类型回归先因缺类而真实2error，免费hy3完整候选请求只返回伪Read而拒收；
随后独立最小块任务31.090s交付异常类和精确替换块，主代理仅集成该候选。新鲜完整
Python **67/67**、语法检查通过。原文件流程、无覆盖发布、fsync顺序及旧冻结材料不改。
源码SHA256 `2c9e885c85a0b0d6623b5285cd88075abd93ee8111c04088eb30ed82fc9304e4`。
具体[机器收据](w3-terminal-publication-error-signal-20261112.json)与本机原件
`.omx/artifacts/w3-terminal-observation-20261112/io-postpublication-{error-fix,blocks}/`。

这是对原review finding的RED→GREEN修正，未再进行一次完整独立复审；不是实际协调器、
Android文件边界、实包、真机或发布批准。首检查点[双端同步收据](w3-terminal-file-primitives-sync-20261112.json)
保留，不覆盖原评论/报告。W3/R4/R5open、goalactive；继续实际采样/收据/ACK协调器及独立设备合同。

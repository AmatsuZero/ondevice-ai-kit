# W3 ownership-proven restoration — 2026-11-13

隔离恢复模块 `terminal_device_restore.py` 已按免费 hy3 候选及精确补丁集成。
新增20个恢复父方法通过；仓库新鲜回归为201观察器+16冻结守卫+108终态，
共325方法及51份Python AST全部通过。独立hy3源码复核accept、无finding。
本轮ADB为空，零真实安装、识别、麦克风、AiAi或模型下载；W3/R4/R5仍open，
goal保持active，源码分支未push。

`restore(root, ctx, *, serial)` 不依赖prepared、collection或transfer成功收据。
它先严格读取实际prepare的before/claim，拒绝重复JSON键、非法schema、损坏、
软链、null及外来绑定；stage child集合从冻结两APK/六模型的8MiB分块推导，
安装claim的SHA须同时绑定冻结APK和实际安装内容。普通包路径、APK字节和模型
摘要在恢复前后验证，不覆盖漂移内容。

仅有匹配所有权的新TEST/APP alias可卸载。stage须有精确33字节nonce+NL、
nonlink目录/marker及UID/dev/ino证明；created收据缺失时，先持久化观察到的
所有权。所有子项须在预声明集合内且为普通无链接文件；删除前再次验证目录
身份和marker。只逐项rm，marker最后，再rmdir；未知内容、链接、目录被替换
或操作不确定均保留失败证据，不伪造allRestored。它不承诺防护最后检查后
恶意同UID并发替换。返回普通Demo需当前resumed记录、user0与精确Activity。

操作错误形成durable false收据；KeyboardInterrupt/SystemExit保留原异常对象，
记录主异常，次生journal中断添加note。正常最终持久化失败直接传播。恢复为
一次性操作，已有recovered journal或restoration结果阻止重试；未来零参数
cleanup closure必须检查allRestored并传播不确定性，不能静默再次调用。

验收保留原始交付及全部失败：首请求自然239.120s终态，raw恰262144B、根JSON/
最终result截断；仅提取完整event2的完整source JSON，未补尾。首13方法中
1个父期望错误已按“live user不符返回false/零mutation”的实际合同订正。
初inode测试未拦住ctx.text闭包，改为真实fakeADB子进程在ls时rename/copy，
取得真实RED。损坏/外来claim、paused记录、am start失败、次生中断、目录替换
等均有真实RED和外部修正；初18GREEN后又补精确组件/user0/主异常2方法5FAIL、
null凭据1方法3FAIL，最终20GREEN。未改正确共享API迎合候选。

恢复依赖已加入DRIVERS及派生的REQUIRED/REVIEWED。coherent删除合同与双复核
依赖的负例先RED，外部单行补丁后16守卫GREEN。旧120冻结、原失败报告/裁定、
95其他文件、81准备源及两APK摘要无漂移。仅清理8个无读写进程的任务字节码
文件共285942B，全部唯一证据保留；同期磁盘可用变化不归因于清理。

完整dispatcher、run launch校验、bounded collection/policy、cleanup接线、
完整当前双复核、新冻结和同run真机仍待完成。不得重放旧consumed FILE/heap。
下一接口与门槛见本机`restore-parent/next-driver-brief.md`及既有adapter-C brief。

结构化结果见同名JSON；本机原始证据在
`.omx/artifacts/w3-terminal-observation-20261112/restore-parent/`及
`restore-repository-acceptance/`。这些host结果不等于Android/OEM验收。

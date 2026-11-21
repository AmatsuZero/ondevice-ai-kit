# W3 远端 ACK 发布器验证（2026-11-12）

`AdbTerminalAckPublisher` 的外部候选和精确修正已集成。完整 Python **157/157**、21 文件 AST、旧 120 冻结文件及原 heap 报告/false verdict 摘要保持；独立 supplied-source 复核 **accept，无发现**。这只验收远端发布适配器的本地主机行为，W3/R4/R5 仍 open，源码分支未 push。

五次有界传输依次为新 stage 的 guarded noclobber 写入、1025 字节上限的逐字节回读、文件 fsync、无覆盖 hardlink、目录 fsync。必需的 `before_link` 回调位于文件 fsync 后、link 前，供完整协调器执行最终同进程资源检查和持久化日志。stage 保留至显式恢复；首次实际尝试后实例即消耗，错误不重试、不自动清理或回滚。

`RemoteAckPublicationError` 保留 phase、原 cause、stage_may_remain 和未验证持久性；从 link 调用开始 publication_possible=true，超时不能证明远端没有发布。成功仅表示要求的 fsync 命令均返回成功，不代表硬件崩溃持久性。

验证共新增 18 方法：外部 8、父真实本地子进程/文件 6、父边界 4。实际默认 subprocess 路径经有空格的 fake-ADB 执行 `/bin/sh` 与真实 `os.link/os.fsync`，覆盖完整 stage 到 link 前不可见、相同 inode、既有文件/目录/悬空链接不覆盖、exec-in 退出 0 但短写拒绝、callback 失败、目录 fsync 失败后的完整 final，以及真实 0.25s 超时且不重试。边界覆盖各阶段 exact bytes、完整标识符、超大整数超时和任意合法 payload bytes。

外部及父检查直接缺模块 RED 均早于集成。首实现外部 2fail；额外父边界 8fail/2error，发现 bytes 子类被接受、match/$ 接受尾随换行、math.isfinite 转换超大整数溢出。免费 hy3 精确补丁纠正实现。独立测试把 `stage` 占位词当真实文件名，并用 `rm` 子串误禁含该字母的合法 `terminal` 目录；另一外部补丁只纠正测试，不改实现迎合假路径。所有原件/失败/补丁完整保留，未把 CLI 退出 0 当交付证明。

证据：`.omx/artifacts/w3-terminal-observation-20261112/publisher-{implementation-candidate,tests-candidate,parent,boundary-fix,test-path-fix,corrected-green,repository-acceptance,final-review}/`；机器收据见[同名 JSON](w3-adb-terminal-ack-publisher-20261112.json)。复核耗时 250.139s，只读供给源码，未执行命令；96 条既有 dirty 路径在本任务拥有的共享文档之外保持。

实际 Android/OEM shell、toybox 和私有文件系统的 hardlink/文件与目录 fsync 能力仍需新冻结驱动验证。下一远端协调器的 6 项父组合验收已 direct missing-module RED；外部独立 4 方法测试 session22761 与免费 hy4free 实现 session72144 正在各自原请求中生成，集成仍等外部测试审查/RED、两组全 GREEN。后续完整 polling/launch/restore、独立 Kotlin wrapper、Android build/lint/APK、新合同、同 run 末端证据及真机完整质量/资源、递归二进制组成/source 对应、生产通知与发布复审均不由本组件替代。零设备操作/推理/麦克风/AiAi/模型或 runtime 下载，已消耗旧 run 不重放。

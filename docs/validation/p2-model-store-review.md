# P2 模型存储与下载审查

日期：2026-10-16。范围限定为 Apple/Android 的 manifest 校验、原子安装、下载协调、取消合并和 lease；没有接入新模型、Demo UI 或真机推理。

## 独立审查与修复

首次独立只读审查覆盖 14 个文件，发现 2 High、2 Medium；修复后的只读复核未发现新的代码缺陷，未重跑构建或测试。该复核通过消息返回后，agent 最终状态因 HTTP 429 失败；因此这里只记录已收到的有限代码意见，不视为完整 merge gate。测试与构建由主执行器验证如下。

| 发现 | 修复 | 回归证据 |
| --- | --- | --- |
| Apple 下载器没有明确的重定向上限 | `DownloadOperation` 记录重定向次数，只允许前 5 跳，目标仍须为允许的 HTTPS host；第 6 跳取消任务并报 `invalidSource` | `testRedirectDelegateAllowsFiveHopsAndRejectsSixth`；先在第 5 跳失败，修复后通过 |
| Android 协调器继续使用调用方可变的 manifest 列表 | 校验前立即复制 `files` 与 `architectures`，后续 fingerprint、下载路径和安装只使用快照 | `freezesCallerFileListBeforeSuspendingDownload`；旧实现会把 `../changed.bin` 传给 transport，修复后没有越界文件 |
| provenance/license URL 与资源元数据校验不完整 | 两端要求来源和许可证为无凭据 HTTPS URL；测量内存非负且上下文非空；版本组件必须是可表示的 ASCII 数字 | Apple `testRejectsInvalidMetadataAndVersions`、Android `rejectsInvalidMetadataAndVersions` |
| 临时下载清理失败被静默忽略 | 清理注入点只清理当前任务目录；错误包装为 `ModelCleanupError`/`ModelCleanupException`，携带目录和底层原因；取消等待者也能收到该错误 | 两端均覆盖提交后清理失败、显式取消和调用方取消。已安装模型仍可通过 `status` 查询；不存在的目录默认清理视为成功 |

清理不会在共享下载根目录上做全量删除，因为不同 coordinator 可能同时拥有其他任务；恢复边界是报告精确目录，由上层决定重试或人工清理。ModelStore 自己的 staging 根仍按其独占 root 约定启动时清理。

## 验证

- `swift test --package-path apple`：Apple 25/25 XCTest 通过（ModelStore 13、Face 7、Core 5）；日志检查无 `Test Suite ... failed`、`Some test targets reported failures` 或编译错误。
- `scripts/verify-android.sh`：Android JVM 20/20 报告无失败（sdk-core 4、model-store 10、face 1、backend-mlkit 3、Demo 2），lint、Demo APK 和原生消费者 APK 构建通过；Gradle 290 tasks，3 executed，287 up-to-date。未变更模块使用已有 Gradle 缓存结果。
- `scripts/verify-apple.sh`：Swift 测试、KMP 模拟器检查、桥接 typecheck、原生消费者与 KMP Demo unsigned simulator build 通过；未变更 KMP 任务复用缓存。
- 默认 Gradle wrapper 下载仍因 TLS handshake 失败；验证使用已有 Gradle 9.4.1 缓存和 JDK 17，无全局配置修改。
- `git diff --check` 通过。

这些测试使用离线 fixture、注入 transport 和手动 delegate callback；没有把它们当作真实 TLS 重定向、Android/iOS 真机文件系统或设备推理证据。P3 仍需 iOS 真机最小闭环，P5 仍负责分发产物复核。

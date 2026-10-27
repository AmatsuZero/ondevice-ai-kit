# LiteRT-LM 0.18.1 native drain 合同修正

2026-10-27。原设计的 `terminalCallbackThenDeadConversation` 假设已被固定 AAR 字节码否定：`Conversation.isAlive` 是 Kotlin 对象生命周期标志，不能证明 native 生成结束。生产 factory 继续不可用；尚未建立替代 drain barrier。

## 固定证据

- Maven 坐标：`com.google.ai.edge.litertlm:litertlm-android:0.18.1`。
- AAR SHA-256：`05c6bdc8acda834efcca12c0ad5d06d2cd8c83e60770f056d8d222f699f3012b`。
- `classes.jar` SHA-256：`0ff0098f64407752c115bf2569a7cd3005c894ecb934e7fdbcd744037f398ce8`。
- 官方 `v0.18.1` tag 指向 commit `bda624851d74be34d4e1f7fac1fc4155939539c7`。
- 本地字节码、tag/tree 响应、官方源码及 URL/hash 清单：`artifacts/verification/android-litertlm-drain-contract-20261027/`；原 AAR/POM 在 `artifacts/verification/android-litertlm-aar-20261026/`。

## 已证实的语义与证据边界

实际 AAR 的 `Conversation.javap.txt` 与固定版本 [Conversation.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L75) 一致：`_isAlive` 初始化为 true，getter 只读取该 AtomicBoolean；`cancelProcess()` 调用 JNI，不修改此标志。[close()](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L743) 先把标志变为 false，再调用 `nativeDeleteConversation`。因此等待 false 后才 close 无法在单 owner 路径上满足；即使另一路观察到 false，也不能证明 native delete 已返回。

官方固定源码还显示：

- [JNI terminal callback](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc#L1307) 先调用 Java `onDone/onError`，之后仍清理 global reference/线程 attachment；收到 callback 不等于 native callback 栈已退出。
- [nativeDeleteConversation](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc#L1183) 直接 delete C++ conversation；[SessionAdvanced 析构](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/runtime/core/session_advanced.cc#L478) 调用 `WaitUntilDone().IgnoreError()`，再 `ReleaseSession`，错误不向 Kotlin 返回。
- [WaitUntilDone](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/runtime/core/session_advanced.h#L215) 使用 Engine [默认 10 分钟 timeout](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/runtime/engine/engine.h#L437)；[threaded manager](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/runtime/framework/resource_management/threaded_execution_manager.cc#L811) 可返回 deadline error。

这些 C++ 源码是固定官方版本的调查证据，尚未证明它与 AAR 内 `.so` 的构建逐项对应，也没有形成可用于生产的安全关闭协议。不能把 terminal callback 或 `close()` 返回自行提升为成功 drain；不能用 coroutine `withTimeout(30s)` 声称阻塞 JNI 会在 30 秒内结束。现有 30 秒 owner 测试仅验证可取消 fake 的等待失败处理。

## 本地修正与验证

- `DrainObservation.isProven` 固定 false；terminal、时间戳和旧名 `sawDeadConversation` 仅保留原始观察，后者表示 wrapper 标志为 false，不表示 native 已终止。
- `candidateStatus` 在无错误、单 terminal、累计 callback 条件下也返回 `unverified-native-drain-contract`；无法分类的 callback 保持 `unverified-callback-mode`，不再产生 `candidate-probe`。
- JSON schemaVersion 升为 2，增加 `drainEvidence=unsupported-conversation-lifetime-flag`。历史无资产设备报告仍为 schema 1，不重写旧报告。
- 删除基于错误证明关闭 conversation/engine 的分支。send 尝试后的 native 资源不主动 close，单进程运行门禁保留；既有轮询仍为最多 30 秒的原始观察窗口，尚未改造成新 drain 协议。没有模型运行或设备复验。
- `LiteRtLmSession.awaitTerminated` 继续是 fake 可验证的抽象 barrier；当前 0.18.1 生产实现缺席，不能用 `isAlive` 实现它。

两个针对误判的回归先得到 **9 tests / 2 failures**（`probe-red.log`，exit 1），修正后 **9/9、0 failures/errors/skips**。`probe-green.log` / `.exit` 记录 `BUILD SUCCESSFUL`、80 tasks（16 executed），包含 probe JVM、lint、APK 与 test APK 编译：

```sh
rtk proxy python3 scripts/gradle-local.py \
  -PlitertlmMavenRepo=.superpowers/sdd/2026-10-26-android-litertlm-backend/maven \
  :probes:android-litertlm:testDebugUnitTest \
  :probes:android-litertlm:lintDebug \
  :probes:android-litertlm:assembleDebug \
  :probes:android-litertlm:assembleDebugAndroidTest --console=plain
```

限定独立审查请求返回 `429 Too Many Requests`，没有审查结论，不重复派发。当前源码 hash、冻结 JVM XML、上游快照 hash 复核与验证摘要已保存到同一证据目录的 `source-manifest.json` / `summary.json`；本次文档续接没有重复运行已通过的构建。

## 继续条件

Task 1/4 的真实 native drain 门槛仍未满足，生产 factory、consumer 和设备推理保持未完成。下一步必须建立有上游 API/native 证据支持、能区分成功终止与等待失败的 barrier，并验证 callback 栈、资源所有权与超时边界；不能仅更换一个成功判据。授权模型 manifest/权重也仍缺失，不重复询问、不代为接受许可或下载 gated 权重。

该修正不新增完成阶段：基础里程碑 **10/12，P4/P11 未完成**；Android 是继续中的 P9 扩展包。

# LiteRT-LM 0.19.1 异步取消合同核查

2026-11-06，按用户选择使用`cbc --model hy4free`，只提供官方公开源码。
约148.31秒自然结束，CLI结果`success`、非空正文、0工具请求，供应商返回模型名`hy4-free`。
原生researcher前一线路的429保持失败，不重试；本次是用户指定的替代线路。

结论：所核对的`0.19.1`公开Android API仍没有可报告成功/失败的异步native cancel/join屏障。
因此，本次研究不构成升级理由，不改变固定`0.18.1`依赖，也不关闭F07异步drain/streaming缺口。
现有[同步factory和真实图像推理](android-litertlm-sync-demo.md)继续按原证据单列。

## 版本与证据

GitHub稳定发布和Google Maven元数据均指向`0.19.1`；发布时刻为2026-11-05T21:57:29Z。
本次固定tag提交`7a03724cece417f1724c923b3d527b4319730841`，不是浮动`main`。
Google Maven元数据可用不等于AAR已验证；本次只取公开文本，没有下载AAR或模型。

| 核查点 | 固定官方源码与观察 |
| --- | --- |
| 异步公开返回 | [Conversation.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L282)的callback重载返回Unit，Flow重载返回`Flow<Message>`；没有独立的join/wait状态接口 |
| Flow结束 | [L451–474](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L451)在`onDone`/`onError`关闭channel，`awaitClose {}`为空，不转发cancel/join |
| 取消与关闭 | [cancelProcess](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L669)与[close](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L761)返回Unit；`isAlive`仍是对象生命周期AtomicBoolean |
| 回调后的原生工作 | [JNI L1535–1571](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc#L1535)先调用Java终态回调，再清理global reference/线程attachment；应用收到终态不证明原生回调栈已退出 |
| 内部等待状态 | [SessionAdvanced::WaitUntilDone](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/runtime/core/session_advanced.h#L224)使用默认超时，内部可返回失败；[析构函数](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/runtime/core/session_advanced.cc#L487)调用`WaitUntilDone().IgnoreError()`，该状态没有作为公开Android屏障结果返回 |
| 取消后重用 | [SessionAdvanced合同](https://github.com/google-ai-edge/LiteRT-LM/blob/7a03724cece417f1724c923b3d527b4319730841/runtime/core/session_advanced.h#L192)仍明确取消后session被poison，不支持重用 |

“没有所需公开屏障”不等于“内部没有等待”。本轮不证明所有析构链行为、原生队列实际状态、
二进制与源码对应或设备安全；这些不能从方法返回Unit单独推导。

## 主代理核验与未采纳意见

主代理逐行核对上述关键源码、公开方法表和文件SHA，并复核固定0.18.1的19份来源摘要。
保留hy4free核心合同结论，否决其两类过度推断：

- `Engine.close`直接调用JNI delete，不能据此断言内部完全不等待session；析构的传递行为未证明。
- 将`terminal_reached`、`responseFormat`、`getTokenCount`、`thinkingConfig`泛称为新版新增不成立；
  保留的0.18.1源码中已经存在这些例子。助手没有收到旧源码，不能据其清单宣称精确版本diff。

Context7检索说明曾把空`awaitClose {}`解释成native drain保证；固定源码不支持该解释，未采纳。
具体版本事实以固定原文为准。继续抓取相同内部实现不能补出缺失的公开合同，本轮研究到此结束。
将来若上游提供可观察成功/错误的屏障，再评估二进制来源、失败/超时行为及真机取消/重入；
不据本轮研究新增生产异步路径或重复已完成的同步设备测试。

公开原文/版本/摘要：`.omx/artifacts/litertlm-later-api-public-20261106/`。
精确prompt、原始事件、正文、进程收据和主代理处置：
`.omx/artifacts/cbc-litertlm-later-barrier-20261106/`；其中`parent-assessment.json`是最终采纳边界。
这些是本机保留证据，不保证随普通Git clone交付；没有新构建、设备操作或发布批准。

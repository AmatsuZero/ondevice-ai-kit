# W3 Qwen heap 边界诊断合同

目标：以一个原批次尚未执行的公开文件，记录已知调用边界的Android native heap及启动后时间，供父端RSS对齐；不改变识别算法，不宣称读出ORT内部每Run峰值、allocator保留或根因。

输入为既有manifest零基index25、`fleurs-en_us-1776`，en-US/76800samples，PCM SHA256 `2821c39358fc8049953945d8042d1833d86cb2245ca468cf5afc31ee1fe1a438`。事前从arena报告核该row仍planned/无stage，父端记录此判断；不重放index0–24。完整65输入仍先校验，但只生成并执行此一row。

保留固定AAR/六模型、CPU2、128mel、seed42、temperature1e-6、topP0.8、maxTotalLen512/maxNewTokens128、greedy、auto准入→强制English两阶段，最多构造1/stream2/decode2，不因诊断改变音量或输出。沿用arena-off精确政策模块，memory pattern仍为默认。父端1.5GiB RSS/512MiB available/每片120s不变；新别名`io.ondeviceai.acceptance.qwenheap[.test]`，runner class仍在既有qwenfile包，新report子目录`w3-qwen-heap`，旧源码/APK/报告均不改。

外部实现只拥有新文件`QwenHeapInstrumentation.kt`：复制既有arena wrapper，复用原core/Activity/inputs/report/model verifier/policy。新增有界heapEvents数组、sticky heapTelemetryErrorClass，不开启线程/计时器、不使用GC/malloc_trim、不联网/采音/新native API。每event含seq、label、elapsedRealtimeNanos、activeCaseIndex、casePhase、当前stage language/counters/status及三项`Debug.getNativeHeapSize/getNativeHeapAllocatedSize/getNativeHeapFreeSize`字节数。三读非原子，不要求相加恒等；允许0、拒负数；错误用null及实际class，不能写0假成功。最多128events，达到上限按故障处理，不截断后继续。

采样点：首次持久化前、constructor dispatch前/返回后、每个corePersist、最终case返回后、recognizer实际释放后、Activity teardown结果后。核心persist仍由原core拥有，不修改操作/计数顺序。采样点只代表边界，无持续native峰值；stage dispatch前persist并不保证其后的native实际已进入。

正常路径采样或持久化失败阻断后续native；错误在outer/core finally必须仍尝试所有已取得stream/recognizer及Activity清理。清理区采样失败只能记sticky错误，不能跳过或重复真正release/teardown，也不能把成功的实际release改报失败；原primary error与telemetry error分别保留。最终完成需全部原门及heapTelemetryErrorClass=null。snapshot纯序列化已有events，不再采样，故采样故障可被best-effort失败快照记录。失败快照也不能伪造持久化成功。

report新增selectedClipId、selectedOriginalIndex25、verifiedInputCount65及diagnosticScope，rows仅1。禁止新增可选ID参数扩大范围，禁止偷偷推理其他输入。新父工具须冻结源码/实包/所选PCM/parent/source identity和公共配置；核APK真实零权限、原四库摘要、一个输入实际调用；独立复核后才允许新run。此文是实现合同，尚不是已冻结设备运行合同。

API证据：Android15 AOSP `android/os/Debug.java:1921,1927,1933`，三个API返回字节；本机来源摘要见`.omx/artifacts/w3-qwen-ort-lifecycle-20261111/android-debug-source.json`。Context7未命中这三个方法，未使用返回的其他API替代。

# P4 AtlasTextOp 分配与线程缓存调查

## 2026-10-28 全范围审查与依赖结论

fresh P4 code-review 覆盖 camera isolation、synthetic PHPicker harness 和当前
resource/page/thread probe，结果为0 Critical、2 Important、0 Minor。默认 resource probe
静默吞掉二次报告写入失败的问题已按 RED→GREEN 增加有界 fallback，并通过 fresh generic
iOS 15 unsigned build；受保护 signing 工程继续留在独立 workstream，禁止进入 P4 选择。
完整范围、哈希和验证见[全范围审查记录](p4-full-review-20261028.md)。

上游只读核验同时得到 `no-supported-upgrade-conclusion`：已发布的 Compose 1.12.2 /
Skiko 0.150.2 及更后 Skia m153 源码仍保留相同 AtlasTextOp TLS 单槽、调用线程
`ClearCache()` 和 `GrRecordingContext` 析构清理机制，没有证据表明升级会处理本次18个
residual。当前不升级、不强制覆盖 Skiko、不调用私有清理，也不重标 Leaks；P4 继续开放。

## 2026-10-27 分配时间与线程快照邻接复核

对既有 `p4-thread-calibration-20261027` 的完整 Allocations List、Leaks 明细和 236 个 own-task 快照做了只读时间对齐。18 个最终 live `GrOp` 中，1 个在 trace 开始前已有；其余 17 个分配的 trace 相对时间均能由 4097B marker 校准到分配时刻前后各约 1.1 秒内的快照，并且前后快照都观察到同一个 kernel thread ID。7 个导出低 32 位标签在不同分配窗口分别对应多个不同 kernel thread ID，具体映射与每行邻接时间见 `artifacts/verification/p4-thread-calibration-20261027/allocation-snapshot-correlation.json`，复核脚本为同目录 `correlate_allocations.py`。

这把“相同导出标签”与“同一实际 kernel thread”区分开：本次 17/17 的时间邻接样本支持分配时刻的观察映射，同时 7 个标签的跨周期 ID 复用仍然存在。快照是非原子的，marker 到 trace 时间原点有调度误差；结果没有读取析构/free、TLS 槽值或精确线程退出，不能关闭 GrOp、证明缓存释放或推导长期增长。原始 Leaks 判定和 P4/P11 状态保持不变。

## 最新：marker 校准与实际 thread label 复验（2026-10-27）

Task12 的取证完整性修正先以 lookup seam 稳定复现缺失 pthread 映射的 runtime RED，再要求 `partial-snapshot`、失败数和 unchanged send-right；修正后 macOS 宿主100轮快照通过，引用计数2→2，iOS15 Swift6 warnings-as-errors typecheck通过。设备 probe 仅在显式 `--camera-thread-inventory` 下启用，普通模式不改变。

本轮在当前源码生成的签名 probe 上完成三轮同进程页面退出/重入和精确 PID Leaks 录制。三轮活动窗口新增 **687/661/660** 次，停止5秒无新增；最终页面 owner、Compose controller、Metal layer 与 bridge 均释放，首次全弱引用释放的实际页面缺失时长约32.643秒，最终约61.986秒仍为0。trace 时长301.202178秒，导出 Allocations List 读取108263行/85579214 bytes、exit0；最终 **18×GrOp/2304B** 全部仍为 live，不能据此关闭原生残留。

4097B marker 实际由 malloc 分类为5120B，分配栈命中 `ThreadInventoryMarker`。其记录的 pthread low32 candidate 与导出的 thread label一致，证明此导出器使用相同低32格式。8个 GrOp allocation labels 都能在 own-task 快照中找到；其中7个完整 pthread 指针分别对应多个不同 kernel thread ID，说明相同 label 不能直接代表同一持续存活 OS 线程。236个快照中66个 complete、170个 partial，partial 仅因 pthread 查询缺失；未见 thread_info、port release 或 VM array release 错误。

这仍不是析构/free/TLS 证据：快照边界非原子，缺失不是精确线程退出时间；没有跨线程内存读取、TLS 读取或私有清理调用。marker 只校准一个已知主线程分配，其他 GrOp 仍缺少 generation/析构归属。新限定 reviewer 继续因429无结论。证据：`artifacts/verification/p4-thread-calibration-20261027/summary.json`、`candidate-thread-mapping.json`、`snapshot-summary.json`及同目录原始报告/trace/导出。P4/P11保持开放。

## 最新：设备 LLDB 取证边界（2026-10-27）

使用本机`devicectl`帮助中记录的`device select`、`device process attach -p`路径，5次有界诊断均未取得可检查的映像。target保持`<none>`/`state=connected`；显式`remote-ios`仍报`no modules found`，`process interrupt`报`Process must be launched`，补充`process attach`报`no such process`。这些是调试工具结果，不能推导产品错误、析构已经发生或TLS槽归属，也不关闭原生残留项。没有新的受支持路径时不重复这些变体。

原始日志与后续只读设备进程快照位于`artifacts/verification/p4-atlas-device-debug-20261027/`。普通Demo原恢复安装URL的新鲜进程为PID27906；上次明确无参数启动为PID27901。本次未重装、重启、终止或操作另一旧安装的进程，未取得新的设备UI或native生命周期验收。已有页面退出和bridge自然释放证据保持有效，基础进度仍10/12、P4/P11未完成。

## 最新：末次 bridge 延迟释放与线程退出机制（2026-10-27）

在保留上一轮全部证据的基础上，当前probe增加最后页面退出后的31/35/60秒名义采样，并记录实际monotonic时间及同步弱bridge状态；不重建页面、不强制GC，也不跨await持有bridge。当前源码/生成器/framework哈希均与本轮实际构建匹配，上一轮审查后的诊断/manifest/status修订也已随新包实际运行。

iPhone16ProMax/iOS27.0.1，PID27882/runID `543373D1-52A7-424C-9ED1-FE5922D6E97C`。三轮30秒窗口新增672/694/694次Vision成功，stop后5秒无新增且inputs/jobs0。每轮名义30秒采样时owner/Compose controller/Metal layer均为空，bridge仍存在但**admissionAvailable=false、pendingInputs/jobs=0**。最后退出实际30.992938秒bridge仍存在，32.086039秒采样已消失，直到62.350613秒仍全部weak对象为0；没有第四次页面或相机重启。原始结果为`observations-complete`、`weakReferenceOutcome=released`，nativeLeakAcceptance继续显式unverified。

301.116972秒Leaks录制（07:14:56.428–07:19:57.545 +08:00）覆盖全部周期与idle尾部。**18×GrOp/2304B仍列入Leaks且全部在最终live allocation表匹配**：17项有33帧AtlasTextOp路径，1项录制前已有。完整导出99401行/73387236bytes、exit0。此次导出额外保留binary定义，trace中`OnDeviceAIDemo.debug.dylib`的UUID **00708A06-D253-3EEB-9B8C-0AF13A8BF460**与实际运行构建的dwarfdump完全一致，PID/安装URL/时序也吻合。末次RSS快照147816448B，不据此宣称零泄漏。

这关闭本实验范围内“最后bridge是否必须重入才释放”的疑问：已关闭的bridge在持续空闲中自然释放。**原生残留仍未解决**，也不能把本轮18与上轮19跨运行对比为增长率或改善。当前普通Demo已恢复无参数启动PID27888，原应用容器、保护文件及生产源码保持原样。证据与严格验证脚本：`artifacts/verification/p4-page-tail-20261027/summary.json` / `verify_tail.py`。

### 固定源码与宿主机制复现

直接取得同一固定Skia revision的[AtlasTextOp.h](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/ops/AtlasTextOp.h#L68)、[GrOp.h](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/ops/GrOp.h#L81)、GrOp.cpp及[GrRecordingContext.cpp](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/GrRecordingContext.cpp#L63)，文件hash和下载URL已保存。它们确认virtual GrOp析构、AtlasTextOp自定义delete及context析构ClearCache调用；不是从类名猜测删除路径。

从已固定cpp的68–89行提取allocator块，仅将全局new/delete调用映射到计数器，用简化120B虚析构类和`unique_ptr<GrOp>`运行独立C++宿主实验（没有链接Skia或修改设备TLS）。`clang++ -std=c++17 -Wall -Wextra -Werror -O0`与运行均exit0：

| 宿主场景 | 观察结果 |
| --- | --- |
| 同一线程三次new/delete | 三次析构，仅一次分配；同线程ClearCache后原始存储归零 |
| 三个线程分别new/delete后退出 | 三次析构、三次分配；join及主线程ClearCache后仍有三块原始存储 |
| 存活worker缓存、主线程清理 | 主线程清理仍留一块；由worker清理才归零 |

宿主三次线程的pthread指针完全相同，kernel thread IDs却不同。原始`thread_local void*`槽没有指向对象的自动清理行为，线程退出路径可丢失这块已析构存储的引用。实验完成后仅回收实验自己的已退出线程存储，不调用实际Skia或设备私有清理API。这说明**线程退出/地址复用可以解释多个周期的残留**，尚不证明真机地址实际经过此路径。

另导出上一PID27863的thread-info：35个kernel TID与Allocations List的thread-id标签没有交集，所有metadata时间为0，未建立生命周期映射。因此，之前“同分配线程有多个地址”应严格解释为**相同导出thread-id标签**，不能据此断言同一持续存活OS线程有多个TLS槽，也不能据此排除单槽缓存机制。需要继续观测实际thread lifetime、delete/析构、TLS槽指针与对象地址的关系。

源码/宿主实验/thread metadata见`artifacts/verification/p4-atlas-reentry-upstream-20261027/summary.json`。researcher本轮429没有意见；作者直接官方取证成功。最近五条默认分支AtlasTextOp.cpp历史未取得可适用修复依据，这不是完整上游修复检索或依赖升级结论。当前不改依赖、不压制Leaks，P4/P11与完整交付继续开放。

## 前次：同 PID 三次页面退出/重入（2026-10-27）

新真机实验已补上真实页面退出：生成器把生产App内容和StateObject原样移入page View，由独立root控制移除/新建；未修改生产导航、相机、controller或依赖。新probe只用弱引用观察page owner、bridge、Compose controller及Metal layer，不强制GC、清理TLS或保存画面。逐项生成源码反向还原与生产源码完全相等；这是生成root驱动的页面实验，不是物理UI控件验收。

iPhone16ProMax/iOS27.0.1、PID27863、runID `7BB10334-CBE9-4CC8-839A-BC6DD3F38C20`。三轮均创建新owner，实际相机活动30秒后stop/drain并等待5秒，再移除整个页面并采样5/15/30秒。每轮最后采样的owner/Compose controller/Metal layer均已释放，bridge仍为1；下一轮warmup时上一轮bridge已消失。最后一轮bridge在最终采样仍为1，所以原始probe结果保留`incomplete-release`，未证明其后释放时间。

| 周期 | 活动秒数 | 窗口新增Vision成功 | 每轮成功累计 | 活动RSS(B) | 页面退出30秒RSS(B) | 本周期分配且最终仍存活的Leaks地址 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 30.137306 | 667 | 672 | 468615168 | 144605184 | 7 |
| 2 | 30.049058 | 690 | 694 | 472645632 | 149618688 | 6 |
| 3 | 30.189110 | 694 | 697 | 474431488 | 150798336 | 5 |

同PID Leaks录制301.106594秒，06:56:02.474–07:01:03.580 +08:00，已验证设备/PID/安装URL和全部周期的时间覆盖。**Leaks19×GrOp/2432B，全部地址在最终live Allocations List中匹配**。完整流式导出104655行/80807499bytes、exit0；18项均有33帧AtlasTextOp/SurfaceMetalRedrawer文字绘制栈，另1项在录制前已存在。18项来自8个allocation thread IDs，其中4个线程各有3个最终存活地址，分属不同周期。

表中的7/6/5是**最终存活地址按分配周期分组**，不是三个时点的Leaks全量计数或全分配事件序列。它证明三轮均新增跨页面退出仍存活的原生分配；当前“每个分配线程恰好一槽”的简化解释不足以关闭此项。实际delete/析构可能在其他线程，TLS槽和释放线程仍未直接观测，也没有证明长期无界增长。Allocations另外含3个live AtlasTextOp地址未列入Leaks，不将其擅自判成泄漏。原7×GrOp旧trace保持原结论，不能跨不同运行计算增长率。

最终Allocations persistent为35672864B、其中VM:IOSurface3080192B；RSS明显回落说明大块资源释放，但不是零泄漏、峰值、footprint、GPU或能耗验收。下一步聚焦AtlasTextOp实际析构/释放与线程归属，并在最后一次页面退出后补更晚的bridge采样。保持当前依赖，不调用私有ClearCache或压制Leaks。

证据：`artifacts/verification/p4-page-release-20261027/summary.json`，包含每轮样本、地址/线程/分配时间、完整栈、原始trace、生成源码/实际App/framework哈希与restore记录。新普通Demo已恢复无参数启动PID27875，保留容器。独立probe审查的3项诊断/元数据建议已处理：失败报告写入错误记录有界NSLog、manifest补framework/Info哈希、将观察完成与原生泄漏验收分字段；第4项生成模式验证由真实双模式源码还原/manifest检查满足。修订后签名构建再次成功，但未再运行真机；真机使用的修订前probe源码和binary保存在独立目录，结论仅对应它。未声称全P4独立审查通过。

## 历史：单次持续/停止 trace 的只读调查

2026-10-27早先针对 `rendered-camera-pid27593.trace` 的只读调查没有新真机运行、生产源码或依赖修改。当时结论是**五个对象的分配路径已明确，线程缓存假设得到支持，完整资源释放仍未验收**；后续跨周期证据以上方最新实验为准。

## 本轮取得的运行证据

原始 runID 为 `DE42C583-3089-416C-92A0-94B38266F918`，PID27593；其两分钟真实相机/Compose运行和停止后30秒验证见[P4记录](p4-camera-stability.md)。本轮按原 Leaks 明细的七个地址筛选 Allocations List，完成导出读取118470行、77838589 bytes，保留目标记录并解析共享frame引用。七个地址、live状态及128B大小全部与原明细吻合。

| 地址 | trace内分配时间 | allocation thread-id | 可用分配栈 |
| --- | --- | --- | --- |
| 0x10240a600 | 00:00.000.000 | 0x6efff000 | 栈限制；录制前已有 |
| 0x10240a780 | 00:00.000.000 | 0x6efff000 | 栈限制；录制前已有 |
| 0x117fb2a80 | 02:12.450.161 | 0x6efff000 | 33帧 |
| 0x11c4c0f80 | 02:31.993.493 | 0x6f1a3000 | 33帧 |
| 0x11c4c0380 | 02:32.176.569 | 0x6f22f000 | 33帧 |
| 0x11c4c3200 | 02:32.285.241 | 0x6ef73000 | 33帧 |
| 0x11c4c0880 | 02:32.352.232 | 0x6f117000 | 33帧 |

五个符号化对象分别来自五个不同分配线程，均有如下完整路径中的关键帧：

`SurfaceMetalRedrawer.startRenderLoop → renderAndPresentFrame → Canvas.drawPicture → SkCanvas.drawTextBlob → SurfaceDrawContext.drawGlyphRunList → AtlasTextOp::Make → operator new → malloc`

栈底为GCD serial lane/worker thread。这明确了这五次分配来自Compose/Skia文字绘制。它记录的是**分配线程与分配栈**，没有记录这些地址的析构执行、最终TLS槽指针或释放线程；不能直接将分配路径等同于泄漏所有权根因。另两个对象仍无可用归因。报告中的wallTime只有秒精度，也不据此断言最后几次分配发生于stop调用之后。

## 固定版本机制与当前二进制

上游只读研究核对的版本链为Skiko `0.144.6` / commit `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0` → Skia tag `m144-22f58c9fd4` / commit `22f58c9fd43d55bde818821c04b48fda5d7ec939`。

[固定 AtlasTextOp.cpp](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/ops/AtlasTextOp.cpp#L68-L89)已直接获取并保存，其68–89行明确：

- `gCache`是`thread_local void*`单槽。
- `operator new`优先取出缓存并清空槽，没有缓存时才分配。
- `operator delete`在槽为空时保留这块已析构对象的原始存储；槽非空则释放本次存储。
- `ClearCache()`释放并清空调用线程的槽。

本地最终验证包的Mach-O也含相同路径：TLS符号`__ZN5skgpu6ganeshL6gCacheE`位于`0x21c3550`；Make的分配慢路径请求120B，与trace中128B malloc分类一致。删除析构函数先释放几何引用链/其他成员，再选择缓存或`operator delete`。导入符号表核对了new/delete跳转目标，避免依赖反汇编中不准确的邻近符号标注。

还取得了补充证据：当前二进制的`GrRecordingContext::~GrRecordingContext()`在`0x486374`直接调用`AtlasTextOp::ClearCache()`。因此，不能从仅查看`GrDirectContext`派生类析构函数推导“context销毁不清理缓存”。但是该清理仍限于调用线程，不会自动遍历其他线程的TLS槽。

被分析本地产物SHA256为`a54698340b106d1482cec680c68c39015910e556294983981b5f6821516f2d3f`，UUID为`5DD32633-C5A3-3B08-9104-9B915963FAAB`。此次attach trace的`dyld-library-load`导出仅有schema、无事件行；没有据此宣称trace映像UUID已独立复核，也没有读取运行时TLS值。

## 结论与下一验证

“五个文字绘制分配分别来自五个线程”与“每线程缓存一个已析构对象的存储”相容，支持线程缓存解释；它尚不能证明所有七个对象都是缓存、存储已析构、线程退出时被释放，或长期运行没有增长。当前仍保留Leaks原始判定，不标记为误报或零泄漏。

下一验证应保持现有依赖，通过真实页面退出/重入及同PID采样，区分稳定缓存、跨线程清理边界与持续增长，同时观测Compose owner/renderer正常释放。正常停止相机仍保留页面和最后一张预览，不能代替整个页面的teardown。未取得适用于本版本的已证实上游修复，不据此升级或增加私有清理调用。

证据在`artifacts/verification/p4-atlas-cache-20261027/summary.json`，包括七个分配记录、五份33帧栈、源码hash、当前二进制符号/反汇编/导入目标与保护hash；完整栈另存`allocation-stacks.txt`。原录制包与summary保留不变。

## 工具与证据边界

- row-address XPath导出为空；首个64MiB上限仅覆盖部分记录，明确保留为partial。扩至512MiB上限后于约77.8MB完整结束，匹配全部七个地址，不把早先空输出解释成对象不存在。
- 桌面UI路径未到达目标trace，且出现窗口不可用/状态变更；没有Instruments UI审查结论。实际调用栈来自CLI导出。
- 上游首次研究有结果，后续补查遇429，没有追加结论或独立产品审查通过。
- 直接补取`AtlasTextOp.h`与Skiko `gradle.properties`的两次只读下载未执行：自动审批服务429，审核未完成，并非操作被判定不安全。未绕过审批；关键缓存路径以已获取cpp及本地二进制交叉核对。

P4/P11仍未完成，基础里程碑保持10/12；全部原范围、未提交修改与保护文件保留。

# W3单文件heap诊断：源码与实包

状态：外部候选原字节集成、完整host及Android实包通过；源码和父工具独立复核的问题已逐项处理，尚无新冻结run或设备操作。W3、R4/R5保持open。

`hy3`整份外壳空响应后，分成纯记录器及接线补丁。记录器首稿13组80断言未覆盖JSON缺key，补充12项RED后定点修正至13组100通过，见[记录器证据](w3-qwen-heap-recorder-20261111.md)。接线522.545229875s交付18392B JSON：17个唯一且不重叠的原文替换及已验记录器body原样附加，机械组装30109B。首轮32组265断言通过；新增同时decode+heap失败回归，真实1RED发现根错误被随后heap错误覆盖。外部最小补丁85.724412250s，只在返回case后的易失败采样之前传递原case错误；一次创建前读取输出报`File does not exist.`，后续真实写入成功。原记录及父端过严工具错误断言导致的缺候选RED均保留，没有重派此修正。

最终`QwenHeapInstrumentation.kt`30513B，SHA256 `b3df42e7cbde1992d4f91b6b1bc7143ec6073386bab0a6cb5afbf13d90e743cb`。隔离组装和仓库原字节集成后各自33组274断言通过；原core、Activity、inputs、report及arena政策未改。根、release、persist、heap错误分开保留，普通采样失败阻断后续native，清理区heap失败保留真实release/teardown结果。

实际prepare另暴露父工具路径错误：`QwenLoadCore.kt`来自`android-qwen-load`，首稿prepare/build/device却绑定`android-qwen-file`；合成夹具也复制了错误目录。按真实布局改夹具后19检查出现9errors RED，再修三入口绑定，19检查通过。原真实prepare未写新source目录、零构建/设备；随后构建入口因无收据也拒绝。未在错误目录补源码或改旧冻结文件。

真实离线Android构建20.243705416s、75任务全部执行，主/测试Kotlin均实际编译，lint0error/7既有warning。来源指纹 `a79adaa583e3707be20055e2fc6dd01f6ca58a4da5da686cf21196666d0ce7e1`。APK核对精确alias/runner、零权限、同debug证书、65实际PCM摘要及四arm64库原摘要；基础16个指定类和额外test recorder类实际DEX均存在。main SHA256 `1d65b2fabb237871dc498ba7fa93807444a6fde6b3292cca5656dc142a7db80a`，test `808f9dbdeff4fb538e16457b695db20bf45d76eb9eba76e0cc9c5c509d884182`。

父检查为heap报告22、路由19、原arena22/FILE8/metrics31，共102项。完整tools首轮691项19failures/8errors，命中已记录的Ruby2.6加载Ruby3.4gem冲突；仅本次子进程PATH对齐已安装Ruby3.4.11并验证JSON可加载后，同691项47.508669584s全部通过，未改全局环境、生成器或锁文件。

独立源码复核自然260.566710417s交付，未发现阻断性问题；一次创建前读取报告失败保留。父端核当前11份来源hash与上下文相同，接受范围仅源码/供应实包收据绑定；对报告中“已冻结”和固定两阶段措辞的订正见[复核收据](w3-qwen-heap-wrapper-review-20261111.json)。它没有重新执行host/package，不证明设备或native配置生效。

父工具独立复核480.620806458s交付，15Read/1Write、无工具错误；原结论`changes-required`保留。漏绑基础`cases.json`已用缺hash回归复现：原守卫仍触及mock ADB，补必需集合后20路由通过。另两项属于复核上下文边界：原core成功路径必有initial和final persist，每次callback均采样，空auto也不只有一个core-persist；保留8..128门。七个实际标签由另一份独立源码复核确认后供冻结绑定，不能声称父工具复核自行核过Kotlin。父端裁定、当前来源与原上下文差异见[父复核收据](w3-qwen-heap-parent-review-20261111.json)。修后103定向及Ruby对齐完整tools691项通过，后者50.250450500s；不与旧102或重叠metrics累计。

本机原始证据：`.omx/artifacts/w3-qwen-ort-lifecycle-20261111/heap-hy3-resume1/`、`artifacts/verification/w3-qwen-heap-20261111/`及各`/private/tmp/odai-hy3-w3-heap-*-20261111/`。原108冻结文件保持；F08交接出现其他任务漂移，未修改或回滚。下一步冻结原未执行index25/1776的唯一新诊断，fresh设备门后才运行。原两阶段/arena-off、1.5GiB/512MiB/120s和整诊断240s不变，不重放旧批、不开麦克风/AiAi、不下载模型、不改生产或普通App。

# W3 Android load-only 探针已准备，真机尚未执行

隔离探针已完成本地验收：core17组/63断言、wrapper12组/44断言、父资源guard11检查通过。
两个APK离线构建、lint及实包核验通过；当前TECNO安全锁屏，未安装、加载或解码。
[详细收据](w3-qwen-android-probe-ready-20261110.json)、[冻结合同](w3-qwen-android-load-protocol-20261110.md)。

| 验收 | 实际证据与边界 |
| --- | --- |
| core | 精确外部候选SHA `0bac095d4cd8151257900827bfc3e013a7b2d0b2bebd92b674b06e39c98f999c`；真实文件hash/path/软链与构造/持久化/释放/计时故障63断言通过，真实JNI0 |
| wrapper | 原配置在host native double上触发tokenizer路径错误，12组中4组失败；定点修正后12组/44断言通过。模型校验在此层是double，真实文件校验由core另验，不冒充JNI/Android设备运行 |
| 实际编译与构建 | 使用已有AAR/SDK/JDK，`assembleDebug assembleDebugAndroidTest lintDebug`，27.557s，75tasks；不升级生产依赖 |
| lint | 0error、7warning：目标API35、两项既有Kotlin插件新版本提示、无ChromeOS x86_64、备份规则、图标、静态英文状态文本；均保留，无零警告声明 |
| 实包 | main32431920B、test19000B；七个预期类进入实际DEX，四个arm64原生库逐字摘要匹配固定AAR；两APK同一已有debug证书、零请求权限；Activity实际exported=false |
| 源绑定 | fingerprint `605181e50ea9357c292be93e2bf634742c40566a13984a0ff0b83fdb8b9c0ecd`，主代理提交前核原探针/隔离构建副本/包内类；该值由父端给定，不冒充设备自行计算源码哈希 |
| 手机前置 | 最近只读：TECNO KM9/API35/arm64，awake=false/unlocked=false，无活动instrumentation，新alias尚不存在。锁屏不会被前台Activity绕过；需要用户解锁才能进入单次加载 |

原多文件hy4free任务的exit0/empty_model_response/零交付保留；原core编译失败与计时错误也保留。
新wrapper首提示由主代理错误指定tokenizer根目录，实际文件及固定上游均要求`modelRoot/tokenizer`；
主代理承担该提示错误，外部助手仅做精确路径、onStart worker和同main-thread gate修正，最终按原字节集成。
没有让主代理或native代写探针实现，也没有重试原native429。

下一步只运行一个load-only run：源/包/模型摘要与普通App安装前清单复核，使用独立alias供应六份固定
公开模型文件，刷新已解锁状态，再做一次构造/释放及采样。RSS>1.5GiB或可用内存<512MiB即停，
不放宽目标、不自动再跑、不创建stream或识别。采样不是true peak，非终态报告不能补造释放成功。
同run双读与模型/App恢复后才裁定；完整两阶段质量、性能、持续资源和再分发仍另验，W3保持open。

本机完整原件：`artifacts/verification/w3-qwen-load-20261110/`。
两端Issue/Wiki最新复核/供应链/core进展已发布并回读，
[发布收据](w3-qwen-progress-sync-20261110.json)；本任务没有推源码分支。

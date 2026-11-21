# W3 Qwen Android load-only 事前合同

当前 Mac 两阶段独立确认已通过，结果不代替 Android。先只判断固定 int8 导出在
TECNO KM9 / API35 / arm64-v8a 上的加载资源可行性。生产后端和普通 App/模型保持原样。

## 冻结边界

- 独立 alias `io.ondeviceai.acceptance.qwenload` 及 `.test`；不声明 INTERNET、录音或存储权限。
- 使用现有 Sherpa 2.0.1 AAR（SHA256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`）。
- 只供应已验证包的三份 ONNX 和三份 tokenizer 文件，共 987015347B；逐文件摘要见
  本机 `.omx/artifacts/w3-model-reselection-20261110/model-manifest.json`。
  Android 在 JNI 构造前以流式读取重新核对实际文件 size/SHA，不把 756MB decoder 读入 ByteArray。
- CPU/2 threads/128 mel/greedy/seed42/temperature1e-6/top_p0.8/maxTotalLen512/maxNewTokens128。
- 一个 fresh recognizer 构造并显式 release；不创建 stream，不调用 decode，不读音频，不做 VAD。
- 前台 Activity 和系统 Awake/已解锁是 JNI 前置；锁屏/退前台、hash/持久化失败时停止，保留失败。
- 报告按 runId 独占预留，以 AtomicFile 写入；首份 running 落盘后才能构造。
  构造尝试/返回、释放尝试/成功分别记录，构造抛错不能虚报已取得 handle。
  主异常与 release/持久化/Activity teardown 错误独立保留，清理后才写终态。

## 资源保护与裁定

设备执行前刷新内存、存储、普通 App/模型摘要和当前 instrumentation；先保存安装前清单。
父进程对精确 alias/PID 的 `/proc/PID/status` 采样，目标间隔 250ms；真实间隔及遗漏如实报告。
加载阶段最多120s，采样 RSS 超过1.5GiB、设备 MemAvailable 低于512MiB或进程失联时停止本次，
不自动重试、不把保护中止写成质量拒绝。只对已核验本次 alias 使用 force-stop，并回收同次报告。
如果采样受限，则不提交加载，先解决观测前置；1.5GiB 不因 Mac 结果放宽。

load-only 通过仅表示构造/释放及采样资源可进入下一项设计，不表示推理 warm RTF、两阶段质量、
true peak、GPU/能耗、持续运行或原生分配全部归还。若构造期间已触及预算，结束此手机配置路线，
保留 Mac 质量信号和明确资源失败，不继续整批解码。

## 执行与验收

免费 CBC hy4free 仅在独立目录交付候选，主代理集成与验收。native 旧429不重试。
先以真实文件和 fake native 检查正常、hash/软链/路径、锁屏、前后持久化失败、构造与释放异常，
再 build/lint、核 DEX/实际权限/ABI/库摘要/签名和源绑定。满足后才准备手机。
终态双读一致后，只卸载本次 alias、清理有清单的暂存，复核普通 App/模型并返回普通 App。

实现候选、host检查、构建与真机观察分别记录；W3 仍 open。

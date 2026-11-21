# Android 普通 App Identity 验收 — 2026-11-06

本次关闭一个实际使用子项：保留原 App 的模型导入 → 显式同意 → 获授权图片注册 →
未校准拒绝 → 删除停用 → 冷启动复用模型。基础里程碑仍为 **10/12**，P4/P11 仍 open/not-ready。

## 实际结果

TECNO KM9/API35 连接恢复后，稳定观察通过；全程使用原 `afed156b…` 普通 App，未换包、
重建、运行 instrumentation 或新增下载。复用已有固定 YuNet/SFace ZIP 与公开 NASA 图片，
仅经普通控件及系统 DocumentsUI/Photo Picker 选择指定输入，没有读取私人照片或采集音视频。

- 普通“导入身份模型”成功，显式同意后启用 30 分钟本机注册。
- PID24632 中一次注册得到 `ENROLLED · opencv.identity.android`，1 位用户/1 个模板。
- 验证拒绝 `THRESHOLD_UNVERIFIED`；无图库识别拒绝 `GALLERY_NOT_FOUND`；随后发布图库
  转回 `THRESHOLD_UNVERIFIED`，图库仍未发布。使用不同错误状态的转换确认操作结果。
- 删除当前用户后 0 用户/0 模板，显示“物理清理已完成”；删除全部并停用后“需要授权配置”。
- 核对摘要后删除本次两份公共输入。清除图片预览并冷启动，PID32138 显示
  “身份模型已安装；可启用本机注册”，仍“需要授权配置”，无需保留原 ZIP 或重新同意。
- 原 main/test/runner、12 份已有模型/附属摘要、相机麦克风权限保持不变；新增安装的
  YuNet/SFace 及 manifest 摘要已核验并保留。不导出模板、embedding 或密钥内容。

## 来源与证据边界

App SHA256：`afed156b40bcd8be0bcf0c5b373a0b30dd5a3daa4c7bd392b3655c7b6926c461`。
九个相关 Identity 源文件与该 APK 的既有构建记录一致，见 `source-binding.json`；这只是局部
来源绑定，不代表全部当前源码或精确 b90 候选验收。此前 b90 的 USB 中断记录继续保留为未通过。
这是自动操作普通 UI 的验收，不是人手触摸、Apple UI、识别准确率、PAD 或发布验收。

本地 ignored 原始证据：`artifacts/verification/android-identity-retained-app-ui-20261106/`。
`diagnosis.json`/`initial-ui.xml` 为恢复前失败记录，未计入成功证据。
只读核验：

```sh
rtk proxy python3 artifacts/verification/android-identity-retained-app-ui-20261106/verify.py
```

## 下一步与停止重复工作的边界

不重复本次注册、旧 TTS、F09 构建或失败 Apple 自动化工具。剩余门槛是 Apple 普通
picker/生成与待答复的现有组合 App 更新、F02 生产 Skiko 所有权/原资源验收、F09/F12
独立审查与分发决策、F10 授权评估数据与校准。最低系统、长期质量/资源等用户后置项仍未验证。
Android 重连不构成覆盖 Apple 当前会话的授权；现有 IPA 直接复用，毋须重建。

10/12 是整阶段计数，无法表达本次子项关闭。之后更新须同时报告已关闭的功能子项与剩余
阶段门槛，不再用新增证据或测试数量替代功能进度。

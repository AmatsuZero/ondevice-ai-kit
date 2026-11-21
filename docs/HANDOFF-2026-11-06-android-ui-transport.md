# Android 普通 Identity 验收中止与恢复 — 2026-11-06

继续 [前一功能交付](HANDOFF-2026-11-06-model-folder-import.md)。本轮没有关闭新的 roadmap
验收项，也没有新增构建、测试通过数或 benchmark 样本。`5671cad` 已保存模型文件夹导入交付
记录和对应 readiness 指针；产品代码仍是 `df46772`。

## 尝试与停止点

在原授权 TECNO KM9/API35 上，从普通人脸页开始，核对原 APK、test/runner、12 份原模型
及附属文件摘要、相机/麦克风权限。将既有精确 `b90e7830…a64d8d` 候选临时 `install -r`，
不构建新 APK、不清数据。原 APK 的完整回退副本先复制到设备并核对 SHA。

普通控件进入 Identity 页，实际显示“请导入固定版本 YuNet/SFace ZIP 模型包”；这台手机
尚无可用 Identity 模型。复用既有获授权的固定 YuNet/SFace ZIP 与 NASA astronaut 图片，
仅复制到唯一临时 Download 路径，ZIP 内两份 ONNX 与固定模型摘要一致。
普通“导入身份模型”打开系统 DocumentsUI，指定 ZIP 已显示，但选择动作未成功确认。

USB 多次在 `device`、`unauthorized` 与缺席之间切换。首次回退包传输 EOF 后检查候选副本
完整、回退路径缺席，只补传缺失副本。后续 UI 观察/操作又遇断连，故停止交互验收，
没有进入“同意并启用本机注册”或“注册”。不把文件出现、页面显示或换包当作验收通过。
没有采音、拍照、Photos 写入、私人媒体读取、下载或新模型授权请求。

## 已恢复，普通 App 可启动

- 原 main APK 恢复为 `afed156b40bcd8be0bcf0c5b373a0b30dd5a3daa4c7bd392b3655c7b6926c461`。
- 原 test SHA `7b9e47ce…9493a621d`、Identity instrumentation runner、12 份模型/附属摘要、
  相机/麦克风权限与前置快照一致，没有替换测试包或清 App 数据。
- 换包后原系统 DocumentsUI 留在任务顶端；单独 `am start` 没有证明 App 进程启动。
  退出记录只有本次 package update，没有新增崩溃记录。取消本轮选择器后，`am start -W`
  确认原 MainActivity `Status: ok` / `LaunchState: COLD`，随后 PID **24632**。
- 精确删除本轮两份设备临时 APK 和两个公共输入副本；九个保护路径及生产 MLX lock 摘要不变。

原始记录和只读复核：

```sh
rtk proxy python3 artifacts/verification/android-identity-physical-ui-20261106/verify.py
```

这里只证明失败边界与恢复，不证明 Identity 模型导入、注册/删除、校准或普通真机流程通过。
不要重复此次不稳定 USB 下的换包/观察循环；稳定且获授权的传输连接具备后，才恢复普通
Identity 模型 ZIP →显式同意→获授权图片→注册→未校准拒绝→删除停用验收。

Apple 新导入版 IPA 沿用原产物，更新选择仍待答复；此轮没有操作 iPhone。
P4/P11、独立审查、分发/校准和用户后置专项继续保留，目标未完成。

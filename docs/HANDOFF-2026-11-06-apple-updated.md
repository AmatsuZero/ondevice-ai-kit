# Apple 组合 App 已更新 — 2026-11-06

用户明确同意“可以结束当前会话并更新”后，已原位安装既有 `df46772` 签名产物，
普通无参数启动成功（初始PID2891）。**人工验收已完成，清理后冷启动PID2911留在前台。**
无需重新构建、签名或下载；基础10/12、P4/P11与发布仍 open/not-ready。

## 已验证

- 原 IPA SHA256 `f96915105a6f3c987496c691b1f56770e515f5678885d9856d25b152a7004d87`，
  安装前完整签名 App tree 与冻结记录匹配，安装成功。
- 旧 PID2562 在本轮更新前已自然结束；两次新进程检查均无该 App 进程，未额外发送 terminate。
- 同一 bundle 更新后 URL 改为新安装地址；其余42个 App 的安装地址与集合保持不变。
- 更新前 installed manifest 匹配固定 SmolVLM2；更新后完整回读14个模型文件，逐文件SHA256
  与固定描述一致，manifest相同；全部文件大小和最后修改时间也保持不变。没有重复制模型入手机。
- 普通启动无注入参数；新进程路径指向新安装，截图显示“本地图像理解模型已就绪”，
  模型文件夹导入按钮按设计停用。最初Identity模型未导入；随后人工流程已完成，见下节。
- 首次host核验错误比较带auditToken的launch字典与不带该字段的进程字典，导致断言停止；
  实际PID/路径一致。已记录并修正，只继续尚未执行的模型回读，没有重装/重启App。
- 九个保护路径与生产MLX lock保持；没有拍照、采音、Photos写入或自动重试失败的UI工具。

本地原始证据：`artifacts/verification/apple-combined-folder-import-20261105/runtime-update/`。
只读核验：

```sh
rtk proxy python3 artifacts/verification/apple-combined-folder-import-20261105/runtime-update/verify.py
```

## 新增人工验收：普通 Files 单图生成与 Identity 文件夹导入

用户手动进入“图像理解”，确认“单图／尚未选择输入”；经真实系统Files进入
“我的iPhone → IdentityInput → approved-astronaut.png”，保持默认prompt并点一次生成。
用户报告SUCCEEDED，系统截图也显示SUCCEEDED、实际后端`mlx.smolvlm2-500m`、固定
revision `fa57db46815177fbdfd65cc85a2b3416a8332268`及非空宇航员描述。
独立provider中该公开NASA文件已回读核对SHA。这里是一次普通人工选图/生成通过，
不是准确率或更广自然质量通过；输出中出现standing等描述，不据此给模型质量结论。

用户随后在真实Files中进入IdentityInput/models；两个ONNX灰色不可单选是folder模式的
正常行为，右上角蓝色“打开”可用。用户点“打开”后报告成功、同意按钮可点；系统截图
显示“身份模型已安装；同意后启用本机注册”。这关闭新版组合App的Identity文件夹导入子项。

用户已显式启用30分钟会话，通过“从文件选择图片”选择同一公开NASA图片，到“身份与活体”
点一次注册。用户确认成功，系统截图直接显示`ENROLLED · opencv.identity.apple`、1用户/1模板。
随后用户确认验证拒绝`THRESHOLD_UNVERIFIED`、删除当前用户到0/0，再删除整会话并停用。
系统截图直接显示“本次所有注册已删除并停用”；中间拒绝/0计数以用户目击记录，不冒充有
独立逐步截图。未导出模板/embedding/密钥。用户已清除图像理解输入，截图直接显示“尚未选择输入”。
此前自动触控工具失败，未重试或切换输入通道绕过；仅用受支持的系统截图观察用户操作结果。

图像理解模型的自动复用不证明新MLXVLM模型文件夹picker导入/取消/错误恢复，该项仍未验。
普通单图生成和Identity导入/注册/拒绝/删除停用依据上述人工证据分别通过；完整物理交互矩阵、
自然质量、PAD及校准/发布仍未通过。用户结束会话/更新的授权已取得，不再次询问。

## 已完成收尾与恢复入口

只终止本轮普通启动的精确PID2891，重新无参数启动同一新版App（PID2911）。首张截图
处于校验/系统切换动画，未当作稳定通过；随后同一进程的稳定截图同时显示Identity模型
已安装、可重新同意（会话关闭），图像理解本地模型已就绪、图片预览为空。没有再次推理。
保留两个模型库、原42个其他App和既有provider测试素材；没有模板/embedding/密钥导出。

人工记录、截图及冷启动来源的一致性复核：

```sh
rtk proxy python3 artifacts/verification/apple-combined-folder-import-20261105/runtime-update/verify-manual.py
```

该脚本核验记录完整性和进程来源，不冒充OCR或自动重放；注册/终态有实际截图，
未校准拒绝及中间0/0以用户目击确认。不要重复已通过的两条人工流程。

下一步不再卡在Apple更新或这两条普通流程。剩余为完整物理交互矩阵、新MLXVLM文件夹
导入/取消/重试的干净存储验收、质量/资源/最低系统（按原后置边界）、F10授权校准/PAD数据、
F02生产所有权、F09/F12独立审查与分发决策。没有因此升级P4/P11或发布结论。

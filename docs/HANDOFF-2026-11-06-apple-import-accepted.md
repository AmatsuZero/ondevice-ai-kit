# Apple 模型文件夹导入人工验收已完成 — 2026-11-06

本记录接续[隔离验收准备](HANDOFF-2026-11-06-apple-import-check.md)，替代其“等待用户取消”的当前状态。
用户操作已全部完成；无需再次取消、导入、生成或杀 App。本次关闭 **1 个实际验收子项**，
基础里程碑仍 **10/12**，P4/P11 与发布仍 `open/not-ready`。

## 已完成的普通流程

- 在隔离 **ODAI Import Check** 中打开 Files 后取消；用户确认，返回截图显示模型未就绪、导入仍可用。
- 选择不兼容的 `IdentityInput/models` ONNX 目录后仍未就绪，可重试。
- 重试选择 `IdentityInput/ODAI-MLX-14-20261106`，模型就绪、导入按钮停用。
- 普通 Files 选择已有公开 `approved-astronaut.png`，默认 prompt 生成一次。
  用户报告 `SUCCEEDED`；结果截图直接显示非空宇航员描述及正确 backend/revision。
  结果截图本身未显示 phase；后续清除输入截图另可见 `SUCCEEDED`，不冒充自动 UI 回放。
- 用户清除输入，截图确认“尚未选择输入”。仅结束本轮 PID2954，普通无参数冷启动 PID2964；
  稳定首屏模型就绪、导入停用、预览为空，未再生成。

安装目录完整回读的 **14 文件 SHA256** 与固定 manifest 全部匹配；导入前后同 PID2954，
隔离 App 无私有 `Documents/model` 回退源。模型实际通过人工 Files 导入，不是 host 注入隔离 App。
冷启动和原 Combined 的安装 manifest 均与 canonical 相同。模型为
`mlx-community/SmolVLM2-500M-Video-Instruct-mlx`，revision
`fa57db46815177fbdfd65cc85a2b3416a8332268`。

## 最终设备状态与复核

只结束隔离冷启动 PID2964，回到原 `io.ondeviceai.combined.demo` PID2911；
最终首屏 Identity/MLX 模型就绪、注册未启用、预览为空。全部 **43 个原有 App 安装 URL** 保持，
仅新增隔离 App；隔离 App 与模型保留但进程已停止，IdentityInput 原始测试素材保留。
没有新构建、下载、签名资产变更、采集、benchmark 或远端操作。

原始证据在本地 ignored 目录 `artifacts/verification/apple-model-folder-ui-20261106/`，
当前验收结论是 **`accepted-summary.json`**；原 `summary.json` 是不可改写的准备快照。

```sh
rtk proxy python3 artifacts/verification/apple-model-folder-ui-20261106/verify-acceptance.py
rtk proxy python3 tools/validate_p11_readiness.py artifacts/verification/p11-readiness-20261106-apple-import-accepted/summary.json
```

复核器检查原始记录、哈希、模型和进程恢复证据；人工动作与视觉观察按来源保留，
不声称重新执行 UI、OCR 或完整功能矩阵。readiness 只刷新一次，8 个 benchmark 分组、0 新样本，
状态及既有未验缺口保持；九保护路径和生产 lock 保持。

## 后续与停止边界

本轮已收尾，无待恢复命令或待用户操作。不要重复已完成的 Android Identity、Combined 两条普通流程或本次导入流程。
完整路线仍缺 F02 生产 Skiko 归属/分发与原资源验收，F09/F12 独立审查及再分发/生产交付决策，
F10 获批真实/攻击数据、PAD 协议/runtime/校准；这些门槛不能由固定图片生成代替。
用户后置的最低系统、长期质量/资源、Android 异步 LM 与进程死亡角落保持未验；失败的 Apple UI 工具不重试。
本次仅本地文档/manifest 提交，不 push/merge/tag/release，不把整个目标标记完成。

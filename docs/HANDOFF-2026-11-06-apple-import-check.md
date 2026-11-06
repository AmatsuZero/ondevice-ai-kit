# Apple 模型文件夹入口：隔离验收起点 — 2026-11-06

接续[新版已更新与人工验收](HANDOFF-2026-11-06-apple-updated.md)。主App的两条人工流程
已通过，不重复。本次针对另一个尚未验收的入口：MLXVLM固定模型文件夹导入/取消/重试。
主App已有模型，按钮按设计停用；没有为此移除或替换其模型。

## 本轮实际准备

- 从既有df46772签名App复制隔离副本，bundle为`io.ondeviceai.importcheck.df46772`，
  显示名 **ODAI Import Check**。复用原通配profile/既有identity；仅副本bundle/display元数据
  与外层签名变化，三个Mach-O section map一致；原签名包/签名资产/生产lock/九保护路径保持。
  这是新的隔离验收副本，不是重新构建或重签原组合App。
- 新bundle安装前缺席，安装后只新增这一个；全部43个原有App安装地址保留。
- 既有固定14文件逐SHA校验后，复制到测试Files源App的全新独立目录
  `我的iPhone → IdentityInput → ODAI-MLX-14-20261106`。设备14个文件名/大小与固定manifest匹配；
  不下载、不覆盖原`models`/NASA输入。尚未把源传输成功当作模型安装或运行通过。
- 普通无参数启动隔离App，PID **2954**。已观察“本地模型未就绪；可重新导入
  固定版本模型文件夹”和蓝色可用导入按钮；没有注入源路径或发起推理。

现有`security cms`解码在普通sandbox失败，改用只读OpenSSL CMS验证解码profile成功；
只输出通配AppID/有效期/设备在册，不创建或修改签名资产。实际codesign及strict验证成功。

## 当前停止点与直接下一步

隔离App留在前台。已通过异步问题请用户点蓝色 **导入图像理解模型文件夹**，打开Files后
点右上角×取消，反馈按钮仍可用/模型仍未就绪；截至本检查点尚无答复，未观察取消动作。
这是依赖人工交互的停止点，不是可继续后台运行的命令，不能假装仍有测试在执行。

收到答复后直接继续，**不重装、不重签、不重新复制1GB模型**：

1. 记录取消后的实际状态；此前失败UI工具不重试，不换输入通道绕过。
2. 再次导入，选择已有不兼容的`IdentityInput/models`文件夹并点打开，确认拒绝/可重试。
3. 再选正确`IdentityInput/ODAI-MLX-14-20261106`文件夹，点打开；确认模型就绪、按钮停用。
4. 回读隔离App安装模型身份；仅在成功导入后做一次公开NASA图片普通生成，清除输入。
5. 只结束本轮隔离进程并回到原ODAI Combined；保留原App、模型、测试素材与原始证据。

当前只完成准备，**0个新验收子项通过、0新增benchmark**；不改变10/12、P4/P11或发布结论。
原始证据：`artifacts/verification/apple-model-folder-ui-20261106/`。当前只读核验：

```sh
rtk proxy python3 artifacts/verification/apple-model-folder-ui-20261106/verify-artifact.py
```

该核验覆盖签名副本/来源/14源文件/43原App保留/无参数启动，不覆盖尚未执行的人工步骤。

# P4 相机闪动与前后台恢复

日期：2026-10-16。此包处理用户实际反馈的两个 Demo 问题，只完成 P4 的相机控件稳定及前后台恢复部分。

## 现象 → 根因 → 修复 → 防护

| 现象 | 根因 | 修复 | 防护 |
| --- | --- | --- | --- |
| 开相机后界面快速闪动 | 每帧沿用单图路径，清空结果并切换进度条、按钮与结果行 | 新增 `detectFrame` 流式状态，保留上次结果，连续帧期间保持输入控件可用；单图仍显示进度 | 共享 ViewModel 两个新增回归用例；真机连续帧 UI 用例 |
| 前后台切换后相机不再输出 | 失活时停止捕获，未保留恢复意图和前台入口 | 区分暂时暂停和用户停止，令牌使过期权限回调失效；恢复等待取消中的桥接推理真正结束，再重建帧队列；会话操作与帧回调使用独立串行队列 | 生命周期检查 7 个场景；真机两轮 Home→返回、停止后返回及手动重启 |

## 验证结果

- 真机：iPhone 16 Pro Max，iOS 27.0.1。`CameraLifecycleUITests` **2/2 通过，0 失败，约 26.46 秒**。帧计数只在实际原生推理成功时增长；测试验证帧持续推进、结果行存在、无逐帧进度条、照片/benchmark 按钮可用。
- 首次 UI runner 在执行测试前因 “Timed out while enabling automation mode” 失败，属于自动化启动阻塞，不能作为行为 RED。停止本次遗留诊断/控制台进程后，以单 runner 重试通过。
- 共享 ViewModel iOS 模拟器测试 **4/4**，其中两例新增流式状态回归。新增 API 先经历缺失编译 RED，再实现为 GREEN。
- `sh scripts/check-camera-lifecycle.sh`：实际 Swift 状态代码 **7 个场景通过**，覆盖暂停恢复、显式停止、过期权限令牌和快速切换；已加入 `verify-apple.sh`。
- Apple 主机 XCTest **25/25**，桥接 typecheck、原生消费者和 KMP Demo 模拟器构建通过；设备 KMP framework 与签名 App 构建通过。
- Android 统一检查通过：JVM 测试、lint、Demo/原生消费者 APK 构建，290 个任务；此结果不代表 Android 设备相机验收。
- `git diff --check` 通过。真机结果包和控制台日志保存在忽略的本地 artifacts/ledger 内，不提交相机照片或设备标识。

## Android 真机补充（2026-10-16）

- 设备：TECNO KM9，Android 15，API 35，`arm64-v8a`；ADB 序列号不写入仓库。
- 修复前截图确认相机每帧走单图 `detect`：Compose 反复显示进度条并暂时禁用“选择图片”。根因是相机分析器未使用已有的 `detectFrame` 流式状态入口。
- 修复：`MainActivity` 相机分析器改为 `model.detectFrame(...)`，保留上一帧结果并保持输入控件稳定；照片路径仍使用单图 `detect`。
- 验证：`./gradlew :demo:shared:allTests :demo:androidApp:assembleDebug` 通过；安装 `io.ondeviceai.demo` 后，真机相机完成人脸检测，结果显示 `人脸 1`，UI dump 显示“选择图片”和 Benchmark 均为 enabled；3 秒 `logcat` 无 `FATAL EXCEPTION`/`AndroidRuntime`。
- 该设备覆盖现代 Android API 35，不替代 API 26 下限验收；未提交截图或用户照片。

独立 reviewer 返回部分意见后因模型容量限制中断，未形成完整审查结论。其提出的 benchmark-smoke 导出问题经对照 HEAD 核实：该路径在基线中已存在，输出指标和 fixtureHash、不输出图像像素，也不标记为合成空白证据；因此不作为本次新增的隐私回归。该路径及 benchmark→相机切换的取消等待由 P5 另行审查。此次执行了本地 diff 自查、项目配置重复执行、Ruby/plist 检查与上述构建/测试，不能等同完整独立审查通过。

## Android API 35 交互补充（2026-10-16）

- 设备：TECNO KM9，Android 15，API 35；使用 ADB 操作已安装 Demo，未保存设备标识或截图。
- 选图取消：打开系统 Photo Picker 后点击“取消”，返回 Demo，原有控件和状态保持可用。
- 选图推理：选择一张现有图片后，UI 显示 `人脸 1`，按钮保持可用；3 秒日志未发现 `FATAL EXCEPTION` 或应用 `AndroidRuntime` 崩溃。
- EXIF：使用本机生成的 Orientation 6 合成 JPEG（原始 160×80），经 Photo Picker 选择后结果显示为 `80×160`，证明 Android 读取 EXIF 并旋转后再送入推理。该临时文件已从设备删除。
- 权限：撤销 CAMERA 后重新启动，系统权限对话框出现；点击“不允许”后回到 Demo，再授予 CAMERA 并重新启动相机，成功得到 `人脸 1`。没有改动系统其他权限。
- 本次验证没有覆盖持续摄像头内存/热量、API 26、系统设置页恢复按钮或 iOS 照片选择器；这些仍保持未验证，不以本次结果替代最低版本验收。
- 尝试在已连接的 iPhone 16 Pro Max 上重跑 UI 测试时，`xcodebuild test` 在构建阶段因 Demo 与 UI Test runner 均无匹配 provisioning profile 退出（exit 65）。已确认本机有 Apple Development 身份，但未使用 `-allowProvisioningUpdates` 创建账户资产；此次未产生新的 iOS 行为结论，此前相机 UI 结果仍以 P4CameraFinal 记录为准。
- iPhone 18 Pro / iOS 27 模拟器新增 `testPhotoPickerCancellationReturnsToDemo`：打开 PHPicker、点取消后，Demo 的选图和相机按钮仍可用。`xcresulttool` 确认 1/1 通过、0 失败。首次测试筛选写错而执行 0 个用例，该次不计为通过；模拟器结果不替代真机。

## 剩余 P4 范围

剩余 P4 范围为 iOS 真机照片/取消、旋转/镜像、权限恢复的真实 UI，以及两端持续资源/热量测量。Android 现代 API 35 的相机和照片交互已完成；API 26 下限仍等待设备。未开始 P5 benchmark；本次单图耗时不作为性能报告。

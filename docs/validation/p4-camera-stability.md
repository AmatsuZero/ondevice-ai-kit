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

## 剩余 P4 范围

照片选择/取消、EXIF 和旋转/镜像、权限拒绝/设置恢复的真实 UI、资源释放及持续相机内存/热量仍需逐项验收。Android 现代 API 35 已完成相机稳定性冒烟，API 26 下限和完整照片路径仍未验收。未开始 P5 benchmark；此次成功帧计数和单次 smoke 耗时不作为性能报告。

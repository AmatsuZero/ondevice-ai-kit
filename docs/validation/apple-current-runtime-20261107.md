# 当前 Apple 候选真机原生链路 — 2026-11-07

2026-11-07 后续状态审计：原`freeze.py --verify`命令的最后一条全局manifest摘要断言已因
后续Identity benchmark索引更新而失效，原命令本次exit1，不能写成当前全量通过。
440份冻结文件、同一2个XCTest方法/86检查、18输入与恢复条件另行只读复核通过；
详见[补充核对与边界](roadmap-status-audit-20261107.md)。原冻结脚本、summary和设备报告保持原样，
不重跑设备。下方命令是原冻结时的验证入口，其全局指针条件不再代表当前manifest。


同一 `636a6eb8854db5dff74244c42c3ce0e82bd2be9f` [组合候选](apple-current-candidate-20261107.md)
在 iPhone17,2 / iOS27.2 完成真实图片、视频生成和 OpenCV 公开图片特征提取。
生产 factory → host → KMP ViewModel → Swift bridge → MLXVLMBackend 链路通过，
包括输入复制/清除、成功回调、close/drain 后模型 lease 释放。关闭这项当前原生链路缺口；
普通 Files 交互、其他能力、自然质量/资源/校准与完整 F12 仍未完成。

## 候选身份与测试边界

使用上轮已经构建的组合设备 App，没有重新编译或链接宿主，也未重跑 82 项桥接或未变化的 host/KMP 套件。
原 unsigned 产物不变；独立副本仅改变 bundle ID/显示名并复用现有有效 profile 和签名身份。
新别名 `io.ondeviceai.candidate.c636.combined` 在安装前不存在。
没有创建或修改证书、私钥、profile、生产 lock 或九份受保护文件。

三个原有 Mach-O 的全部有效 section 摘要和 UUID 在签名及测试包装前后保持：

| 已加载产物 | 冻结 UUID |
| --- | --- |
| `SpeechDemo` | `3E3F88FC-2F07-35F2-8513-FB1D054224AE` |
| `SpeechDemo.debug.dylib` | `42687C74-B758-3929-8559-C0EF93BC099B` |
| `__preview.dylib`，包装前后核对 | `47A384E1-A725-34BD-A37D-AC24A98CD0CB` |

使用标准 **hosted 非 UI XCTest**。仅新编译测试 bundle，动态链接现有 debug dylib；
在另一份测试副本中加入 Xcode 自带的 10 项 XCTest 运行库并重新签名。
单独的身份测试及真实原生测试均在运行时核对完整 `ODAISDKCommit` 和前两项实际加载 UUID。
原 signed/unsigned App 文件树均保持。shared framework 仍保留 e0438c0 构建身份和生产 Skiko0.144.6，
不是 F02 `.6` 或 F09 补丁版。此运行方式没有操作 UI、启动 XCUIAutomation 测试或重试旧失败的 CUA/WDA。

模型和输入直接放入新别名私有 `Documents/current-inputs-636a6eb`，避免使用普通 App 自动准备的
`Documents/model`。只复用 14 份既有 MLX 文件、两份 OpenCV ONNX、公开 astronaut 图片和既有
29fps 合成视频；共 18 份输入在设备推理前后校验完整 SHA/长度，catalog 自身摘要也固定。
picker 使用受控回调返回这些 URL，随后走真实生产导入代码，**不计系统 Files 点击验收**。

## 实际结果

| 验证 | 结果 |
| --- | --- |
| 唯一 Vision smoke | 固定 64×64 白图，真实 Swift/KMP→Vision，成功、0脸、261.821 ms；未重跑 |
| 宿主身份 XCTest | 1 项方法通过，0失败/跳过；实际提交与加载 UUID 一致 |
| 原生链路 XCTest | 1 项方法通过，0失败/跳过，方法耗时8.668秒；86条内部检查全部为true |
| 图片生成 | `mlx.smolvlm2-500m`，3548.894 ms，非空输出及完整模型身份匹配 |
| 视频生成 | 同一真实后端，2356.706 ms，非空输出及完整模型身份匹配 |
| OpenCV | 1张可用脸，qualityScore1；128维，平方范数1.000000006600189；特征仅在内存使用 |
| 关闭与清理 | image/video输入副本和视频snapshot清除，host关闭，两个ModelStore均可移除已释放模型，工作目录删除 |

86 条是该方法内部的输入/身份/生命周期检查，不能写成 86 个 XCTest 用例。
一次图片和一次视频的耗时来自固定样本调用，不是新的 P11 benchmark、质量判定或资源预算通过。

图片实际输出：

> A woman in an orange space suit with a helmet on her head is smiling in front of a rocket.

视频实际输出：

> The video shows a series of images with a grid of colored squares, each with a different number and color.

输出只证明生成链路运行，未独立裁定内容准确性。
OpenCV 未注册用户、写入 embedding、访问身份 Keychain 或校准阈值；
verification/identification 的 `THRESHOLD_UNVERIFIED` 拒绝继续通过，PAD 仍 absent。

原生测试在推理前持久化唯一 runId `current-apple-native-20261107-636a6eb`，
在成功或异常路径更新报告。实际只启动一次、每种生成各一次，没有因观察故障重放。
成功私有报告两次独立回读 SHA 相同：
`473d9dd73792593d599bf86c1122c0eb98e1c689c00965883f018b2e0afe9932`。
终态报告与两份 xcresult/逐方法日志相互印证。

## 助手、配置失败与警告

cbc `hy4free` 生成器候选在 **678.693秒**自然完成，完整响应、0工具事件，没有任务超时。
主代理修正 debug dylib 的实际根目录位置及 rpath，补上 XCTest Swift overlay 搜索路径。
外部 `TEST_HOST` 先被 build-for-testing 的宿主目标选择、再被正常 build 的产物相对路径检查拒绝，
均未编译测试或运行设备。之后只构建测试 bundle，由安装版 Xcode 的 `xcodebuild.xctestrun(5)`
所述参数提供宿主，实际编译和真机执行成功。Context7 未找到对应配置正文，未据摘要猜测验收。

身份运行的第一次 CLI 预检拒绝 `-test-iterations 1`，并留下空结果目录；第二次拒绝该已存在目录。
两个原始 exit64 保留，均未开始设备测试。去掉该选项并使用新结果路径后，唯一身份测试通过；
没有开启失败重试。原生测试使用这套已验证配置，首次运行即通过。
测试初版的 optional quality score 隐式转 Any 警告在执行前通过显式解包修正，再编译后才运行。
这些均为父方测试接线问题，不计产品 RED 或产品修复。

日志保留原有 `KalmanFilter`/`VideoWriter` 与系统 Objective-C 类重名警告，以及公开 PNG 的 iCCP 警告。
本次两测试无失败，但不能据此断言这些警告没有其他影响，未在本轮更改 OpenCV/依赖源码。
助手原文、提示、命令、会话和父处置在 `.omx/artifacts/cbc-apple-hosted-runner-20261107/`，
其产出是生成器候选，不是完整源码/独立发布审查。Qoder403、原生429等线路未重启。

## 恢复与后续

Vision进程按精确PID/可执行核对后停止；两个 XCTest 宿主均自行结束。
已卸载本轮唯一新增的候选别名，随之移除它的测试库、私有模型副本和报告；
原有 **44个App安装URL逐项相同**，普通 `io.ondeviceai.combined.demo` 已返回。
原有其他候选和普通 App 容器没有被本轮复制、删除或替换操作针对。
九保护路径、生产lock、原签名profile及原构建产物摘要保持。

不再重跑本项推理、宿主身份检查或既有构建。继续尚缺的其他能力/普通交互、
实际质量资源、完整复审、来源许可和生产交付；最低系统设备及授权校准数据仍按既定边界保留。
无新采集、下载、上传、远端推送或 P11 样本；P11仍10组，基础 **10/12、P4/P11 open、release not-ready**。

ignored 本地证据：`artifacts/verification/apple-current-runtime-20261107/`。
440份原始日志/收据/源码/助手和终态xcresult固定，summary SHA为
`6856a87070507ab379936088f6c8467b912e66d7cade16b66d9067f827f7126d`。
只读复核，不操作设备、构建或运行模型：

```sh
rtk proxy python3 artifacts/verification/apple-current-runtime-20261107/freeze.py --verify
```

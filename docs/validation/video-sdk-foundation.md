# Video SDK Foundation 本地验证

日期：2026-10-21；工作树 `.worktrees/foundation`，分支 `feat/sdk-foundation`。契约 `17a330e`、Swift client `fd2e537`、Android client `112634d`。最新分发/审查提交以 Git 为准。

当前范围是宿主可注册后端的双端视频客户端库，没有内置视频处理器，不代表真实超分、音轨保留或 P7 完成。下一包接入 `apple.videotoolbox.sr`，随后 KMP/Demo 与 Android 模型；既有探针的真机与质量边界见 [P7 记录](video-sr-backends.md)。基础系统下限保持 iOS15/macOS12、Android API26。

## 契约与生命周期

Swift `ODAIKitVideo` 只依赖 Core/Foundation；Android `android:video` 只依赖 Core、Kotlin 与已有 coroutines1.10.2。请求使用本地文件、整数倍率≥2，默认 preserve 音轨；后端声明支持倍率/音轨与独立 availability。输出 metadata 和 progress 做数值验证，Explicit missing/unavailable/不支持选项均不回退。

`VideoProcessingClient` 的单槽覆盖 availability、process、drain、未交付输出清理。取消会传给独立 worker，终态等待后端返回和 drain；晚到成功 output lease 在无取消的清理上下文释放，失败向调用方抛出。成功交付的 lease 归调用方，必须显式 `release()`；client.close 不删除这些输出。close 拒绝新任务/query，等待进行中任务与 query，之后每个 backend 关闭一次；并发 close 共享完成。旧 generation 与倒退进度被抑制。回调在协调上下文串行执行，调用方应避免阻塞。

## 本地证据

所有日志位于 `.superpowers/sdd/2026-10-21-video-sdk-foundation/`，产物位于 `artifacts/verification/video-foundation-20261021/`，均被忽略。

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Swift 契约 RED→GREEN | 4/4；最初未注册 target 的0测试不算证据 | `task-1-red.log`、`task-1-green.log` |
| Swift client RED→GREEN | 8/8；availability/process/drain barrier、late lease释放/失败、close/query join、generation与输出所有权 | `task-2-red.log`、`task-2-green.log` |
| Swift 全套 | 426/426、0失败 | `task-2-full.log`；Apple门禁输出 `artifacts/verification/swift-tests.log` |
| Android RED→GREEN | 7/7、0失败，video lint通过 | `task-3-red.log`、`task-3-green.log`；`android/video/build/test-results/testDebugUnitTest/` |
| 分发生成器 RED→GREEN | 5/5；真实xcodeproj检查8产品、Video全源/仅Core依赖、重复生成不变 | `task-4-red.log`、`task-4-green.log` |
| 工具全套 | 78/78 | `task-4-tools.log` |
| Apple 总门禁 | exit0；含Swift/KMP/bridge/方向/ASR fixture和原生consumer/Demo构建 | `task-4-apple.log` |
| Android 总门禁 | exit0；610tasks，6 executed/604 up-to-date | `task-4-android.log` |
| Apple 本地分发 | fresh8XCFramework，device/simulator arm64 | `task-4-package.log`、产物 `archives/` 与 `xcframeworks/` |
| Core+Video 独立消费者 | iOS15 arm64 与 Simulator15 arm64 编译/链接通过 | 产物 `consumer/{iphoneos,iphonesimulator}/{build.log,linked.txt}` |
| Android 隔离Maven | Core/Video本地发布、POM仅Core/Kotlin/coroutines；独立二进制消费者24tasks构建成功 | `task-4-maven.log`、`task-4-maven-consumer.log`、产物 `maven-dependencies.txt` |

工具测试与生成器使用同一 Ruby3.4.11 wrapper；系统 Ruby2.6 与当前 json gem 不兼容，环境失败不算产品 RED。生成器回归允许系统 Foundation.framework，SDK target依赖仍严格只有 Core。Android消费者需与仓库一致的 Kotlin2.4.0，AGP9.1默认内置2.2编译器无法读取本仓2.4 metadata；不关闭 metadata 校验。

复核入口：

```sh
rtk proxy swift test --disable-sandbox --package-path apple
rtk proxy python3 scripts/gradle-local.py :android:video:testDebugUnitTest :android:video:lintDebug --console=plain
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -v
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby sh scripts/verify-apple.sh
rtk proxy sh scripts/verify-android.sh
```

重新打包必须选择新的 `ODAI_ARTIFACTS_DIR`，脚本拒绝覆盖已有输出。Maven本地输出使用绝对 `-Dmaven.repo.local=...`；相对路径会按Gradle daemon目录解析。此次没有远端发布或push。

## 未通过或不在范围的门禁

独立 code-reviewer 已尝试但服务返回 Selected model is at capacity，未产生审查结论，保持 pending，不记 APPROVE。作者范围审读不是独立审查。下一包 researcher 同样429失败，不视作资料确认。本地通过不能代替真实编解码、色彩/传递函数、时间戳、音轨、质量、峰值资源、离线或最低系统真机验收。输出lease接口要求后端幂等释放，但本包没有实际文件后端；文件唯一目录/清理正确性将在该后端包验证。没有新增第三方依赖版本、下载模型、采集音频、修改签名资产或触碰受保护的原Demo工程与用户验收草稿。

# Android 系统 ASR 能力与语言元数据探针（W3）

这是一个仅供验收运行的原始 Android `Instrumentation`，不属于产品 API。实现见
[SystemAsrCapabilityInstrumentation.kt](SystemAsrCapabilityInstrumentation.kt)。
它复用已有 system-ASR 生产模块，不新增依赖或构建 harness；应放入现有、测试 APK
已包含该生产模块的 `androidTest` classpath。

## 构建与运行

AGP 9 内建 Kotlin 必须通过 Kotlin source set 注册源码，不能只设 `java.srcDirs`，
否则 Kotlin 编译任务可能 `NO-SOURCE` 而 APK 仍成功打包：

```kotlin
sourceSets.getByName("androidTest") {
    kotlin.directories.add("src/androidTest/kotlin")
}
```

使用现有测试模块、variant、runner 配置和构建任务；不要为此另建 harness 或加依赖。
构建后核对 DEX 确有探针类。使用唯一临时 package/application ID，避免与普通测试包及
provider authority 冲突；运行后移除这些别名并核对包清单恢复。不要改动普通 App。

冻结生产模块与探针的逐文件摘要清单，将其摘要作为 64 位小写十六进制
`sourceFingerprint`；每次使用唯一、仅含 `[A-Za-z0-9_-]` 的 `runId`（1–80 字符）。
两者都由调用方显式传入；fingerprint 只是报告元数据，不会自行证明 APK 内容。
示例需替换为已核实的值，并明确指定设备序列号、测试包名：

```sh
rtk proxy adb -s "$SERIAL" shell am instrument -w \
  -e runId "$RUN_ID" -e sourceFingerprint "$SOURCE_FINGERPRINT" \
  "$TEST_PACKAGE/io.ondeviceai.systemasr.SystemAsrCapabilityInstrumentation"
```

将逐文件源码清单与实际 APK 字节/SHA-256、manifest 中 package/target/runner、
DEX 中生产类和探针类，以及 `apksigner verify --print-certs` 的签名身份绑定核验。
命令退出码不是结果：读取 instrumentation code 和持久终态报告。

报告位于目标 App 私有目录
`no_backup/w3-system-asr-capability-probe/<runId>.json`；按相同显式序列号和目标包
拉取该精确路径，并校验原始字节、runId、terminal、status、outcome 和两个 locale 行。
`running` 非终态不是成功。父级本地设备收据仅供本机核对，不随本文档分发。

## 行为边界与已记录结果

探针查询生产后端可用性，以及 `en-US`、`zh-CN` 资源元数据；资源检查可创建并销毁
生产检查所用的查询 recognizer。它不调用识别、不录音、不请求或授予麦克风权限、
不下载、不改网络/设置/普通 App。`NOT_REPORTED` 仅是系统返回的元数据，不代表已安装。

父级分别记录了两种设备状态，不能合并推断：

| 环境 | 设备服务与语言查询 |
| --- | --- |
| 实际 API 36 emulator | 无 on-device 服务；`querySupported=false`、`UNSUPPORTED_DEVICE`；两个 locale 均 `queried=false`。 |
| 实际 TECNO API 35 physical device | 服务存在、`querySupported=true`；`en-US` 与 `zh-CN` 均实际 `queried=true`，`assetStatus=NOT_REPORTED`。 |

两次均无音频、识别调用或下载；麦克风权限为 denied 且未改变；临时别名包已移除、
包清单已恢复。以上是元数据证据，不是识别成功、离线 ASR 或语言质量通过，也不代表
whole-W3 完成；W3 仍开放。不要写成“installed”或“offlineASR passed”。

## 验收边界

- `METADATA_QUERY_EXECUTED` 只表示元数据查询完成；不能替代真实转写、离线行为或质量验收。
- denied `RECORD_AUDIO` 是已记录的前置条件缺口；不得为这个仅查询元数据的探针授予权限。未来另行授权的识别 workload 不在此禁止范围内。
- `canObserveDownload` 是 API level 标志，不证明服务支持下载；探针不触发下载。

源码接入与绑定检查的本地记录（不随分发）：
`artifacts/verification/asr-current-source-harness-20261108-0010/package-verified.json`。

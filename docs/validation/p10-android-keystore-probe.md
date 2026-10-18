# P10 Android Keystore envelope 真机探针

日期：2026-10-18。设备：TECNO KM9，API 35。结果：**通过**。本记录仅覆盖原生密钥与 envelope，不代表完整 IdentityStore 生命周期验收。

探针 runner 必须同时要求 `INSTRUMENTATION_CODE: -1` 与 `probeStatus=pass`；`probeStatus=fail` 或其他完成码都必须按失败处理。`forceFailure=true` 仅用于 androidTest 的受控失败验证，不是生产 API。

## 已执行

```sh
rtk proxy ./gradlew :android:identity:assembleDebugAndroidTest
rtk proxy /Users/daubert/Library/Android/sdk/platform-tools/adb -s 15365375C9007986 install -r android/identity/build/outputs/apk/androidTest/debug/identity-debug-androidTest.apk
rtk proxy /Users/daubert/Library/Android/sdk/platform-tools/adb -s 15365375C9007986 shell am instrument -w -r -e debug false io.ondeviceai.identity.test/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation
```

构建 `BUILD SUCCESSFUL`，安装 `Success`。实际 instrumentation 输出（修复后的成功路径）：

```text
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: keyBits=256
INSTRUMENTATION_RESULT: keyEncodedNull=true
INSTRUMENTATION_RESULT: probeStatus=pass
INSTRUMENTATION_CODE: -1
```

## 失败码回归验证

修复前以 `-e forceFailure true` 触发受控异常，真机输出同时出现 `probeStatus=fail` 与 `INSTRUMENTATION_CODE: -1`，证明失败会被错误地报告为成功。修复后同一命令输出为：

```text
INSTRUMENTATION_RESULT: probeError=java.lang.IllegalStateException: forced probe failure
INSTRUMENTATION_RESULT: probeStatus=fail
INSTRUMENTATION_CODE: -2
```

随后不带 `forceFailure` 的真实 Keystore 路径仍输出 `probeStatus=pass` 与 `INSTRUMENTATION_CODE: -1`。这两条证据覆盖失败和成功完成码，避免只读取 `probeStatus` 或只读取最终 code 的误判。

## 覆盖与边界

- 真实 AndroidKeyStore AES-256 密钥，`encoded == null`；合成字节 AES-GCM seal/open 成功。
- 在同一进程重建 provider 后读取同一 key 成功；密文篡改及 AAD context 替换返回 `invalidInput`。
- 精确删除探针 key 并重复删除；旧 envelope 解密与 `key(createIfMissing=false)` 均返回 `reEnrollmentRequired`，没有创建替代 key。
- namespace 为每次运行的随机 UUID。仅创建及精确删除该 namespace 的 `envelope-key`，不枚举其他 aliases、不采集图像或个人数据。
- 该结果不证明进程重启、设备重启/锁屏、完整 Store 故障恢复或安全硬件等级。API 26 最低系统与 Apple Keychain 真机路径仍待验证。

首轮通过 APK SHA-256：`656ca3b04063b86ed56e0968c0ff3baee07e5b18a6e87292b5fd731af12d600e`；本次失败码回归 APK SHA-256：`86546e4528cf6c2e68d6e475b9215ca745ffe5c8b44402d7206f508b434414b5`。探针源码及 runner 配置随本记录提交；后续构建会随 SDK 变化生成新 hash。

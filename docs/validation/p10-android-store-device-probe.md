# P10 Android IdentityStore API35 真机探针

日期：2026-10-18。设备：TECNO KM9，API 35，serial `15365375C9007986`。探针仅使用合成向量 `[1f,2f]`、随机 store/KeyStore namespace 和随机 opaque 引用，不读取照片、麦克风或个人数据。

## 构建与安装

```sh
rtk proxy env GRADLE_USER_HOME=/tmp/odai-gradle-home ./gradlew :android:identity:assembleDebugAndroidTest :android:identity:lintDebug --no-daemon --console=plain
rtk proxy /Users/daubert/Library/Android/sdk/platform-tools/adb -s 15365375C9007986 install -r android/identity/build/outputs/apk/androidTest/debug/identity-debug-androidTest.apk
```

结果：`BUILD SUCCESSFUL`、lint 成功、安装 `Success`。APK SHA-256：`62c0f831eb7da3f1cb9304749a1f04c5204c3646258f0805d6b849c0c5f17da3`。

## 成功路径

命令：

```sh
rtk proxy /Users/daubert/Library/Android/sdk/platform-tools/adb -s 15365375C9007986 shell am instrument -w -r -e debug false -e probe store io.ondeviceai.identity.test/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation
```

最终输出：`probeStatus=pass`、`INSTRUMENTATION_CODE: -1`、`noBackupRoot=true`、`templateLifecycle=true`、`persistedRevocation=true`、`cleanup=pass`、`managedKeyCount=3`。覆盖：

- `IdentityStore.open` 将根限定在 `Context.noBackupFilesDir/ondevice-identity-<random>`；
- AndroidKeyStore envelope 的 journal 与模板 payload 写入、同进程 close/reopen、opaque membership 恢复；
- 持有 TemplateLease 时删除保持 `purgePending`，释放后变为 `purged`，payload 消失，重复删除保持同一 receipt；
- 重开后 tombstone/receipt 保持，已删模板不能复活；
- 同域旧 epoch 撤销拒绝，可信 epoch=1 的新 grant 产生新 candidate，旧 candidate/template 仍撤销；
- cleanup 仅删除本次跟踪的 key IDs 与私有 root，未枚举其他 alias。

## 受控失败路径

```sh
rtk proxy /Users/daubert/Library/Android/sdk/platform-tools/adb -s 15365375C9007986 shell am instrument -w -r -e debug false -e probe store -e forceFailure true io.ondeviceai.identity.test/io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation
```

输出：`probeStatus=fail`、`probeError=...forced Store probe failure after persistence`、`cleanup=pass`、`INSTRUMENTATION_CODE: -2`。失败不会被误报为成功，且仍完成精确清理。

## 边界

此证据覆盖 API35 同进程恢复和 Store/Keystore 生命周期，不证明 API26、进程被杀/设备重启、文件系统故障注入或 iOS Keychain 真机路径；这些仍按设备矩阵单列。

# P10 Android IdentityStore API35 真机探针

日期：2026-10-18。设备：TECNO KM9，API 35，serial `15365375C9007986`。探针仅使用合成向量 `[1f,2f]`、随机 store/KeyStore namespace 和随机 opaque 引用，不读取照片、麦克风或个人数据。

## 两次 SIGKILL 后的跨进程持久恢复（2026-10-20）

本次使用新构建的探针 APK（SHA-256 `adc7e114f3798cd43a0e68c4e577504024d3e2e483d1aead9d68cc4c13e62ca0`）和新的 run ID `6b7d0f3e-b0b2-4e32-9d8c-2b4e98fca6d1`。runner 在每个阶段先对精确测试包执行 `am force-stop` 并确认没有旧 PID，再清理该包自己的报告文件；每次启动附带独立 launch nonce，报告、PID、controller 和 validator 必须一致。它不读取或输出向量、KeyStore 内容或 opaque 引用。证据目录为 `artifacts/P10/identity-restart-reviewed-android-20261020/run-6b7d0f3e-b0b2-4e32-9d8c-2b4e98fca6d1/`，该目录被 Git 忽略。

复现命令：

```sh
rtk proxy python3 tools/probes/run_android_identity_restart.py \
  --adb /Users/daubert/Library/Android/sdk/platform-tools/adb \
  --serial 15365375C9007986 \
  --apk android/identity/build/outputs/apk/androidTest/debug/identity-debug-androidTest.apk \
  --evidence-dir artifacts/P10/identity-restart-reviewed-android-20261020/run-<fresh-uuid> \
  --run-id <same-lowercase-uuid>
rtk proxy python3 tools/probes/validate_android_identity_restart_report.py \
  artifacts/P10/identity-restart-reviewed-android-20261020/run-<same-uuid> \
  --run-id <same-lowercase-uuid> --serial 15365375C9007986
```

实际结果：`create` PID `11954` 和 `delete` PID `12094` 均报告 ready，runner 在 kill 前确认当前 launcher 仍存活，再通过包 UID 的 `kill -9` 结束进程，随后轮询 `pidof` 确认进程消失；该设备上的 `am instrument` transport exit 为 0，因此证据使用 `launchAliveAtKill=true`、`killExit=0`、`deadAfterKill=true` 绑定设备端终止，不把 host exit 码当作设备进程结果。`recover` PID `12162` 和 `cleanup` PID `12243` 均以 exit 0 完成并随后 force-stop 清理测试包。四个 PID 和四个 launch nonce 互不相同，runner 内置 validator 与独立再次运行的校验器均通过：`Android identity restart report verified: two kill -9 boundaries and durable recovery`。最后一次 `pidof io.ondeviceai.identity.test` 无输出且返回未找到进程。

覆盖的恢复事实：create 后重新打开同一 store 恢复 membership；delete 进程在持有 lease、提交 `purgePending` 后被终止；recover 进程恢复 tombstone 和稳定 receipt，解密 payload 已清理、模板 KeyStore key 已删除，旧模板被拒绝；cleanup 只删除本次 run 的精确 key ID 和私有 root。校验器同时要求固定 serial、包名、instrumentation、run ID、阶段顺序和不同 PID，拒绝缺阶段、旧报告、错误信号或相同进程链。

这项证据只覆盖 Android API35 的真实进程终止边界；不证明 API26、设备重启、提交中断、文件系统故障注入或生产模型/PAD 精度。P10 仍保持实施中。

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

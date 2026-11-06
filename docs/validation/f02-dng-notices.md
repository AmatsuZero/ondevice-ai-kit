# F02 DNG 原始通知补充与 .6 本地候选

实际编译输入中的 `dng_safe_arithmetic.h` 带有 AOSP Apache-2.0 原始声明；冻结 `.4`
的 72 份材料未包含该声明。qoder 完成公开头部提取后，主代理对固定源码逐字核对，
保留缺项 RED，并在新版本 `0.144.6-ondevice-f02.6` 补齐。旧版本保持原样。
这是已证实的通知材料缺项，不据此断言二进制违反许可或取得完整许可批准。

## 来源与修改

- DNG 修订：`dbe0a676450d9b8c71bf00688bb306409b779e90`，与固定 Skia DEPS 一致。
- 原文件 SHA256：`f986ab7208e31c42e523cd7c39720ed277e91789def8f2902149f2463003935b`。
- 原始首个 C 注释加一个换行：623 字节，SHA256
  `0b85d443cfbd60b749a265ed466a2ca2a6e16afea7c07d2fe5b4039ce4655115`。
- [原始声明与来源记录](../../probes/skia-atlas-text-cache/dng-notice-supplement/DNG-NOTICE.json)
  随新候选交付；该头文件的 Apache 声明不自动扩展至同名 `.cpp`。

核对 140 个实际 DNG 编译输入的固定摘要，其中仅该文件的前导块包含 AOSP/Apache 声明。
组件根 LICENSE、NOTICE、source/technology 许可及 PATENTS 原件继续保留。新材料为原始声明
和来源 JSON 两份；另更新归属、变更说明及索引，共 74 份。其余旧材料逐字节保持，
其中历史生成数据审阅草稿保留其原时点范围，不当作当前发布证明。

`.5` 仅曾用于独立 notice JAR 的校验实验；`.6` 避免与该实验混淆。新源码副本应用已审查的
第四输入校验补丁，正常离线 Gradle publication 执行 60 个任务、78.40 秒成功。
首次受限 shell 无权写 Gradle 缓存的失败单独保留；获正常权限后的实际构建才计成功。

## 新鲜验证

- 三模块正常 publication 共 130 文件；归档仅含 115 个版本文件，排除 15 个可变 metadata/sidecar。
- 6 个 notices/source JAR 均保留全部 74 份材料；原始 AOSP 块逐字节匹配，索引及校验 sidecar 正确。
- 6 个 `available-at` 引用闭合，18 处既有源码修改声明保持，40 个嵌入 Skia 静态库摘要不变。
- SDK 根项目与独立 KMP consumer 均正常解析 `.6` 并收集 74 份材料，分别 9.97 秒和 5.35 秒。
- 新生成 unsigned generic-device App 正常构建，9.85 秒；App 内 74 份通知逐字节匹配。
  本项复用已冻结 `.4` 的两个已链接框架，仅验证新通知保留，未重新链接 `.6` SDK 框架或运行设备。

App 收集脚本首次假定只有一个 `*.app`，遇到正常生成的 UI test runner 而拒绝；
构建本身成功。保留该失败后，选择精确 `SpeechDemo.app` 核验原成功产物，没有重建。
九个保护路径和生产 Apple lock 摘要保持；没有上传、生产依赖切换、下载、签名或设备操作。

归档 108,791,304 字节，SHA256：
`2f426cf8ff6a868abbe49a919445720108c19d7e9a1e58d23ebc3707c9d18fe6`。
只读复验需要本机 ignored 证据：

```sh
rtk proxy python3 artifacts/verification/f02-dng-notice-delivery-20261106/verify.py
```

## 助手结果与后续边界

qodercli 1.1.84 / `Qwen3.8-Flash` 的首项实际任务 27.05 秒完成，返回的原始声明经父代理核验。
本次记账 cost/credits 均为 0，不据此保证未来无限免费。调用方式已补入根本地 `AGENTS.md`。
OpenCode 同日额度复查仍返回 `429 provider.free-limit: Free usage limit reached`，停止该线路；
按用户已选的 cbc hy3 完成同一公开 DNG 核查，31.24 秒。cbc hy4free 的 HarfBuzz 专项核查
206.51 秒完成。两份解释逐条处置，没有把正文非空当作正确结论或发布批准。

根 DNG 协议的伴随源码定义、HarfBuzz 的包级 Old MIT 授予均有明确原文；
不因无前导通知或未逐文件枚举就认定缺许可，也不把 vendored/生成代码一概排除。
Windows 语言 ID 页面与 MS-LCID Open Specifications 不是同一来源，不能互套授权。
来源专项的既有历史限制继续保留；不新增没有原文依据的专利或强制外部法律签核门槛。

当前工程缺项已修复，但具体 hosted 选择/本次发布授权、远端匿名消费者、完整审查和生产资源
验收继续开放。旧 `.4` 的 404 预检不适用于新 `.6`，不能据其上传。
基础 roadmap 仍 10/12，P4/P11 open，release not-ready；没有新增 benchmark 或质量样本。

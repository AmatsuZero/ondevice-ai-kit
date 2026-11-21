# W3 JVM 通知集成及独立 APK 保留

本切片把已知 JVM 子集的 10 份原许可/归属（63392B）和机械绑定索引集成到 `android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/jvm/`，资产共11份/72718B。候选来自用户指定的免费 CBC hy3，父端只按 id 绑定已给路径/revision/大小/SHA、原字节复制并追加候选 README 段落。未改 Kotlin、Gradle、依赖、版本、坐标、锁、模型或 runtime AAR。

首次 hy3 真实返回5.676s/CLI0/result success，但正文只有伪工具文字，原样保留并拒收。独立缩小清单任务28.537s交付完整JSON；另一个独立最终APK夹具候选68.832s交付三个文件。所有调用均无已观察的配额错误或真实工具事件。首调用虽传空 tools，init仍列工具目录；后续加调用级deny/空setting-sources/纯文本system prompt且缩小任务，完整交付不能证明其中单一改动的因果或工具权限保证。无付费/自动换模型/原429重试。

10份涵盖coroutines LICENSE/NOTICE、Kotlin LICENSE/COPYRIGHT、annotations LICENSE、stdlib已知Boost/Guava/GWT/ThreeTenBp许可及原归属注释JSON。模板COPYRIGHT_HEADER、Compiler NOTICE和compiler范围README有逐项排除理由。不是所有参照文件的整包复制，不把Compiler NOTICE当stdlib通知。

本地离线Debug/Release AAR、lint及隔离Maven发布通过（12.290s，435任务/29执行）。新Release AAR68052B/SHA `098bb261b5ef0da3c8a39b5fa3b61add2dc52e9a4b41c17675c9c9204c3f11ef`；旧Release四个成员全字节不变，仅增加11资产。Debug AAR69916B/SHA `9ed961698131aba5f329717da35a369085fa970284a6b6a6d1aead805ed67162`。

独立 `com.android.application` 夹具经本地Maven坐标消费，无project dependency/模型资产或普通Demo参与，构建10.904s/35任务全执行。APK127887599B/SHA `9409ebd8c4d722bf33cc02a2a4ba41b16dd89a3167b0d90cddd1665fbc11959c`，46非目录成员，11通知资产按Python/Info-ZIP两读保留；16原生库与固定runtime四ABI逐字节同。实际选中SDK及runtime摘要见 `apk-verification.json`。原库不能strip及SDK XML版本warning原件保留，不伪装成无警告构建。

`verify-delivery.py` 用 `expected-assets.json` 校验最终归档的唯一成员集、大小和SHA。父端在独立复制件删除通知、同长改单字节均被拒收，原APK保持。复验可运行：

```sh
rtk proxy python3 probes/sherpa-runtime-materials/jvm-notice-integration-20261115/verify-delivery.py /absolute/path/to/original.apk probes/sherpa-runtime-materials/jvm-notice-integration-20261115/expected-assets.json /absolute/path/to/new-negative-output
```

只证明已知JVM子集在新SDK及独立最终APK中的技术保留。native通知集成、完整递归组成、所需完整修改说明及P4独立发布接受仍缺；P1–P4/R4/R5/W3不因此关闭。未安装/运行设备，未开麦克风/AiAi或下载模型。原173保护路径和161冻结文件在文档前保持，后续共享文档仅新增本轮前缀。

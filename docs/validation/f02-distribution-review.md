# F02 固定 Skiko 候选分发审查（2026-11-03）

状态：**本地候选，未批准对外分发或切换生产依赖**。本记录只检查归档的内容与许可材料可追溯性，不裁定许可合规。

## 已复核的候选

- 版本 `0.144.6-ondevice-f02.1`，固定 Skiko `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0`、Skia `22f58c9fd43d55bde818821c04b48fda5d7ec939`，修复补丁 SHA-256 `c4ba9412811a6c68df3f3c9e14e53cb684c7eb849f3f16c0f683c10f6b55fc30`。
- ignored 本地归档 `artifacts/verification/f02-versioned-consumer-20261103/skiko-0.144.6-ondevice-f02.1-maven-repo.tar.gz`：109,379,413 B，SHA-256 `d042a71f6a931320f367387580341f6d05b404196099d51d969308a0a0c2e480`。重新运行 `verify_versioned_archive.py`，125 个常规文件与正常本地 BuildRepo 发布摘要逐项匹配，另有 4 个目录成员。
- 归档只含 root、AWT、iOS arm64、iOS simulator arm64 四个 Maven 版本目录。四份 POM 均声明 Apache-2.0。归档成员路径中没有 LICENSE/NOTICE；逐个检查包内 16 个可读取的 JAR/Klib 的条目路径，也没有 LICENSE/NOTICE。路径扫描不证明二进制中不存在版权信息。
- 固定源码的 Skiko `LICENSE` 为 Apache-2.0，`NOTICE` 提及 Android Open Source Project；Skia 根 `LICENSE` 为 BSD 风格，明确要求二进制分发随文档或其他材料重现版权声明、条件和免责声明。对应本地文件 SHA-256 分别为 `c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4`、`35dd077c24768b53625101cfcc0ad853ae7e2abc08bd2a111a0267760d1406d8`、`5f787c1dee3c56547f09ccc2906ab5f5293c4d8dd6c8654e573216c38e908dbd`。源码树还有多个第三方许可文件；尚未按实际链接内容确定应随分发物附带的完整清单。

## 分发前的未关闭事项

1. 从固定构建参数及两份 iOS Klib 内嵌的 `libskia.a` 确认实际打入的第三方代码，整理适用许可和 NOTICE；将 Skiko、Skia 及实际第三方材料做成与制品同版本、同哈希索引的分发附件，重新审查整个附件。当前归档不能单凭四份 POM 的 Apache-2.0 元数据视为许可材料齐全。
2. 明确项目专用版本的维护者、托管地址、CI/消费者读取方式、不可变版本规则与撤销流程。当前 109 MB 归档只在本机，未上传 GitHub/GitLab，也没有远程消费者验证。
3. 选定分发方案后，从空缓存的独立消费者按归档哈希取件，重验两份 iOS Klib、Gradle variant、最终 App 身份和真机资源预算。现有本地构建与十轮 trace 不自动证明远程分发和生产验收。

F02、原 P4/P11 及发布结论继续为 `open/not-ready`。相机权限和同 PID 十轮无 recorder trace 已完成，见 [AtlasTextOp 调查](p4-atlas-text-cache.md)；无需再次申请相机授权。

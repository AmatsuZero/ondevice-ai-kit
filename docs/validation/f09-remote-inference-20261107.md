# F09 远端制品：新消费者真机图片与视频推理

2026-11-07，从已真实远端取得并逐项核对的制品生成新的隔离消费者，实际 iPhone 图片与视频推理均通过。制品 manifest SHA256 为 `dbafbc31d118d3962fba69a5b27e9bc2c4acfdc68d72ed516f7ac663920dbf3d`；输入来自此前新 GET 的 25,288,674 B 包，而非重新构建的 SDK。

消费者采用已验收 `BinaryInferenceProbe`，仅将其 manifest 绑定替换为上述精确摘要；SDK、固定模型、公开图片/视频均未改变。新的 unsigned Release 消费者构建 11.037 秒通过，实际 App 的 24 份许可材料逐项摘要一致。新独立别名 `io.ondeviceai.remoteconsumer.inference.c20261107` 使用已有有效签名资产，没有创建或修改签名资产。

同一真机请求 PID4332 的持久终态为 `complete/passed`。14 个模型文件的大小/摘要与模型 manifest 一致；图片 3067.592 ms、10 次更新，视频 2281.231 ms、22 次更新，两项均有非空输出，且 `runs[].identityMatched=true` 表示模型/运行时身份一致。生成后 drain、close、关闭后拒绝、模型租约释放、无新增视频快照及输入保持均通过。单次耗时是描述性记录，不是性能预算或语义质量结论。

完成后停止精确 PID、卸载本次新增别名及其模型/素材容器，核对原有 44 个 App 安装 URL 保持，并返回普通 combined Demo。九个保护文件、生产锁、已有 profile 和 unsigned 原 App 均未改变。

证据：`artifacts/verification/f09-remote-inference-20261107/summary.json`、`report.json`、`runtime-verification.json`、`cleanup.json`；摘要绑定 930 份源码/产物/原始回执。旧[远端资源检查](f09-remote-consumer-20261107.md)和[字节绑定](f09-runtime-equivalence-20261107.md)保持各自历史范围，不能回写成已执行新推理。

关闭的子项是“这份真实远端制品的新消费者尚未在真机执行图片/视频推理”。构建仍在同一物理 Mac，未创建 Release/tag，没有新上传或模型下载。跨场景语义质量、资源/能耗预算、最低系统、来源条款适用及整分支最终独立审查仍开放；P4/P11 和 release 保持 `open/not-ready`，roadmap 仍 10/12。

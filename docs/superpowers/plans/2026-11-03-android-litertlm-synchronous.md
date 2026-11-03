# Android 多模态同步生成接入

承接 [原设计](../specs/2026-10-26-android-litertlm-backend-design.md) 与 [原计划](2026-10-26-android-litertlm-backend.md)。2026-11-03 用户要求优先完成功能、后置 corner case，并授权继续。沿用固定 0.18.1、CPU/image、ModelStore、可选依赖和受保护文件边界。

## 本轮实现决策

使用官方 `Conversation.sendMessage` 同步路径，先交付最终文本，不依赖异步 callback 或 `isAlive`。固定上游 `bda624851d74be34d4e1f7fac1fc4155939539c7` 的 `runtime/conversation/conversation.cc:507–550` 在正常返回前调用 `WaitUntilDone` 并等 terminal notification。失败可能提前返回，**异常不能证明 drain**，仍保留资源并 poison backend。源码与 AAR 的接口对应不代表已验证 native 二进制/设备行为。

取消先使请求失效、禁止交付输出，等待阻塞的同步调用返回，再关闭 conversation/engine、清理 cache 并释放 lease。首版不从另一线程调用 native cancel，因此取消延迟可能达到剩余生成时间；这项优化后置，UI 不得提前允许重入。

## 顺序与验证

1. 新增同步 session 的正常输入/预算、错误不释放、取消等待 sender 的 host 测试；先观察缺少实现的 RED，再实现、全模块测试和真实 AAR 编译。
2. 增加公开 `LiteRtLm.createBackend`；构造/availability 不加载模型。真正生成时校验 ModelInfo 的 vision/CPU/runtime 条件，创建独立 conversation，关闭顺序 conversation → engine。
3. 通过显式 build property 接入 Android Demo 的 image bridge；默认构建不引入 LiteRT-LM。仅从 app-private、带 manifest 的本地导入目录安装已授权资产，不自动下载、接受许可或伪造结果。缺资产明确 unavailable。
4. 验证默认/opt-in 编译、依赖图、相关测试与 lint。真实推理必须另有实际模型和设备输出；无资产时保留这一缺口，不以 host fake 通过替代。

功能目标仍是实际选图→模型生成→共享页面输出。长期资源、异步 streaming、快速原生取消及最低系统专项后置，不删除原要求。

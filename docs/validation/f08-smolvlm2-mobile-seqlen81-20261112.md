# F08 SmolVLM2 2.2B 真机 `image_seq_len=81` 对照

状态：**F08 仍为 open/not-ready**。这次隔离真机运行证明了一个真实的 Swift 配置读取阻塞：固定 checkpoint 的 `processor_config.json` 声明 `image_seq_len=81`，而原生工厂只从 `preprocessor_config.json` 构造处理器，缺失字段时默认成 64。原配置因此只产生 8×64=512 个图像 token；修正后的隔离运行产生 8×81=648 个图像 token 并完成全部生成。

## 真机结果

- 设备：iPhone 16 Pro Max（`iPhone17,2`，iOS 27.2.1），唯一 Metal 设备 `Device(gpu, 0)`。
- 模型：`smdesai/SmolVLM2-2.2B-Instruct-4bit`，revision `0a4ab666f99095e6c6bd0e1e49aca8822fc46df2`；权重 SHA-256 保持已核验值。
- 输入：同一冻结 12 条新视频、原问题/选项、8 帧、384×384×3；没有重新下载、改 prompt、改评分或改默认模型。
- `factoryImageSequenceLength=64`，`correctedImageSequenceLength=81`；12/12 行完成，0 格式无效，严格正确 **5/12**。
- 类别：counterfactual 2/2、physics 2/2、semantics 1/2、memory 0/2、abstraction 0/2、camera_motion 0/2。
- 同题宿主 Smol Metal 输出与真机 12/12 原始答案完全相同，均为 5/12；这只说明这批输入的剩余错误不是“真机没生成”，不证明 token/pixel 数值逐项相等。

## 资源观察

本次共 7,972 个采样（目标间隔10ms，实际调度有抖动），运行约 88.0 秒；最大 `phys_footprint` **3,206,056,856 B**（约2.99 GiB），最大 MLX active/peak **2,114,725,306 B**，resident **1,620,803,584 B**。起始 thermal state 为 nominal，结束为 fair；没有触发本轮诊断 guard（3.5 GiB、thermal state 2、180 秒）。这些是一次代表性观察，不是已批准的产品内存、持续热或发布预算。

加载记录为240.175ms；逐条完整处理 `totalMs` 中位数 **7.04秒**。`generateMs` 中位数71.9ms和`firstChunkMs`中位数44.5ms的计时起点位于 `TokenIterator` 构造之后，不能拿这两个值宣称用户请求端到端延迟仅几十毫秒，也没有测出“模型已加载即完整预热”的保证。没有验证模型释放后的内存回落。

## 证据与边界

主证据目录：`artifacts/verification/f08-smolvlm2-mobile-seqlen81-20261112/`。

- `device-run-1/terminal-second-read.json` 与 `assessment.json`：终态报告双读一致，PID 4078，12/12 完成。
- `device-run-1/parent-verification.json`、`parent-final-check.json`：严格 schema、输入/模型摘要、资源和评分通过。
- `same12-host-device-comparison.json`：同 12 个 case 的 host/device 原始答案 12/12 相同。
- `native-readonly-review.md`：独立只读审查，确认修正是单变量配置修正，并指出资源阈值与产品预算的边界。

构建 `build-confirm.log` 为新鲜 exit0/BUILD SUCCEEDED；6项 Python 报告检查与21项 Swift 合同检查通过。父端重新核验751项源码/产物、27项旧输入、306项既有72题输出摘要保持。旧运行PID4061在 preparation 阶段失败、0行完成，不能把0行当12题全错。前置准备对照证据另存 `f08-smolvlm2-prep-boundary-20261112/{device-report-second,parent-verification}.json`，其像素相等结论仅针对已测首题。

运行报告未逐行持久化精确648个图像token；它由编译进入当前产物的生成前断言验证，不能把 `promptTokens>648` 自身当独立的图像token计数证明。`execution-observations.json` 保留启动收据选项误放在 `--` 后、导致无宿主launch JSON的错误；正确模型启动flag实际生效，PID/终态由进程清单与双读报告独立确认，未重新启动。只读子审查曾额外离线运行报告verifier，超出其“不得执行测试”的委派约束；没有模型/构建/设备操作，其结论不作为额外设备运行或独立测试批准。

修正后的候选 App 已卸载，普通 `io.ondeviceai.combined.demo` 仍在设备上；45 个既有 App 的安装元数据逐项未变化。没有修改生产依赖、锁文件、默认模型或普通 App。F08 的当前结论是：**已解除“无法生成”的配置阻塞，但识别质量仍不足，不能宣称功能或模型已解决。**

下一有区分力的诊断是先检查这7个错误的视频过程在已保留的8帧中是否可见，区分采样丢信息与同输入模型决策失误；不先重复这12题、不任意扫prompt或帧数。held-out质量、产品内存/时延/持续热预算和生产适配仍未完成。

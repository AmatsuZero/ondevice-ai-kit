# F08 Qwen3 Swift 视频输入协议对齐

证据根：`artifacts/verification/f08-qwen3vl-swift-parity-20261114/`。用户已允许主代理直接实现；仅隔离候选，不修改原 runtime、项目依赖、锁或默认模型。F08 仍 **open/not-ready**。

已确认固定 Swift 原实现与 Qwen3 官方视频协议不同：原共享 helper 产生一个视觉块，官方按 temporal2 时间组产生多个带时间块，位置计算也需要将 T 展开为 T 个 T=1 网格。真实 checkpoint BPE 的全部32个自然视频输入均复现原实现不一致；这证明 Qwen3 Swift 输入协议有缺陷，不能倒推其他模型错误的唯一原因。

隔离修复保留物理时间戳、按 temporal2 平均时间展开、绑定实际 checkpoint token IDs，并将视频位置接到实际 `getRopeIndex`。范围限定 batch1、无 padding、纯视频、merge2/temporal2；混合输入等未覆盖分支显式拒绝。原图像/文本路径保留，尚无完整回归验收。

| 验证层 | 实际证据 | 边界 |
| --- | --- | --- |
| 原实现复现及新 helper | 原32题全不一致；RED 保留；最终235检查通过 | 含完整tokens、三轴位置、delta、anchor0/37、续写及负例 |
| 实际 Swift tokenizer/chat template | 32题、29编码检查通过，断网执行 | 真实 checkpoint BPE，不是查表替代 |
| 完整 Swift 源 | 28个 MLXVLM 源 iOS typecheck exit0 | 仅原 AVAsset 弃用 warning；源摘要不变 |
| 实际位置 runtime | 32题×两个anchor，MLX GPU 数组全部对应 | 不加载权重、不进行模型推理 |
| 完整 processor.prepare | 32题 tokens/THW/shape/位置/delta 全对应，0 protocol failures | Mac Metal，3.759s；手机单列，均非质量接受 |
| iPhone16 Pro Max完整准备链 | 32题0协议失败、终态双读相同、949文件重核 | Metal GPU，无权重/推理；全部32像素导出与Mac逐字节相同 |
| 独立像素核验 | 全部有限、大小和SHA通过，393冻结文件复核 | 与Python/Pillow像素仍不完全相同 |

像素残差折算8位RMSE为 **0.271743–1.145725 /255**。尚未确定它对模型输出的影响，不宣称逐像素等价或识别问题已解决。完整原始数组和失败证据保留。

最终候选 `Qwen3VL.swift` SHA256 `658c6a584a03c85401e796b56e3f25431224ca42d276dadf4f4024231e3bb92e`；helper SHA256 `9557569d119545d361755340914cca62bf8cf15e4a2820f6f4d9364da5b9a2cb`。生成器重现候选逐字节相同。原实现 SHA256 `05f6b40ef3037a9d0c66d87f7c1f95f8d32783fc6f7325064dcbb80dc9208ae9`，fixture SHA256 `7804be738320808292872525c11d2af7919890f6383c7e19b709b4900e700362`。

失败与恢复：POSIX locale 改变1.95的单小数舍入，移除后真实tokenizer通过；嵌套网络沙盒导致SwiftPM EPERM，构建与断网执行分离；修改编译中输入的一次构建被明确拒绝，不验收该轮。首processor执行6题后在CMTime纳秒传输guard失败，定位4.1经构造变4.099999999；改为四舍五入整数纳秒后256时间、32组时间文本及4非法值通过，再以新输出r2完成32题。没有重跑模型或覆盖失败。

独立 iPhone preparation-only App已完成同32题/256 PNG与配置，零权重；`parent-ios-processor-verification.json`核949冻结文件、tokens/三轴positions/THW/delta与完整像素。全部32手机像素和Mac精确相同，Python残差范围不变。候选`io.ondeviceai.f08qwen3prepare20261114`已卸载，45原App完整元数据相同并恢复UgreenHome；`ios-restoration.json`与`parent-preparation-final-audit.json`有证。

首iOS构建exit0但资源缺失被包验收拒绝，按既有XcodeGen folder-resource模式修正再构建，首产物未安装。首codesign报unknown exception，精确根因未定；保留原错，单独签嵌套兼容dylib并用既有身份SHA后深签验证通过，未改签名资产。首次launch命令把options放bundle后触发解析exit64，尚未启动；修正顺序后唯一实际运行成功，未重放模型。

下一独立模型臂固定原valid18全部18题，greedy32、balanced512、完整每步logits与28层offset/rope delta；沿用3.5GiB/serious/180s采样诊断guard，不是获批预算。权重从现有文件传入新候选容器、不下载；`ios-model-plan.json`冻结后才执行，不自动重跑。此为手机可行性诊断，CoreImage残差与Python/full-prefill差异仍需分开解释。手机模型质量、Android、最低设备、加载/内存/耗时预算及持续运行仍未接受；既有Qwen3严格9/18基线不因本报告改变。

后续同日[独立iPhone模型臂](f08-qwen3vl-iphone-valid18-20261114.md)已完成18/18、严格9/18且回答同Mac；不回写本准备链为质量接受。

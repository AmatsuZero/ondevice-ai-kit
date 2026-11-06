# F10 身份与 PAD 校准探索

2026-11-07，用户明确要求先委派 AI Agent 探索。cbc hy3 在89.495秒自然完成，
返回完整研究候选，实际0工具事件。主代理随后核验官方页面并更正过时入口、数据归属、
许可和统计建议。本轮完成研究与任务拆分，没有完成数据授权、校准或生产识别／PAD验收。

原提示、完整输出、父处置及4份官方检索快照保留于
`.omx/artifacts/cbc-f10-calibration-exploration-20261107/`。未下载数据集／模型，
未收集新人脸或攻击样本，未联系第三方、接受协议、上传数据或改变生产阈值。

## 已核验的来源与下一步

| 来源 | 本轮核验事实 | 项目用途／处理 |
| --- | --- | --- |
| [Face Research Lab London Set v5](https://figshare.com/articles/dataset/Face_Research_Lab_London_Set/5047666) | 作者页面列102位成人、CC BY 4.0及研究／展示用途的签署同意说明 | 值得优先核验的身份流程实验候选；尚未引入。需要将具体生物识别用途与同意范围对应；小规模受控人脸不能证明目标设备的生产低FAR、开放集或PAD能力 |
| [Replay-Attack 官方记录](https://zenodo.org/records/4593128) | 1300视频／50主体；有身份分离的train/devel/test及enroll协议；文件受限，EULA明确限定非商业研究 | 可参考现成协议；企业内部用途没有因此自动获准，不能直接下载后当生产校准集 |
| [CelebA-Spoof 作者页面](https://mmlab.ie.cuhk.edu.hk/projects/CelebA/CelebA_Spoof.html) | 非商业研究；商业用途及进一步分发受限制；单一组织站点内部复制的例外不取消用途限制 | 不以“内部使用”推导商用授权；目前不能据此闭合生产数据门槛 |
| [OULU-NPU 官方申请说明](https://sites.google.com/site/oulunpudatabase/download) | 需机构固定岗位人员签署并发送EULA，处理申请后给链接；不接受普通公共邮箱申请 | 已确认申请路径；尚未取得并审阅完整EULA，具体企业用途仍unknown；本轮未签署或发信 |
| [SiW-Mv2 官方页面](https://cvlab.cse.msu.edu/siwm-v2-dataset.html) | 官方搜索索引显示MSU研究许可／DRA申请；完整页面和协议抓取超时 | 暂不作为已核验的可用数据；cbc把SiW-M误写为USC／Multi-ethnic，已否决，不能照抄候选 |
| [IJB-C NIST申请页面](https://www.nist.gov/itl/tted/btg/ijb-c-dataset-request-form) | NIST已于2023-03-14停止分发IJB-A/B/C | 从近期获取队列排除，否决cbc“填写表单即可下载”的过时建议；不转找未经核验的镜像 |

[CC BY 4.0说明](https://creativecommons.org/licenses/by/4.0/)允许遵守署名等条件的商业使用，
同时明确隐私／肖像等其他权利可能仍需许可。因此London Set是有明确版权许可的候选，
不是本项目已取得完整用途授权的结论。LFW/CPLFW本轮未取得足以确认本项目用途的官方条款，
不采用cbc的访问流程或许可推断。没有找到可直接宣称“商用授权已齐、生产校准可立即完成”的数据来源。

## 不等数据也能先完成的工程任务

| 优先级 | 可独立交付的小任务 | 验收边界 |
| --- | --- | --- |
| 1 | 数据／协议manifest与只读校验器：dataset/version、官方来源、许可快照摘要、用途范围、授权引用、输入／标注摘要、split/protocol、模型和预处理revision | 未授权用途拒绝；本地评估与数据再分发分别判断，不把再分发许可作为从不分发数据时的额外通用前提 |
| 2 | split与score完整性检查：拒绝重复媒体、跨dev/test身份泄漏、错label/类别方向、NaN/Inf、缺失来源和模型版本混用 | gallery与genuine probe在同一分区应有对应身份，图片／会话须隔离；跨设备为明确评测协议，不能机械要求所有固定benchmark设备完全不重叠 |
| 3 | 纯离线指标工具：1:1 FAR/FRR/ROC/DET；1:N probe级TPIR/FPIR与gallery规模；PAD逐PAI APCER/BPCER、非响应和分母 | 可用合成score夹具测试公式与拒绝边界，不能把夹具结果当真实准确率；空分母保持未定义／拒绝，不能输出0% |
| 4 | 校准候选policy与报告绑定：模型／预处理／数据／split／阈值／目标工作点／独立测试报告摘要 | 只生成可复核候选；默认维持THRESHOLD_UNVERIFIED，不自动启用生产决策 |

这些是研究给出的下一批工程任务，**本轮尚未实现**。不需拍照、不需下载新模型，
也不依赖生产阈值。私有score记录只含必要分数／协议内伪名和类别，不能导出图片、embedding或真实姓名；
伪名及分数仍按可关联的敏感实验数据处理，不能称“哈希后已匿名”。

## 获得适用授权数据后的最小协议

1. 明确1:1、开放集1:N、被动RGB PAD分别对应的使用场景和目标工作点；不给尚未选定的产品目标臆造阈值。
2. 固定模型和处理流程。现有YuNet/SFace与PAD流程分列；PAD原始ONNX的RGB、归一化及0=real/1=spoof已核验，
   见[既有模型记录](pad-model-candidate.md)，不重复该研究或已完成的原始分数smoke。
   尚需选择并验证捕获／裁剪协议，不能默认复用SFace对齐，不能以整图smoke冒充可用PAD人脸预处理。
3. 在开发／校准分区确定阈值，冻结模型、预处理、split与policy后评估独立test；不利用test反馈调阈值。
   同一锁定协议允许可复现的重跑及有记录的软件故障修复，不能把“test只能运行一次”当作恢复禁令。
4. 1:1分列genuine与impostor试次／主体／会话；1:N以unknown probe搜索为FPIR分母，
   报告真实gallery规模和rank，不能把pair FAR简单乘gallery规模当实测FPIR。
   PAD按攻击材料／类型报告APCER及BPCER，并保留非响应率；不能仅用一个ACER均值遮盖弱攻击。
   [NIST PAD报告入口](https://www.nist.gov/publications/face-analysis-technology-evaluation-fate-part-10-performance-passive-software-based)
   可用作方法参考；其[评测通道当前关闭](https://pages.nist.gov/frvt/html/frvt_pad.html)，不是本周可直接提交的捷径。
5. 报告置信区间和样本相关性。例如仅在独立Bernoulli试次且0错误时，单侧95%上界为
   `1 - 0.05**(1/n)`；要使该上界不超过`1e-4`至少需29956试次。这是统计示例而非项目目标，
   同一人／会话组合出的相关pair不满足独立前提，不能靠组合数量宣称已满足。

推荐顺序：先做上述1–3项工程工具；将London Set作为身份流程候选继续核验用途，
PAD则保留官方申请／明确商业许可数据／已有合法业务数据三条待选路径。
这些路径未获适用授权前不获取数据、不接受条款、不新增采集。
PAD完整SDK/Demo接入、生产校准、准确率与阈值、目标场景独立验收仍开放；P4/P11和发布状态不变。

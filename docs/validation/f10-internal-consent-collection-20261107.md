# F10 公司内部组织知情同意采集执行清单

文档性质：**执行记录**，不是对真实数据的授权决定。当前没有真实参与者或授权数据；
用户本人是维护人，不代表公司法律主体名已知。本清单只把“公司内部组织知情同意采集”
这一 F10 授权数据取得渠道变成可执行的采集前材料和交付要求。它**不**把“选择渠道”
说成已经取得授权、已经取得数据或已经完成生产校准。

权威上下文：本文档依据 `docs/validation/f10-calibration-exploration-20261107.md`、
`docs/validation/f10-calibration-manifest.md`、`docs/validation/pad-model-candidate.md`
及 `docs/agent-pitfalls.md`（条目 1/2/F10）。不探测不存在的泛 README/AGENTS/notes，
缺信息即在下方标“待填”。

---

## 1. 当前决定与责任分工

**身份识别**与**活体 / 呈现攻击检测（PAD）是两类不同的用途，必须分开准备、分开确认。**

- **身份识别**：采集真人自愿参与的照片 / 视频，用于 1:1 验证、开放集 1:N 识别等。
  它的采集、处理、留存与同意范围自成一套。
- **活体 / PAD（呈现攻击检测）**：为检验防冒充能力，需要**经参与者明确同意**的
  “**攻击样本**”作为对照——这里“攻击样本”**仅指**经同意的照片、屏幕回放、视频回放等
  **替代真人**的呈现方式，用于与真人活体对照测试。**它绝不是网络攻击，也不是对采集设备
  发动的攻击。** 这类对照样本的制作与呈现，必须**在身份采集之外单独确认授权**，
  不能因为用户选择了“内部采集渠道”就视为已经同意这些对照测试。

渠道已选：**公司内部组织知情同意采集**（F10 授权数据取得渠道之一）。以下责任均可待填，
不要假设用户本人填所有责任；正式采集不是本任务自动执行的一部分。

| 角色 | 当前状态 |
| --- | --- |
| 公司主体（法律实体名） | 待填 |
| 数据控制 / 保管负责人 | 待填 |
| 授权审批者 | 待填 |
| 采集组织者 | 待填 |
| 评估负责人 | 待填 |

采集前必须填的最小业务输入（不能用默认阈值填空）：

- 实际使用场景分别明确，并各自独立确认同意范围：
  - **1:1 验证**、**开放集 1:N 识别**：身份用途，先完成流程准备即可。
  - **被动 RGB PAD**：含经同意的对照样本制作 / 呈现，须**单独**授权确认；是否适用、是否开展标“待定”。
- 三类场景在交付表中**分别列示 待定 / 是否适用**，不强制三者都已确认；身份采集可先于 PAD 完成流程准备。
- 目标工作点 / 容忍错误：每类适用场景的 FAR/FRR、FPIR/TPIR、APCER/BPCER 容忍值（待填，不臆造）。
- 目标人群、设备、光照、姿态及数据保留期限（待填）。
- SDK 能力闭合边界：明确本项目 YuNet/SFace 与 PAD 流程各自覆盖范围；缺口标“待填”。
- 阈值/工作点一律待工程校准得出，不能填默认或推断值。

---

## 2. 知情同意检查清单（非法律充分的正式合同）

本清单是采集前确认项，不是宣称法律充分的正式合同；真实合法性仍由授权审批者独立审查。

- 用途分别明确且分别授权：**身份识别用途**与**活体 / PAD 用途**各自列清，不混写；
  选了内部采集渠道**不等于**已同意 PAD 对照样本的制作 / 呈现。
- 数据处理范围分别列清：原始照片/视频，以及派生分数、embedding 各自的采集、处理、留存与销毁范围。
- 访问对象、存放位置、保存期限、撤回删除路径与联系渠道逐项填（待填）。
- 参与自愿、可拒绝，且不影响工作权益；明确避免上级压力的采集方式。
- 仅成年人自愿参与；明确不得夹带旁观者或第三方未授权人脸。
- 授权文件与真实身份映射、评估伪名分库存放；**图片/embedding 不入 Git、不发外部 AI 助手**。
- 撤回与删除：明确参与者可随时撤回，撤回后对应数据删除路径与负责人（待填）。
- PAD 对照样本的制作 / 呈现须另行书面确认，并逐 PAI、逐设备、逐会话记录；未单独确认不得进行。

---

## 3. 交付映射（按现有 manifest 真实字段）

校验器为真实文件 `tools/identity_calibration_manifest.py`（测试
`tools/tests/test_identity_calibration_manifest.py`），只读校验声明完整性，不独立裁定授权真实性。
其成功输出固定为 `validationStatus=pass-bounded`、`authorizationStatus=declared-scope-only`、
`calibrationStatus=unverified`。**文档抽象概念不写入真实 JSON 字段**；以下全部字段以该 validator 为准。

顶层对象严格字段（缺任一或多任一即被拒绝）：

| 字段 | 类型 / 取值 |
| --- | --- |
| `schemaVersion` | 整数，必须等于 `1` |
| `kind` | 字符串 `"identity-calibration-manifest"` |
| `dataClass` | `"synthetic"` 或 `"real"` |
| `task` | `"verification"` / `"identification"` / `"passive-pad"` |
| `dataset` | 对象，见下 |
| `purpose` | `"local-calibration"` 或 `"local-evaluation"` |
| `redistributeDataset` | 布尔 |
| `licenseEvidence` | 对象 `{ "path": <相对路径>, "sha256": <64 位小写 hex> }` |
| `authorization` | 对象，见下 |
| `binding` | 对象，见下 |
| `samples` | 非空数组，见下 |

`dataset` 严格字段（**无** `version` / `officialSource` / `licenseSnapshotDigest`）：

| 字段 | 类型 / 取值 |
| --- | --- |
| `id` | token 字符串 |
| `revision` | token 字符串 |
| `sourceURL` | 凭证无关（无 `user`/`password`）、非空 hostname、无空白/控制字符、端口可解析的 `https` 字符串（按 `tools/identity_calibration_manifest.py` 源码，仅用 `urlsplit` 校验上述项）。**不**排除 `localhost` / 内网 IP / 私有 host：校验器只读取声明、不请求该 URL、不验证可达性或法律授权，内部 HTTPS 来源不因私有 host 被禁止 |

`authorization` 严格字段：

| 字段 | 类型 / 取值 |
| --- | --- |
| `allowedPurposes` | 非空、无重复、元素属 `{"local-calibration","local-evaluation"}` 的列表，且须含 `purpose` |
| `redistributionAllowed` | 布尔 |
| `evidence` | 对象 `{ "path": <相对路径>, "sha256": <64 位小写 hex> }` |

`binding` 严格字段：

| 字段 | 类型 / 取值 |
| --- | --- |
| `backendId` / `modelId` / `modelRevision` / `preprocessRevision` / `scoreMetric` | token 字符串 |
| `modelSHA256` | 64 位小写 hex |
| `scoreDirection` | 身份任务：`"higher-is-match"` / `"lower-is-match"`；PAD 任务：`"higher-is-bona-fide"` / `"lower-is-bona-fide"` |

每个 `sample` 严格字段：

| 字段 | 类型 / 取值 |
| --- | --- |
| `id` / `subjectId` / `sessionId` / `mediaGroupId` | token 字符串 |
| `split` | `"calibration"` 或 `"test"`（**仅此两个，无 `train`**） |
| `role` | 身份任务：`"enrollment"` / `"probe"`；PAD 任务：`"presentation"` |
| `label` | 身份任务：固定 `"bona-fide"`；PAD 任务：`"bona-fide"` / `"attack"` |
| `pai` | `"bona-fide"` 必须为 `null`；`"attack"` 为 PAI token（如 `print`） |
| `file` | 对象 `{ "path": <相对路径>, "sha256": <64 位小写 hex> }` |

> 正文不填虚构值：`sourceURL`、`licenseEvidence`、`authorization.evidence`、各 `sample.file`、`modelSHA256` 等
> 未定即为**待填**，不能假造授权、URL 或 SHA。`sourceURL` 当前强制凭证无关 HTTPS、非空 hostname、
> 无空白/控制字符、端口可解析；须指向真实受控的内部数据版本 / 协议说明来源，私有 host（如内网 IP /
> `localhost`）不被排除。不得填写假公网 URL，也不得为凑 HTTPS 把个人媒体公开。

**分区与隔离（按 validator 实际校验规则，已直接查看源码）：**

- 校验器只接受 `calibration` / `test` 两个 split；**`train` 不受支持**，本清单不在该 manifest 中要求 train。
  若未来确需训练集，须走单独协议 / 独立数据管理，不混入现有 schema；本清单不为 train 扩展代码。
- 每个 split 都必须具备完整身份角色（`enrollment`+`probe`）或 PAD 双类（`bona-fide`+`attack`）。
- 按 `subjectId` 与 `mediaGroupId` 隔离两个 split：同一主体、同一源媒体组都不得跨 `calibration`/`test`。
- 身份任务中，enrollment 与 probe 可对应同一 `subjectId`（同主体可同时有注册与探针），
  但同一 `(subjectId, sessionId)` 不能既作 enrollment 又作 probe；同一 `mediaGroupId` 也不能跨角色。
- 校验器**不会**检查“跨主体同名 `sessionId`”或“PAD 所有会话”这类它未定义的项目；不要声称它会做这些检查。
- 实际拍摄 / 采集批次的跨 partition 泄漏风险（如一批素材被误分到两 split）仍需**组织协议人工控制**；
  区分“校验器已自动核验”与“需额外人工要求”两项。

---

## 4. PAD 采集与覆盖边界

- “攻击样本”**仅指**经参与者同意的照片、屏幕回放、视频回放等**替代真人**的呈现对照；
  **不是网络攻击，也不是对设备发动的攻击**。
- 真实呈现与 replay / print 等攻击的**制作 / 呈现须分别授权**，按实际 PAI、设备、会话记录；
  未单独确认不得进行。
- 无法制作某类攻击则明确写“覆盖缺口”，不假装已覆盖。
- 固定捕获 / crop / preprocess 协议仍待工程选择 / 验证；**不能默认复用 SFace 对齐，不能以整图 smoke 冒充可用 PAD 人脸预处理**（见 `pad-model-candidate.md` 的 `whole-frame-cubic128-rgb-nchw-v1` 仅完成 smoke 的说明）。
- 不鼓励使用未授权网上人脸、冒用他人身份、隐蔽采集。
- 真实与攻击标签必须可追溯（绑定 `pai` 令牌与证据 sha256）。
- RGB 被动 PAD 不能声称验证了 3D 主动挑战。

---

## 5. 分阶段执行

1. 先批准用途 / 同意 / 撤回路径 / 访问控制；**身份用途与 PAD 对照用途分别确认授权**。
2. 小 pilot 验证 capture / crop / 模板及去重 / 摘要流程。
3. 冻结协议 / 模型 / 预处理 / 主体 split 等**采集与处理协议**（此时尚未定生产阈值 / policy）。
4. 用 `calibration` 分区选候选 policy。
5. 冻结阈值 / policy 及相关摘要，再进入独立 `test` 分区评估；**不因 test 结果调阈值**。
6. 可复现的软件故障可按固定协议留记录重跑；不发明“test 只能运行一次”的禁令。

指标分别报告，不把 ACER 均值遮盖弱 PAI：

- 1:1：`FAR` / `FRR`
- 1:N：`TPIR` / `FPIR`（以 unknown probe 为 FPIR 分母，报告真实 gallery 规模与 rank）
- PAD：`APCER` 按**各 PAI 分列**（分母为该 PAI 的攻击响应数）；`BPCER` 以**全部 bona-fide 响应数**为分母（必要时可分场景报告，但分母始终是 bona-fide 响应，不按攻击 PAI 分母）。
- 置信区间与样本独立性分别说明

**分母与非响应的准确口径（按 `tools/identity_calibration_statistics.py` 源码）：**

- 现有软件输出的 `FAR`/`FRR`、`TPIR`/`FPIR`、`APCER`/`BPCER` 均为 **OnResponse 版本**：`errorRateOnResponse`、
  `tpirOnResponse`/`fnirOnResponse`、`fpirOnResponse` 一律以**对应的有效响应数（responses）**为分母，
  **不是**以全部 trials 为分母。不要把当前 OnResponse 公式误标成“全部 trials 版”标准指标。
- 非响应数量与非响应率由 `trials` / `responses` / `nonResponses` / `nonResponseRate` **分别统计、单独报告**：
  非响应既不丢弃，也不被强算作成功或失败；`nonResponseRate` 以 `trials` 为分母单独给出。
- 只有**相应分母为空的那个比率**才为 `null`（未定义）；其余可计算的比率照常给出。
- 完整生产协议对“非响应”究竟如何处理（计入/重试/剔除/如何计工作点）**仍待确定**，本清单不在此先行假定。

统计提示（引用自 `f10-calibration-exploration-20261107.md`）：仅在独立 Bernoulli 试次且 0 错误时，
单侧 95% 上界为 `1 - 0.05**(1/n)`；相关 pair（同一人 / 会话组合）不满足独立前提，不能用组合数量宣称达标。
此为统计示例，**非本项目目标**。

---

## 6. 简明交付检查表

所有状态初始为 **待填 / 未采集 / 未签署**；不提供虚构文件 SHA 或参与者数。

- [ ] 公司主体、各责任角色已填
- [ ] 身份场景（1:1 / 1:N）用途与工作点已填
- [ ] PAD 场景（被动 RGB）**是否适用 / 待定**已分别标明（不强制已确认）
- [ ] 身份用途知情同意书模板与撤回 / 删除路径已定（未签署）
- [ ] PAD 对照样本制作 / 呈现授权已**单独**确认（未采集 / 未确认）
- [ ] 图片 / embedding 不入 Git、不发外部 AI 助手 的存放约束已生效
- [ ] capture / crop / preprocess 协议已选并验证（待填）
- [ ] 按 `subjectId` / `mediaGroupId` 的 `calibration` / `test` 两分区与隔离规则已落 manifest（未采集；无 train）
- [ ] 小规模 pilot 完成采集 / 处理 / 标签 / 工具验证（未执行）
- [ ] 协议 / 模型 / 预处理 / 主体 split 冻结（未冻结）
- [ ] `calibration` 候选 policy 产出（未产出）
- [ ] 阈值 / policy 冻结后独立 `test` 评估与分报告（未执行）
- [ ] `THRESHOLD_UNVERIFIED` 保持，未启用生产决策

原始数据只在受控本地 / 内部存储。给主代理可提供：目录、manifest、授权范围与授权文档摘要
（不在对话贴个人信息）。现有 validator 只验证声明完整性，不独立裁定授权真实性。
正式采集不是本任务自动执行的一部分。

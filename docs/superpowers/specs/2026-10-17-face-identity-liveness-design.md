# 人脸身份与活体能力设计规格

日期：2026-10-17
状态：已完成架构设计，待用户审阅；尚未授权实现。
审查输入为本地未追踪草案 `docs/validation/face-identity.md`，SHA256 `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`；本规格不修改或发布该草案。

## 1. 范围与默认决策

本规格定义三个相互独立的能力：

- `FaceVerification`：单张 probe 与一个已授权模板的 1:1 比对。
- `FaceIdentification`：单张 probe 在固定 gallery snapshot 中进行 1:N 搜索。
- `FaceLiveness`：持续帧会话，判断采集到的呈现是否满足选定的活体协议。

本阶段只固定公共协议、生命周期、数据边界、错误和验收方法，不选择具体 embedding 模型、第三方活体 SDK 或云端服务，不把检测结果自动升级为身份结果。

已确认的工程默认：

1. 业务主体 ID 由宿主持有；SDK 对外只使用 opaque 引用，内部保存加密模板和必要元数据。opaque ID 不编码业务身份，但仍可关联个人，按敏感数据处理。
2. 撤销先成为逻辑线性化点，立即阻止新任务并取消在途任务；不允许在撤销后让旧会话自然产出身份结果。
3. 未校准或不兼容的阈值策略 fail-closed，返回稳定错误，不使用 SDK 内置猜测阈值。
4. 默认后端仍遵循 `Default` / `Explicit(backendId)` 选择契约；显式后端不可用时不回退其他后端或云端。
5. 三类能力各有自己的类型化请求、结果和会话；不存在通用 `FaceResult` 或“检测后自动识别”隐式路径。

## 2. 对象和引用

### 2.1 Opaque 引用

引用由各自的签发方建立；SDK 验证其来源和元数据，调用方不能任意伪造：

| 引用 | 含义 | SDK 是否解释业务身份 |
| --- | --- | --- |
| `TemplateRef` | 一份模板的 opaque ID、兼容键和授权 epoch 的索引 | 否 |
| `GalleryRef` | 一个已发布 gallery snapshot 的 opaque ID 与 revision | 否 |
| `CandidateRef` | IdentityStore 签发的 store-scoped opaque 主体分组引用 | 否，不能当业务主体 ID |
| `LivenessProof` | 一次性活体证据，绑定同一采集事务 | 只验证绑定和时效 |

`TemplateRef` 不接受调用方自行拼装的 `modelId`、`modelRevision`、创建时间或相似度分数。SDK 内部事实只由 `IdentityStore` 读取。业务主体 ID 与 `CandidateRef` 的映射留在宿主自己的数据库中，SDK 日志不记录该映射。`createCandidate` 为一个授权域创建随机 `CandidateRef`；`enroll` 必须指定它，模板永久归属于同一 store 中的一个 candidate，不得换绑其他 candidate。一个 candidate 可有多模板，一个模板可进入同 purpose/兼容空间的多个 gallery。`deleteCandidate` 撤销并删除该 store 下该 candidate 的全部模板和所有 gallery membership；宿主自身主体数据库中的别名不由 SDK 自动发现。

### 2.2 授权声明

SDK 不声称取得法律意义上的同意；宿主在采集前取得同意并传入 `AuthorizationGrant`：

```text
AuthorizationGrant {
  grantId: opaque
  revocationDomainId: providerNamespace + tenant + purpose + opaque subjectScope
  scope: set of enrollment | verification | identification | liveness
  purpose: caller-defined stable purpose ID
  issuedAt: wall-clock timestamp
  expiresAt: optional wall-clock timestamp
  authorizationEpoch: monotonically increasing integer
  evidenceRef: opaque audit reference, never raw consent text
}
```

`subjectScope` 由宿主授权提供者生成随机 opaque token，不包含业务主体 ID。撤销 epoch 以完整 `RevocationDomainId` 为命名空间；IdentityStore 保存每域最高 epoch、grant→domain 和 candidate/template/gallery membership→grant/domain 反向索引。`revokeGrant` 解析该 grant 所在域并推进该域 epoch，使同域旧 grantId 全部失效；新 grantId 不能绕过旧域撤销。重新授权必须由提供者在已持久化新 epoch 上建立新授权和新对象，不能复活 tombstone。gallery 保存操作授权域和所有成员域的 epoch map，而非一个全局 epoch。

授权信任边界是宿主注入的授权提供者；SDK 不防御能够修改其进程或授权提供者的恶意宿主。提供者验证声明，IdentityStore 持久化已接受的 epoch，不信任请求自行提高或回退 epoch。`createCandidate`、`enroll`、gallery builder 的创建/变更/发布、`verify`、`identify`、`openLivenessSession` 在 admission 时验证 scope、purpose、有效期和 epoch；发布结果前再校验撤销状态。撤销递增 epoch；旧 grant、旧引用和旧 proof 不能重新启用。授权有效期由提供者按可信 wall clock 校验；运行内 deadline/顺序使用 monotonic clock。重启后先恢复 tombstone/epoch，不能用单调时钟推断跨重启有效期。删除由宿主独立的所有权授权控制，不要求已撤销或已过期的生物授权仍有效。

### 2.3 兼容空间

模板、gallery、索引和阈值策略必须绑定同一不可变 `EmbeddingSpaceId`：

```text
EmbeddingSpaceId {
  supportedCapabilities: set of face.verification | face.identification
  backendId: namespaced backend ID
  backendVersion: implementation version
  modelBinding: modelId + modelRevision, or systemModelBinding
  featureFormatVersion: schema version
  dimension: integer
  dtype: fixed numeric type
  endianness: fixed byte order
  normalization: fixed normalization rule
  preprocessRevision: immutable preprocessing revision
  scoreMetric: metric name and score direction
}
```

自有模型引用 `ModelRef` 和 `ModelLease`；系统或 bundled 模型使用稳定的 `systemModelBinding`，至少包含系统后端、运行时/SDK 版本、OS build 或 bundled runtime revision。相同 `backendId` 但不同模型绑定不能互相比较。兼容失败只返回 `embeddingSpaceMismatch`、`reEnrollmentRequired` 或 `rebuildRequired`，绝不跨空间计算相似度。

## 3. 公共能力协议

以下是跨端语义，Swift 和 Kotlin 的最终 ABI 在实现计划中通过编译探针固定：

```text
createCandidate(grant) -> CandidateRef
enroll(probe, candidateRef, grant, options, backend) -> EnrollmentResult
verify(probe, templateRef, grant, options, backend) -> VerificationResult
createGalleryBuilder(baseGalleryRef?, grant, policy, backend) -> GalleryBuilder
addTemplate(builder, templateRef, candidateRef, grant) -> void
removeTemplate(builder, templateRef, grant) -> void
publishGallery(builder, grant) -> GalleryRef
identify(probe, galleryRef, grant, options, backend) -> IdentificationResult
openLivenessSession(grant, options, backend) -> LivenessSession
revokeGrant(grantId, ownershipAuthorization) -> RevocationReceipt
deleteTemplate(templateRef, ownershipAuthorization) -> DeletionReceipt
deleteCandidate(candidateRef, ownershipAuthorization) -> DeletionReceipt
deleteGallery(galleryRef, ownershipAuthorization) -> DeletionReceipt
awaitDeletion(receipt, deadline) -> DeletionStatus
```

`EnrollmentResult` 区分 `enrolled`（含 TemplateRef/embedding space/quality）与 `insufficientQuality`（TemplateRef=null，无模板写入）。每个任务都有 `cancel()` 和 `close()`；每个会话最多一个活跃 run，取消幂等，关闭幂等。每个任务只有一个 Core 终态：`completed`、`failed` 或 `cancelled`。成功结果回显实际 `backendId`、`backendVersion`、`modelBinding`、策略 ID、session/run ID 和输入/策略 revision；错误在已解析这些字段时附带它们，未选择后端或校验输入前失败时相应字段为空；不返回业务主体 ID、原始图像或模板向量。

### 3.1 1:1 验证

`VerificationResult` 的 completed 领域 outcome 只有：

- `match`：相似度达到已验证 policy 阈值且质量门槛通过。
- `nonMatch`：质量通过但分数低于阈值。
- `insufficientQuality`：输入合法，但质量门槛未通过，不能解释为 nonMatch。

`match/nonMatch` 必须有 score、scoreMetric、threshold；`insufficientQuality` 在比对前返回，score/scoreMetric/threshold 均为 null，不填 0。所有 completed 结果包含 thresholdPolicyId、后端独立的 quality summary、face count、template revision、耗时和 preprocessing revision。多脸、空输入、损坏图像和授权错误使用稳定错误，不以任意第一张脸代替单脸约束。

### 3.2 1:N 识别

默认 candidate unit 是 `CandidateRef` 对应的 subject；同一 subject 的多个模板先按不可变 `aggregationPolicyId` 聚合，再去重、排序和截取 `topK`。探索期默认策略为同一 subject 取最大合格分数，分数相同按 opaque `CandidateRef` 的稳定字节序决胜；策略升级必须生成新 gallery revision 和 threshold policy。

`IdentificationResult` 的 completed outcome 只有 `candidates`、`unknown` 或 `insufficientQuality`。候选包含 `CandidateRef`、聚合 score 和 rank；是否由宿主映射成业务主体由宿主决定。`topK` 在聚合和去重后应用，默认上限 32；`topK=0`、超过上限、空 gallery 和已删除 revision 返回稳定输入/资源错误。`unknown` 不返回低于阈值的候选；`candidates` 只包含达到阈值的项。结果固定 `galleryRevision`、`aggregationPolicyId`、`thresholdPolicyId` 和 gallery 内容摘要。gallery revision 是模板集合、兼容键、聚合策略和授权域 epoch map 的不可变摘要；它不是可被原地更新的别名。

### 3.3 阈值策略

1:1 与 1:N 使用不同类型的不可变 `ThresholdPolicy`，至少绑定：

```text
policyId, policyVersion, embeddingSpaceId, scoreMetric, scoreDirection,
qualityFloor, operatingPoint, datasetRevision, gallerySizeRange,
enrollmentRule, effectiveAuthorizationEpoch
```

1:1 报告比对级 FMR/FNMR，并另行报告包含检测、质量拒绝、重试和策略的端到端 FAR/FRR；两者不混称。NIST FRTE 1:1 将 FMR 定义为达到阈值的 impostor comparisons 比例、FNMR 定义为低于阈值的 mated comparisons 比例，因此报告必须说明比较集合和阈值。

1:N 报告 FPIR/FNIR、rank、gallery 人数、每个主体的 enrollment 数、probe/gallery 条件和阈值。NIST FRTE 1:N 的 FNIR/FPIR 绑定 gallery 规模和每人 enrollment 条件；本 SDK 不把小规模本地样本外推成 NIST 规模结论。`TPIR` 只有在规格明确 rank、阈值和分母后才能使用，不引用为 NIST 页面直接定义。

没有锁定开发/验证数据集、operating point 和模型兼容关系时，policy 状态为 `unverified`，调用只返回 `thresholdUnverified`，不得继续输出 match、nonMatch、candidate 或 unknown。

PAD 使用独立的 `PADPolicy`，绑定 policy revision、backend/model binding、passive/active、challenge policy、输入/采集约束、校准数据与攻击类型范围、operating point、calibrationStatus。连续分数后端的 score/threshold/metric 成组出现；类别型后端三者全部为 null，仍必须使用经过验证的分类决策 policy。质量拒绝时三者为 null。PAD policy 未校准或不匹配同样返回 capability-scoped `thresholdUnverified`；不因后端只返回类别而跳过校准。分别记录攻击错误率和真实用户拒绝率及分母/置信区间，当前没有标准正文及真实攻击验证，因此不发布 APCER/BPCER/ACER 数值或标准合规声明。

### 3.4 活体会话

活体不是单张静态图片 API。会话状态机为：

```text
created -> running -> evaluating                         (passive)
                   -> challengeIssued -> evaluating      (active)
  -> completed | failed | cancelled
completed/failed/cancelled -> closed
```

接口固定 `start`、`submitFrame`、`nextEvent`、`finish`、`cancel`、`close`。会话创建时固定 backend、model binding、独立 PAD threshold/challenge policy 和 authorization epoch；PAD policy 不使用身份 embedding space 的阈值。passive 后端不发主动挑战，但同样受时间窗和单脸连续性规则约束。帧输入使用 monotonic timestamp；平台端最多保留一个处理中帧和一个最新待处理帧，报告 accepted/dropped/stale 计数，关闭或终态后的迟到帧全部丢弃。

挑战 nonce 来自安全随机源，绑定 session、purpose、challenge ID、创建时间、过期时间和授权 epoch；挑战发出前的帧不能完成挑战，nonce 不复用。completed 领域 outcome 只有 `live`、`spoof`、`insufficientQuality`、`challengeFailed`；`unavailable`、`sessionTimeout`、`invalidChallenge`、`authorizationRevoked` 和 `closed` 属于 failed + 稳定错误，调用方主动取消才是 cancelled。

活体与 verify/identify 默认相互独立。若业务需要组合认证，调用方必须显式传入一次性 `LivenessProof`，绑定同一采集事务、capture digest、session/nonce、policy、model binding、authorization epoch 和 freshness；不能把两个成功 JSON 简单拼接，也不能使用 tracking ID 作为身份键。

## 4. 撤销、删除和存储

`IdentityStore` 和 `IndexStore` 独立于 `ModelStore`。ModelStore 只管理模型资产和模型 lease，不伪装成模板数据库。

删除采用两阶段流程：

1. 逻辑阶段是线性化点：持久化 tombstone、递增 authorization/index epoch、拒绝新 admission，并使相关 builder 失效，向所有相关活动会话请求底层停止；其 Core 终态由撤销门提交 failed/authorizationRevoked。
2. 物理阶段等待 `TemplateLease`、`IndexReadLease` 和 `ModelLease` 在 `finally` 中释放，随后清理模板、gallery revisions、派生索引、缓存和恢复日志。撤销和结果发布由同一串行门协调：先提交的完成结果不追溯收回；先提交的撤销抑制后续结果，任务以 failed/authorizationRevoked 终结，并请求底层停止，不能当作调用方 cancelled。

删除状态至少区分 `revoked`、`purgePending`、`purged` 和 `purgeFailed`。请求取消等待不恢复已提交撤销；重复删除幂等。Gallery 更新使用 immutable snapshot + copy-on-write。Builder 固定 base revision、target embedding space、aggregation/threshold policy、操作授权域及成员 epoch map，单 writer、single-use，状态 `open -> publishing -> published | discarded`，close/cancel 进入 discarded。publish 在同一撤销/发布串行门内重验操作 grant、每个 template/candidate 的 tombstone、域 epoch、purpose、兼容键和 policy；任一失效则整个 publish 失败，相关 builder 不能重用。并发变更同一 lineage 使用 base-revision compare-and-swap，冲突返回 `galleryRevisionConflict`；新索引构建失败保留旧 revision。`deleteGallery(ref)` 删除 ref 所在 store 中整个 gallery lineage（所有 snapshots 和 builders/memberships），不会删除可由其他 gallery/1:1 使用的模板；模板清理由 deleteTemplate/deleteCandidate 完成。旧 revision 只有在无读 lease 后才能物理删除。

模板采用加密 envelope，记录 key ID、加密格式版本和 rotation epoch；密钥由宿主注入的平台安全存储提供。gallery 因成员域撤销而失效时拒绝整个旧 snapshot 并取消其在途查询，不静默过滤成员继续返回旧 revision 结果；移除失效成员需显式发布新 snapshot。

原始图像只存在任务期间，不写通用日志、benchmark 或崩溃报告；诊断只记录稳定错误类别、opaque 引用和 hash/计数，不记录模板向量或业务主体映射。默认不提供可逆特征导出、跨设备复制或云端上传。备份排除模板/索引及恢复临时文件；物理删除确认表示 SDK 管理的活动文件与可解密密钥引用已按策略移除，不承诺闪存覆写或消除设备/宿主已有备份。加密算法和平台密钥实现需在存储实现设计中固定并通过故障恢复测试，不能把普通文件删除称为取证级擦除。

## 5. 错误和后端选择

公共错误至少包括：

```text
backendNotRegistered, backendUnavailable, unsupportedOption,
authorizationRequired, authorizationExpired, authorizationRevoked,
candidateNotFound, candidateRevoked, templateNotFound, templateRevoked,
galleryNotFound, galleryRevisionGone, galleryRevisionConflict, builderClosed,
embeddingSpaceMismatch, featureFormatUnsupported, reEnrollmentRequired,
rebuildRequired, indexUnavailable, indexBuilding, thresholdUnverified,
invalidInput, multipleFaces, invalidChallenge,
challengeExpired, sessionTimeout, busy, closed, cancelled
```

错误类别跨端稳定，平台原始 domain/code 只能作为附加诊断。`Default` 和 `Explicit` 在会话创建时固定；显式后端不可用不回退其他实现。身份模块和每个后端独立 product/artifact，云端后端若未来加入，必须另立凭据、地域、上传和远端删除设计，不进入本规格默认路径。

## 6. 验收切片

实现前先完成共享夹具和状态测试：

1. 两端验证 opaque 引用、grant scope/expiry/epoch、撤销后 admission 拒绝和幂等删除。
2. 两端验证 embedding space mismatch、system model binding、模板/索引 lease、copy-on-write publish 和失败回滚。
3. 1:1 验证单脸/多脸/质量拒绝、policy unverified fail-closed、FMR/FNMR 与端到端 FAR/FRR 字段分离。
4. 1:N 验证多模板 subject 聚合、去重/topK/tie-break、unknown、gallery snapshot 和 FPIR/FNIR 分组字段。
5. 活体验证 nonce 防重放、挑战前帧拒绝、accepted/dropped/stale、取消/超时/迟到帧和单终态。
6. 错误契约、选择契约和最小宿主依赖图分别在 Swift/Kotlin 测试与消费者构建中验证；不得用合成图片声称生物识别质量通过。
7. 真实 accuracy 需要获得合规授权的数据集、锁定模型/策略 revision 和设备矩阵；没有这些资产时明确标记 `unverified`，不进入通过率分母。

### 6.1 官方参考边界

- NIST FRTE 1:1：<https://pages.nist.gov/frvt/html/frvt11.html>，用于 FMR/FNMR 的比较级定义和阈值说明。
- NIST FRTE 1:N：<https://pages.nist.gov/frvt/html/frvt1N.html>，用于 FPIR/FNIR 与 gallery 规模、每人 enrollment 条件的报告边界。
- NIST FATE PAD：<https://pages.nist.gov/frvt/html/frvt_pad.html> 与 NISTIR 8491：<https://doi.org/10.6028/NIST.IR.8491>，用于攻击类别和被测范围参考；本规格不据此声称 ISO/IEC 30107-3 合规。
- NIST SP 800-63B Rev.4：<https://pages.nist.gov/800-63-4/sp800-63b.html>，仅作为政府网络数字身份场景参考，不是本 SDK 的普适合规要求。

## 7. 实施顺序与停止条件

实施计划必须按以下顺序拆包：

1. Core 共享 DTO、opaque 引用、grant/epoch、稳定错误和 shared fixtures。
2. 独立 IdentityStore/IndexStore 的加密、lease、tombstone、snapshot publish 和删除回收。
3. 先实现测试后端验证 1:1/1:N 的选择、模板兼容和阈值 fail-closed。
4. 再接入一个明确模型 binding 的本地后端；系统人脸检测不直接复用为身份模型。
5. 活体单独实现状态机和测试后端，再接入选定平台后端。
6. KMP bridge、Demo 和分发裁剪最后接入；每项后端独立验证依赖图和真机行为。

保留期、真实数据授权和误识率 operating point 由具体商用宿主显式配置；未配置有效策略时只能运行共享夹具/测试后端，不能交付默认生物判定。后端/model 选择、生产校准策略和真实数据验证分别是后续工作包，不以本设计批准代替其验收。

本规格完成后停止在设计阶段。只有用户审阅并确认本文件，才进入 `$writing-plans` 创建实现计划；在计划获批前不创建身份实现代码、模型资产或真实生物数据。

## 8. 设计审查记录

2026-10-17 独立架构审查先指出 candidate 删除域、builder 撤销竞态、授权 epoch 域和 PAD/nullable score 四处缺口；修订后针对这些问题的增量复核为 CLEAR，未发现剩余设计阻断。该结论仅覆盖本文架构契约，不代表真实后端、数据集、精度或商用合规已通过。原草稿与 Demo 签名文件 SHA256 复核未变化；无身份实现代码或生物数据加入仓库。

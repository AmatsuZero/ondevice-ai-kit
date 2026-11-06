# 助手执行踩坑记录

适用于本项目主代理、cbc、dsh、OpenCode、Copilot、agy、Ollama、qoder 和原生子代理。执行前读取与任务相关的条目；遇到相同错误先查本文，再决定是否继续。只允许输出候选、禁止工具的任务，由主代理把相关条目附到提示词，不能为读本文扩大工具权限。

记录日期：2026-11-06。版本、额度、设备连接状态都是当时观察，继续工作时须重新核对。以下本地证据路径均相对 foundation worktree；`artifacts/`、`.omx/artifacts/` 是本机保留证据，不保证随 Git 分发。

## 1. 所有助手：进程成功不等于任务成功

- **delete返回false不是释放成功**：SR输出租约忽略两次`File.delete()`，真实非空条目删除失败却向bridge报告成功，导致重试句柄被移除。原租约两个JVM探针RED后，共用精确路径`Files.deleteIfExists`并保留双错误/处理错误；不递归删其他条目。cbc候选只把第二错误放进消息且若干测试未清理目录，均由主代理修正。SR10+启用SR的Demo56实际通过、video18缓存通过；证据`artifacts/verification/sr-output-cleanup-20261107/`。正式测试不依赖临时反射探针的编译器类名。
- **工程材料不等于App材料**：F09 consumer工程含24份notice，真实成功构建的App却全部缺失。
  修复需实际执行构建复制动作并检查App字节，覆盖同名LICENSE/带引号路径；旧审查不覆盖新修改。
  现已通过双目标unsigned App的24摘要及414工具测试，证据`artifacts/verification/f09-app-notices-20261106/`。
- **Python包入口**：依赖`from tools...`的生成器用`python -m tools.generate_mlxvlm_binary_consumer`，
  直接执行文件可能在构建前报`ModuleNotFoundError`。记录启动器失败，不能当作SDK构建失败。
- **现象**：退出码 0，但没有正文、JSON 不完整，或只有模型目录/连通测试结果。
- **处理**：同时检查进程退出码、完整响应、结构化错误和任务验收结果。保留原始 stdout/stderr，解析失败就报告未取得可用结果；不能补齐截断 JSON 后当作模型成功输出。
- **等待**：用户明确要求助手任务不按耗时或静默自动终止。短轮询只用于观察同一个进程，不是任务超时；不要因此重复发起或切换付费模型。
- **额度**：429、认证失败或明确额度耗尽时停止该线路，保留原错，按用户选择换供应商；其他独立线路继续。禁止循环换模型规避供应商共享额度。
- **证据边界**：候选生成、主代理集成、单元测试、构建、真机验收、独立发布批准分开记录。任何一项都不能自动替代下一项。
- **零finding不推翻复现**：F02原7文件agy审查未报告已被真实Gradle RED证明的旧publisher输入缺陷；第四补丁在packet外。保留审查结果及父处置，不能把无新finding写成新补丁/整个发布无缺陷。证据 `.omx/artifacts/agy-f02-notice-review-20261106/parent-assessment.json`。

## 2. cbc：结果格式与长任务

- 2026-11-07视频审查建议把帧数过滤`<`改为`<=`；主代理实测原实现能保留`(3,null)→(3,8)`的总帧数补充，而建议会丢第二次更新。累计计数相同不等于完整事件重复，不能仅据测试名称推断严格去重协议。建议RED/原源码恢复/完整video18+Demo56实际通过及SR4缓存通过见`artifacts/verification/video-client-review-20261107/`。这是对建议的否决，不是产品缺陷修复或发布批准。
- 本轮DNG核查承认根协议覆盖伴随源码，却又因缺逐文件枚举而判两份`.cpp`缺许可；根协议并无该枚举条件，且前缀摘录不能证明全文无通知。HarfBuzz核查还把tag表误归Unicode16、混淆Windows语言ID与MS-LCID URL，并把只给助手的上下文缺项当作项目阻断项。按固定源码/原文逐项处置，保留正确发现，不整体接受或整体否决。证据：`.omx/artifacts/cbc-hy3-dng-license-scope-20261106/parent-assessment.json`、`cbc-harfbuzz-specific-inputs-20261106/parent-assessment.json`。
- 公开条款提取不能直接当许可结论：hy3曾把MS-LCID明确的“Regardless of any other terms”
  反读成网站条款可追加覆盖限制，泛化排除HarfBuzz已入源的生成表，并凭空增加专利门槛。
  三段授权引文已对原文逐字归一核实，但这些解释均否决；其“exact quotes”字段还混入解释性
  文字，不能直接发布。查`.omx/artifacts/cbc-public-permissions-20261106/parent-assessment.json`。
- 早期 `--output-format json` 返回 JSON **事件数组**，不能假设根对象就是结果。检查最后的结果事件及错误字段，提取真正的任务正文。
- `hy4free` 早期一次 41 分钟任务由监督者主动取消，不是模型返回失败；其退出 0、正文空也不是成功。后续 F09 审查在没有任务超时的情况下于 431.23 秒完成。
- 再一次 F09 修复调用于 2263.57 秒自然退出，CLI 退出 0，但原始 JSON 在字符串中截断，启动器解析失败。没有接受实现候选；主代理按已复现问题自行完成 RED/GREEN 修复。证据：`.omx/artifacts/cbc-f09-consumer-fix-20261106/`。
- 候选任务沿用禁工具/MCP的调用，保留 `--max-turns 1`；不要自行调用工具或递归委派。用户最新允许有限非免费额度 `glm-5.3-flash`、`space-bunny` 用于有理由的高优实现，默认仍优先免费。两个付费模型在本轮尚未实测；不要为确认目录就消耗额度。

## 3. dsh：配置层、模型参数与 429

- `dsh` 模型通过 invocation patch 选择，不要猜测 `dsh --model`。仅使用用户已配置供应商的 `deepseek-flash`。
- 网关拒绝默认 `medium`：能力声明须包含 `reasoningEfforts: {low: low, high: high, max: max}`，同时选择 `reasoningEffort: low`。只改临时覆盖层，不改保存的默认配置。
- headless profile 默认关闭保存的凭据存储；临时覆盖层只启用已有环境变量引用，不能复制密钥、打印组合配置或凭据。
- 连通测试 4.65 秒完成、返回 `DSH_UGREEN_DEEPSEEK_READY`，随后正式校验器审查返回 `429 RATE_LIMIT`。用户已选择改交 cbc hy3；没有授权的新恢复方案时不要重试此线路。
- `codex --model deepseek-flash` 是用户自行研究的另一条路；dsh 成功不能作为重试该路的授权。
- 证据：`.omx/artifacts/dsh-ugreen-check-20261106/`、`dsh-candidate-validator-review-20261106/`；完整 patch 配方见根目录本地 `AGENTS.md`。

## 4. OpenCode：401 与匿名免费额度是两个问题

- 用户要求复查后，同日新隔离任务再次返回`429 provider.free-limit: Free usage limit reached`，`retryAfterMs=34277000`（当时约9.52小时）。停止该线路，按用户已选的cbc hy3接手，同一公开DNG任务31.24秒完成；不用再次询问同一fallback。目录可列模型不代表推理额度恢复。证据：`.omx/artifacts/opencode-dng-license-scope-20261106/`。
- 保存的 key 返回 `401 api_key_expired`。单独使用临时 XDG 目录、仅在该子进程移除 `OPENCODE_API_KEY` 测试匿名免费层，未修改用户保存的认证。
- 匿名层返回 `429 provider.free-limit` / `Free usage limit reached`，当时建议约 16 小时后重试。不能因此轮换免费模型或创建账号；用户选择了 agy 接手。
- 安装版本的模型目录命令是 `opencode models --standalone`；不接受猜测的 positional provider 过滤参数。帮助命令也可能写日志，隔离 XDG 路径仍有用。
- 使用输出事件判断错误，不能只看 CLI 退出码；模型出现在目录中不证明现在有免费额度。

## 5. agy：打印超时不是后台任务终态

- 一次新任务在CLI内部60秒认证等待后返回 `authentication failed or timed out`，`conversation_id`空、`num_turns=0`；这不是模型容量或监督者任务超时。用户说明网络恢复并明确要求重试后，新进程静默认证成功，348.12秒取得完整SUCCESS/1turn和7文件审查结果。尚无完整输出时只报告“已提交/仍等待”，不能提前报可用审查。保留两次记录，不改凭据，不把失败的OAuth URL复制进提示词/文档。证据：`.omx/artifacts/agy-f09-packaging-supplement-20261106/`、`agy-f09-packaging-supplement-retry1-20261106/`。
- 使用 `--print-timeout 0`。首次 180 秒打印超时返回 `DEADLINE_EXCEEDED`，当时后台 turn 尚在运行，不能立即重发。后续保留日志确认 CLI store manager / language server 已退出，仍不构成该审查通过。
- `--conversation ID --print ...` 会提交新一轮，不能当作只读状态查询。
- `--disable-slash-commands` 与 `--mode plan` 合用会使模式无效；不要组合。`--mode plan --sandbox` 也不是 cbc 的禁工具机制，候选提示词仍要明确只用给定材料。
- JSON 输出顶层的 `status == SUCCESS`、`response` 字符串和其中候选 JSON 是不同层；先查状态，再解析正文。保留工具事件，不能仅凭提示词假定没有工具调用。
- 独立 README、chunker、run/collect 候选分别已返回；候选仍须主代理审查和验证。证据：`.omx/artifacts/agy-candidate-readme-20261106/`、`agy-apk-chunk-plan-20261106/`、`agy-candidate-run-collect-20261106/`。

## 6. Copilot：空工具列表与 JSONL

- 本轮安装 `copilot 1.0.106`，`--model auto` 实际选择 `gpt-6-luna`。这是该次计划/账户可用模型的结果，不是永久免费目录。
- `--available-tools=` 被 CLI 接受，但工具 schema 仍出现在元数据；不能声称该参数已彻底移除所有工具。候选任务加显式 shell/read/write/url/memory deny，关闭内置及保存的自定义 MCP，并审查实际工具请求/执行事件。本轮已审查的调用未发生工具调用。
- `--output-format json` 返回 **JSONL**。只提取非空 `assistant.message` 正文，检查 `session.error` 与最终 `result.exitCode`；不要把空消息、reasoning 或 opaque session payload 打到报告里。
- `--no-ask-user` 只禁用提问工具，不授予更多权限；`--sync-session=off` 只控制本次账户历史同步，不是禁止所有本地持久化。
- 使用个人免费额度和已有登录。`premiumRequests: 1` 是请求计量字段，不能据此声称付费或无限免费。不得自动开通计费、改账户或切付费模型。
- 证据：`.omx/artifacts/copilot-candidate-verifier-20261106/`、`copilot-device-protocol-review-20261106/`、`copilot-f09-fix-review-20261106/`。

## 7. Shell / Ruby：继承环境与执行路径可能不匹配

- 在父 Git 仓库内的源码副本执行 `git apply`，可能退出0却输出 `Skipped patch`。不能只看退出码：使用精确路径的 `patch --batch --forward --fuzz=0 -p1`，并核对目标源码修复后摘要/实际任务。F02首次真实buildSrc验证因此被否决，原记录保留；修正后真正执行validator并验证72材料。证据 `artifacts/verification/f02-notice-validation-20261106/actual-parent-rejection.json`、`actual-v2-result.json`。
- 所有 shell 命令加 `rtk`；原始/二进制证据用 `rtk proxy`，禁止把过滤后的输出当原始字节计算摘要。任务正文通过 Python 参数列表或文件传递，避免 shell 展开反引号、`$()` 或凭据。
- 完整 tools 测试一次出现 19 failures / 8 errors：执行的 Ruby 2.6 加载了继承的 Ruby 3.4 gem，报 `dynamic constant assignment` / syntax error。它是工具链环境冲突，不能据此修改生成器逻辑或删除失败测试。
- 修复仅限本次测试子进程：在 PATH 前放已安装的 `/Users/daubert/.rvm/rubies/ruby-3.4.11/bin`，先用 `ruby -rjson` 核对 Ruby/JSON 能加载，再跑相同完整测试。未改全局环境、gem 或锁文件；随后 411 项通过。
- 证据：`artifacts/verification/f09-consumer-fix-20261106/tools-tests.log`、`tools-tests-ruby-aligned-result.json`。首次失败保留，不覆盖。

## 8. ADB：EOF、offline、unauthorized 与暂存

- `adb push` 先打印 `1 file pushed` 再报 EOF，不证明设备保留了完整文件；重连后曾确认暂存目标不存在。每次以远端文件摘要为准。
- 连接会在 `device` / `offline` / `unauthorized` 间变化。先刷新状态；只有需要用户确认 USB 调试时再请用户操作。错误建议里的 `adb kill-server` 不是执行全局重启的授权，也不要反复要求杀 App。
- 本轮改用 8 MiB、`adb push -Z` 分块；只跳过已核对 SHA 的块，再核对重组整包。部分块存在但摘要不符时停止并诊断该精确文件，不能盲目覆盖或据传输输出写成功收据。
- 安装前独立核验**全部五份**候选/回滚/原 test/ASR test/SR test APK 的本地固定摘要、暂存收据及远端摘要；不能先装候选再发现回滚或 test 不完整。主包保持原始 `b90e...64d8d`，不能换成新重建的主包。
- 本轮安装前五份 APK 的整包摘要已核对，最终验收和恢复后五份APK及113块均已清理；暂存收据是历史证据，不证明文件当前仍在手机上。证据：`artifacts/verification/android-persistent-candidate-20261106/stage-*.json`、`stage-resume-06.json`、`restore-complete.json`。

## 9. Android 报告、普通 App 与恢复

- am/ADB 退出 0 不是推理通过；ASR/SR 原生 finish code 也不相同。回读同一 runId 的私有终态、核对精确字节摘要，再校验原生断言。断连后只观察原运行，不自动重启。
- `recorder-pass` / `recorder-fail` 是记录器自检，必须被 inference 校验器拒绝；completed 自检不能被当成真实推理通过。
- SR 还需独立 native detail；它的随机 runId 必须区别于之前备份的 detail，避免旧报告复用。保存二进制内容不能 `.strip()` 或重新序列化后当原始摘要。
- 不运行原 Identity instrumentation：它使用普通 App 已有的 journal namespace，可能改动原身份数据。
- 普通 App/test/runner、25 项模型/journal 摘要、权限/TTS 默认值、四份 SR 文件必须保留并恢复；不清数据、不卸载。先备好 run/collect/restore 协议，再安装；恢复必须允许失败或部分安装，不能要求推理成功才能恢复。
- 恢复日志/保留副本不能直接写最终路径：进程中断可能留下半份文件，下一次便无法续接。当前恢复脚本先完整写临时文件，再用同目录 exclusive hard-link 发布；已存在内容只接受完全相同字节。SR scratch 若因 USB 留下半份内容，先按恢复 intent 核对归属、保留精确副本和摘要，再精确清理并重写；不能把半份文件移到原路径。
- `find` 顺序不稳定，保留输出不能按遍历下标命名；改用远端路径摘要。预检只证明没有 SR 输出文件，未证明其目录不存在，因此清理保留 SR 目录，不能把原有空目录当作本次创建。失败推理也可能没有 outputs 目录，收集前须严格探测存在性。
- 以上由 Copilot 代码审查与主代理 RED/GREEN 验证确认；16 项恢复检查、1 项失败报告收集检查和8项原生命周期边界检查分开计数，不并入完整tools suite。证据：`artifacts/verification/android-persistent-candidate-20261106/protocol-offline-final.json`、`.omx/artifacts/copilot-candidate-restore-review-20261106/`。
- 单测夹具与校验器可能共享错误假设：首次两者都写成128×96，真实已批准输入 `8277f1da...65d8c` 却是64×48/2帧H.264/AAC，native按4倍输出256×192。先用保留输入/输出做 ffprobe、实际解码和PTS核对，再修正严格尺寸约定并加拒绝另一夹具尺寸的回归；不能放宽成任意尺寸或重跑推理制造新pass。原始拒绝收据和同次报告重验收据分别保留为 `run-sr-fixed-inference-20261106-collected.json` / `run-sr-fixed-inference-20261106-revalidated.json`。
- 最低系统版本物理设备缺失已明确延期；F10 无授权校准数据，保持 `THRESHOLD_UNVERIFIED`。不能用模拟器、自拍或臆造阈值填平门槛。

## 10. F09 consumer：manifest 与输出目录竞态

- 许可刷新与编译来源分开：旧20模块二进制仍绑定f3899bb；后续内部许可来自3b5150d。先证明原149 SDK输入与新150文件快照仅多LICENSE，再在新副本刷新notices，保留原build receipt和非notice字节，单列许可来源。不能把有效旧编译收据标作失效或改写成新构建。完整25,288,674 B运输包/24材料保留证据 `artifacts/verification/f09-git-delivery-20261106/`。
- 审查建议需核对schema上下文：v1保留旧固定Metal摘要，v2明确采用按平台构建输出/收据；不能给v2强加v1摘要而拒绝现有合法产物。共享validator已对实际Metal文件执行摘要校验，收据一致性不等于发布者认证；static framework内ar归档也不要求可执行位（实际0644）。cbc补充审查的3条意见按此核实后未采纳，10项static测试及字节篡改/收据不符拒绝检查通过。证据：`.omx/artifacts/cbc-f09-packaging-supplement-20261106/parent-checks.json`、`parent-assessment.json`；契约见 `docs/validation/apple-mlxvlm-distribution.md`。
- 多次读取可变 manifest，会导致 payload、验证、拷贝、Swift 常量、收据引用不同字节。只读一次，保留原始 bytes 和 hash；向只接受 Path 的校验器提供冻结临时文件，使用显式 artifact root。
- POSIX 普通 rename 可覆盖已存在的空目录，先 `exists()` 再 rename 也有竞态。第一版 mkdir 占位仍可能在最终 rename 前被替换；不能只在失败清理时核对 inode。
- 最终采用独占重命名：macOS `renamex_np(RENAME_EXCL=0x00000004)`；Linux `renameat2(RENAME_NOREPLACE=1)`。API 不可用/文件系统不支持时失败退出，绝不回退为普通覆盖 rename。macOS实际回归和本机Linux/aarch64/glibc2.41的13项检查均已通过；两项新增仓库回归也在Linux通过，完整consumer模块在macOS18/18。Linux使用已有Colima镜像、禁网/只读挂载/非root；不是仅模拟sys.platform。证据：`.omx/artifacts/ollama-linux-publish-checks-20261106/`。
- 相对 framework 内部链接、symlink 父目录并非自动错误；现有 artifact validator 已检查根目录逃逸和摘要。不要照搬助手审查中已否定的泛化问题，扩大修复范围。
- 原问题、第一版及最终 RED/GREEN 都保留在 `artifacts/verification/f09-consumer-race-20261106/` 和 `f09-consumer-fix-20261106/`；前者 `before-fix.json` 不得被修复后重跑覆盖。

## 11. 继续与交接：保护状态、只记录真实变化

- 先读当前交接、Git 状态、活动进程、设备和证据；不能把之前的意图、文件名或中断命令当完成证据。
- 九份受保护路径和 Apple lock 以 `artifacts/verification/f09-native-cache-inputs-20261106/retained-inputs.json` 为摘要基线；不要恢复、暂存或提交其中用户原有改动。
- 基础 roadmap 仍 10/12、P4/P11 open、release not-ready。辅助脚本、通过单测、连通结果或负向质量分数都不能自行关闭这些门槛。
- 新坑按“现象 → 已证实原因 → 有效处理 → 不可越过的边界 → 证据路径”追加。密钥、密码、完整凭据配置、用户媒体和线程堆栈不得写入本文或提示词。

## 12. Ollama：本地连通、完成标志与候选质量

- 2026-11-07 Sherpa两文件本机审查14.429秒返回。异常覆盖发现经原实现两项真实RED确认；但“取消时立即放行下一次同步JNI”违反drain前不得重入的合同，已否决。其“协程异常身份保留”假设同样错误。使用标准库use的修复由主代理实现并实测，不把预修复静态意见当最终独立批准。证据`.omx/artifacts/ollama-sherpa-session-20261107/`。
- F02两文件审查12.58秒返回：索引/材料未校验一条已用真实Gradle复现并修复；另一条建议删除source JAR中的notices，与明确交付契约冲突而未采纳。不能把助手的一组建议全部接受，或把本地技术补充当独立许可批准。证据 `.omx/artifacts/ollama-f02-publication-review-20261106/`。
- `ollama --version` 在受限沙箱提示无法连接，不代表服务没开。经正常获授权 host 路径，只读 localhost 目录成功，确认 `qwen3-coder:30b` 已安装、约18GB；不要因为沙箱警告另起服务或重新下载。
- 固定 `127.0.0.1:11434`，不继承可能指向其他主机的 `OLLAMA_HOST`；本轮通过禁代理的标准库 HTTP 请求访问本机，没有云端调用。
- `/api/generate` 外层 JSON 的 `done: true` 不等于有任务结果：卸载请求也会返回 true。要求非空 `response`，核对 `done_reason`，再解析内层 JSON；`length` 截断不能当完成。`format: json` 只约束格式，不保证逻辑正确。
- `num_ctx` / `num_predict` 是本次上下文与输出边界，不是墙钟超时。使用无自动任务超时的请求；协调本机 GPU，不重复启动静默任务、不强停其他模型。只为本次加载的模型可用 `keep_alive: 0` 释放；原已加载的 runner 不额外停止。
- 第一次真实测试候选约19.99秒返回，但把 `/absolute/path/lifecycle_run.py` 示例硬编码，误把 `'80'` 当80字符输入，并未测到真正的尾随换行。主代理修正路径/导入和用例后，8项离线检查通过；不能据“生成了 unittest”或退出0直接宣称覆盖充分。
- 随后的恢复协议审查14.50秒返回，但5条建议都重复或忽略提示词里已有的约束；主代理逐项否决，没有据此宣称独立批准。复杂设备恢复协议继续由主代理集成验证；该本地模型优先承担小型纯函数/测试候选。证据：`.omx/artifacts/ollama-restore-protocol-20261106/parent-assessment.json`。
- Linux边界候选22.79秒返回，但缺少目标导入，把目录发布写成普通文件移动，多个拒绝场景未预期异常，缺失API的mock也没有作用于实际调用。未原样执行；主代理修正并补齐后，在真实Linux完成13项检查，并把两个关键场景加入仓库、双平台复验。生成候选不等于通过测试；证据 `.omx/artifacts/ollama-linux-publish-checks-20261106/parent-assessment.json`。
- 证据：`.omx/artifacts/ollama-lifecycle-tests-20261106/` 的原始 request/response、候选、主代理修订脚本、`tests.log` 和 `parent-verification.json`。这只证明本地任务生成及离线边界测试，不是设备推理验收。

## 13. LiteRT-LM：检索摘要、版本新增与原生静止合同

- **现象/原因**：Context7说明把空`awaitClose {}`解释成等待native callback drain；固定0.19.1
  实际先关闭Flow，JNI在Java终态回调返回后仍清理引用/attachment。检索摘要的解释不是源码合同。
- **有效处理**：查固定commit的Kotlin与JNI顺序，区分channel完成、回调栈退出和原生队列静止。
  内部`WaitUntilDone`可能超时，析构的`.IgnoreError()`也不是应用可观察的成功结果。
- **版本比较**：hy4free仅收到新版源码，却把`terminal_reached`及若干配置/方法称为新增；
  主代理对旧版摘要和原文核对，发现已存在，否决这类推断。没有旧源码时只报告新版事实。
- **边界/证据**：直接JNI delete不能证明传递析构完全不等待。缺公开barrier不代表同步factory缺席，
  不据此升级依赖或重跑同步推理。见`docs/validation/android-litertlm-later-barrier-20261106.md`、
  `.omx/artifacts/cbc-litertlm-later-barrier-20261106/parent-assessment.json`和公开原文目录。

## 13. qoder：CLI入口、受限网络与逐字核验

- 2026-11-07新通用关闭测试矩阵任务返回exit1、`error_during_execution`、HTTP403并附`pricingUrl`、`error_code:112`，0输出token/无候选。停止该线路，等待用户选择fallback，不重试/改登录/换模型；不能据此断言具体余额或支付状态。此前成功记录仍有效。原提示无项目源码，失败及父处置见`.omx/artifacts/qoder-close-cases-20261107/`。
- 后续通用落盘候选70.24秒成功，但先`O_RDONLY`打开FIFO会在`fstat`拒绝前阻塞；父代理保留负向RED后加`O_NONBLOCK|O_NOFOLLOW`并拒绝非bytes输入，修正版16项离线检查通过。候选外围多了说明文字，只提取唯一完整JSON代码块，未修补截断内容。不把修正后测试归给原始候选，不把通用函数生成当上传。证据：`.omx/artifacts/qoder-verified-download-20261106/`。
- 使用`qodercli`（本轮1.1.84）；`qoder`是另一个App入口。受限shell首次模型目录返回`Failed to fetch model list: Was there a typo in the url or port?`，正常获授权网络目录成功，唯一配置模型`Qwen3.8-Flash`。不要据沙箱失败改认证/网络或换供应商。
- 首次公开头部提取27.05秒成功，父代理对原源码逐字核对623字节/SHA通过。保留调用、原始结果和父处置；costUSD0/credits0只是本次记账，不保证无限免费。JSON仅返回结果，不能据此声称全事件零工具；调用明确禁工具/MCP。
- 已安装help不含`--max-turns`，不要从通用文档添加猜测参数；完整已测配方在根本地`AGENTS.md`。不设任务耗时终止，不改保存配置、不加权限绕过、不自动重试429。证据：`.omx/artifacts/qoder-dng-notice-extraction-20261106/`。
- 原始头部提取帮助发现冻结Skiko`.4`的72材料漏收DNG实际编译头`dng_safe_arithmetic.h`的AOSP声明。必须核对具体编译输入和原始通知，不能只确认组件根LICENSE存在。先保留缺项RED，再用新版本补齐并检查JAR/consumer/App字节；不由材料遗漏直接推断法律违规。

## 14. Kotlin协程测试：异常副本与未执行任务的旧XML

- 2026-11-07关闭/ASR回归首次GREEN在跨协程`assertSame`失败；实际错误类型和内容相同，堆栈含协程恢复边界。官方文档说明恢复可复制异常并将原异常设为cause，suppressed保留在原异常。测试改核对类型/内容、必要时原cause及实际close/destroy效果，不为测试改生产传播逻辑或全局关闭恢复。原RED/GREEN及文档见`artifacts/verification/speech-cleanup-20261107/`。
- Gradle在speech测试失败后未执行ASR任务，但ASR旧XML仍存在。不能把XML存在当本轮执行证据；检查准确task行和状态，区分executed/cached/not-run，跨模块必要时用`--continue`。本轮旧XML已明确排除，后续实际ASR14与speech29、TTS3/Demo56全过；源码/收据绑定，不能把修正测试前的失败称为成功。

## 15. Sherpa离线构建：指定已冻结的本地Maven仓库

- 直接运行Sherpa模块`--offline`会报`No cached version of com.k2fsa.sherpa.onnx:sherpa-onnx:2.0.1 available`，测试尚未启动，不能当产品RED或重新下载。复用`artifacts/verification/android-committed-candidate-20261105/inputs/maven/sherpa`，以`-PsherpaMavenRepo=<绝对路径>`显式传入；先按同目录`inputs.json`的externalInputs校验AAR/POM摘要和长度。
- Demo真实启用此后端还需`-PenableSherpaAsr=true -PsherpaAsrAssetsDir=<已冻结inputs/assets/sherpa-whisper-tiny绝对路径>`，核对全部七份既有模型/通知文件；默认Demo测试不等于启用后端配置验证。本轮14后端+56启用Demo测试实际执行通过，speech29为up-to-date。不修改全局Maven、不联网下载、不把host通过当设备JNI验收。证据`artifacts/verification/sherpa-cleanup-20261107/`。

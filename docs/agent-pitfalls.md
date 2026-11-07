# 助手执行踩坑记录

适用于本项目主代理、cbc、dsh、OpenCode、Copilot、agy、Ollama、qoder 和原生子代理。执行前读取与任务相关的条目；遇到相同错误先查本文，再决定是否继续。只允许输出候选、禁止工具的任务，由主代理把相关条目附到提示词，不能为读本文扩大工具权限。

记录日期：2026-11-06。版本、额度、设备连接状态都是当时观察，继续工作时须重新核对。以下本地证据路径均相对 foundation worktree；`artifacts/`、`.omx/artifacts/` 是本机保留证据，不保证随 Git 分发。

## 1. 所有助手：进程成功不等于任务成功

- **活动测试也可能停在内核冻结**：Identity原run01停7行，PID31731/活动instrumentation仍在，
  主/测试线程wchan均 `do_freezer_trap`，ActivityManager isFrozen=false；不能据此判OpenCV死锁。
  确认普通staging空且保留私有hash后，仅前台化同PID一次，原请求自然完成35行/close/lease/清理。
  该受干预运行排除干净基线。恢复原包后，新的受控Schema2在计时前启动首页并做72个RESUMED检查点，
  同一新请求完整通过且原状态再次恢复。内核/OEM具体触发仍未证明，不改系统freezer设置、不杀进程重跑。
  主线程读状态、instrumentation线程断言，避免检查异常直接杀主looper而丢失败报告。
  RSS必须标识包含UI，检查点不等于连续可见；证据 `artifacts/verification/android-identity-foreground-resources-20261107/`。
- **传输prepare/stage分开**：新目录先执行本地prepare生成payload-manifest，再stage。
  父遗漏prepare时stage先创建了登记的空目录后报FileNotFound；无APK复制/安装/推理。
  保留错误、补prepare，再校验同目录/intent继续，不能另开推理或把准备失败记为SDK失败。

- **预编译宿主与测试包装分开**：当前Apple标准hosted非UI XCTest首次外部`TEST_HOST`先被
  build-for-testing的宿主目标选择、再被build的产物相对路径检查拒绝，均未运行设备。
  仅编译新test bundle、动态链接实际App根目录的debug dylib，由安装版`xcodebuild.xctestrun(5)`
  参数提供独立测试副本宿主；加入Xcode运行库后签名，实际两方法/86内部检查通过，原3个Mach-O
  section/UUID保持。不是UI自动化或普通Files验收。XCTest Swift overlay还需`Developer/usr/lib`的`-I`，
  单加`Developer/Library/Frameworks`不足。`-test-iterations 1`会在CLI层拒绝且可留下空xcresult目录，
  默认一次运行应省略该选项、每次使用新结果路径，不删除旧失败/开启自动重试。
  optional quality score写JSON前显式解包，避免`Double? as Any`；编译warning在实际推理前处理。
  cbc hy4free本候选678.693秒自然完成，0工具，父修正后验收；不是原样候选成功或发布批准。
  原日志仍有OpenCV/system Objective-C重名及PNG iCCP警告，不能把两测试通过写成无警告/所有影响已排除。
  证据`artifacts/verification/apple-current-runtime-20261107/`及对应cbc助手目录；候选已卸载、原44App保持。

- **关闭开发者签名不等于模拟器无任何签名标记**：当前Apple构建指定`CODE_SIGNING_ALLOWED=NO`，
  设备App确实unsigned，模拟器主程序却带`adhoc,linker-signed`、无TeamIdentifier/资源封印。
  父检查统一要求codesign退出1，实际在模拟器误拒；原检查和两平台输出保留后，按平台核验无profile/
  `_CodeSignature`及精确linker-only状态，读取同一App通过，无重建/开发者签名/安装。
  不要改包移除合法linker标记来迎合检查；证据`artifacts/verification/apple-current-candidate-20261107/`。

- **ADB255与持久终态分别核验**：LiteRT-LM资源测量主机命令255/空输出，不能据此宣布失败并重跑。
  同一runId后续自行写出6行completed/terminal，760666原始字节与设备SHA一致；没有提交第二次推理。
  后续not-found使首次restore在变更前停止，恢复连接后仅继续原test恢复，推理不重做。
  固定prompt不能只核对摘要形状；cbc按给定合同返回shape校验，父集成时新增wrong-valid-hex
  用例后固定精确值。父新增P11适配器的schema bool接受也经负向RED修正。44聚焦/完整tools440通过。
  证据`artifacts/verification/android-litertlm-resources-20261107/`。小样本/采样RSS仍不是质量或预算通过。

- **JSON整数不能仅用相等判断**：cbc的ASR资源汇总候选把`True==1`、`False==0`和整数等值float
  接受为schema/count/index等合法整数；11项负向子用例复现后，用`type(value) is int`逐字段修正。
  不能靠`isinstance(value,int)`排除bool。10项reducer/40项聚焦及完整tools426通过；
  原候选、RED/GREEN见`artifacts/verification/android-asr-resources-20261107/`和
  `.omx/artifacts/cbc-asr-resource-summary-20261107/`。该运行明确绑定普通afed APK，不能冒作新SDK清理代码。
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

- **自然退出也可能输出截断**：移除轮数限制后的Identity候选自然终止1715.484秒，exit0，
  stdout恰262144B；JSON在assistant reasoning字符串内截断，只有一个完整user事件，没有完整
  assistant/result/Kotlin候选。不是监督者超时，也无429证据；保留原文，不修补JSON、不再发第三次。
  主代理接手原Kotlin实现。证据`.omx/artifacts/cbc-identity-resource-probe-turns-fix-20261107/parent-assessment.json`。

- **max-turns不是耗时上限但仍会截断复杂任务**：Identity资源Kotlin候选自然运行2277.90秒后
  exit0/stdout0B，stderr明确`Max turns (1) exceeded`。无候选，不是父进程超时或额度结论。
  原任务已终态，保留原文后仅修调用配置：新独立`hy4free`任务移除`--max-turns 1`，
  工具/MCP继续禁用，不扩大权限或改全局设置；新结果须独立验收，不能提前计成功。
  根AGENTS通用cbc候选配方已去掉该限制；证据`cbc-identity-resource-probe-20261107/`
  与`cbc-identity-resource-probe-turns-fix-20261107/`（均在`.omx/artifacts/`）。

- **数据集候选必须核验当前官方入口**：cbc本次F10研究把已于2023-03-14停止分发的IJB写成可填表下载，
  把Replay-Attack的1300段写为1200、SiW-M归属／含义写错；父代理用官方页面更正。
  CelebA-Spoof／Replay的非商业限制不因“内部使用”自动消失，code license不等于数据授权。
  本地校准用途与数据再分发分别判断；伪名hash不是匿名证明，相关pair也不能当独立低FAR试次。
  89.495秒完整JSON／0工具只是研究候选成功，不是授权或校准通过；报告与处置
  `docs/validation/f10-calibration-exploration-20261107.md`及对应`.omx/artifacts/cbc-f10-calibration-exploration-20261107/`。

- 当前Apple四文件检查清单误要求静态SwiftPM/OpenCV产物出现在`App/Frameworks`，又建议启动unsigned
  真机App、错用提交号字段及简写切片范围。主代理按实际Mach-O/Swift输入/Info.plist/平台资源核验，
  多项更正、未采纳产品finding；检查清单不计审查批准。原文/处置
  `.omx/artifacts/cbc-apple-combined-checklist-20261107/`，实际两构建及82bridge通过。

- 2026-11-07设备协议审查把输入里正确的`('original-test', d.TEST, d.SHA_OLD_TEST)`错引成主包摘要，
  又把未提供的lifecycle_run模块推断为不存在。父代理核对原始prompt/实际调用者并否决四条意见，
  不为这些意见改正确实现或放宽split APK准入；之后真实恢复通过。审查末尾未执行的“已保持设备”文字
  同样不是证据。见`.omx/artifacts/cbc-current-device-protocol-review-20261107/parent-assessment.json`。
- 2026-11-07原始ZIP字节复用候选在415.712秒自然完成，不是超时失败。父代理四项负向检查复现：
  只按初始长度重算SHA会漏追加字节；固定FD未核对路径替换；失败清理无身份核对会删掉替换后的文件；
  第二输入在try外打开失败会泄漏第一FD。修正固定FD读ZIP、前后身份/size/metadata/SHA与finally关闭后，
  10离线检查通过，真实签名APK本地精确还原；这不是设备重建或发布批准。
  证据`.omx/artifacts/cbc-apk-byte-plan-20261107/`及`artifacts/verification/android-current-candidate-20261107/`。
- 2026-11-07视频审查建议把帧数过滤`<`改为`<=`；主代理实测原实现能保留`(3,null)→(3,8)`的总帧数补充，而建议会丢第二次更新。累计计数相同不等于完整事件重复，不能仅据测试名称推断严格去重协议。建议RED/原源码恢复/完整video18+Demo56实际通过及SR4缓存通过见`artifacts/verification/video-client-review-20261107/`。这是对建议的否决，不是产品缺陷修复或发布批准。
- 本轮DNG核查承认根协议覆盖伴随源码，却又因缺逐文件枚举而判两份`.cpp`缺许可；根协议并无该枚举条件，且前缀摘录不能证明全文无通知。HarfBuzz核查还把tag表误归Unicode16、混淆Windows语言ID与MS-LCID URL，并把只给助手的上下文缺项当作项目阻断项。按固定源码/原文逐项处置，保留正确发现，不整体接受或整体否决。证据：`.omx/artifacts/cbc-hy3-dng-license-scope-20261106/parent-assessment.json`、`cbc-harfbuzz-specific-inputs-20261106/parent-assessment.json`。
- 公开条款提取不能直接当许可结论：hy3曾把MS-LCID明确的“Regardless of any other terms”
  反读成网站条款可追加覆盖限制，泛化排除HarfBuzz已入源的生成表，并凭空增加专利门槛。
  三段授权引文已对原文逐字归一核实，但这些解释均否决；其“exact quotes”字段还混入解释性
  文字，不能直接发布。查`.omx/artifacts/cbc-public-permissions-20261106/parent-assessment.json`。
- 早期 `--output-format json` 返回 JSON **事件数组**，不能假设根对象就是结果。检查最后的结果事件及错误字段，提取真正的任务正文。
- `hy4free` 早期一次 41 分钟任务由监督者主动取消，不是模型返回失败；其退出 0、正文空也不是成功。后续 F09 审查在没有任务超时的情况下于 431.23 秒完成。
- 再一次 F09 修复调用于 2263.57 秒自然退出，CLI 退出 0，但原始 JSON 在字符串中截断，启动器解析失败。没有接受实现候选；主代理按已复现问题自行完成 RED/GREEN 修复。证据：`.omx/artifacts/cbc-f09-consumer-fix-20261106/`。
- 候选任务沿用禁工具/MCP的调用；复杂任务不设置 `--max-turns 1` 或耗时终止。不要自行调用工具或递归委派。用户最新允许有限非免费额度 `glm-5.3-flash`、`space-bunny` 用于有理由的高优实现，默认仍优先免费。两个付费模型在本轮尚未实测；不要为确认目录就消耗额度。

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

- **失败测试也要证明触发了目标行为**：Nexus GET候选的变更测试向只读验证fd `pwrite`，
  EBADF被泛化ValueError捕获，文件并未改变却显示测试通过。父新增实际字节变化断言后RED，
  改用独立写句柄修改同一inode，检查原读取字节/实际修改/拒绝结果后GREEN。
  不是核对器已证缺陷，不能把错误测试的绿灯当作fd签名覆盖。新增20+既有9聚焦29过，
  完整tools530过；证据 `artifacts/verification/f02-nexus-get-receipts-20261107/`。
- **离线收据不证明网络来源**：GET/status/auth字段与实际留存字节都正确，只能证明一致性。
  Nexus核对器固定 `anonymousExecutionVerified=false`、`remoteConsumerValidated=false`；
  实际传输匿名性、Gradle来源/variant/编译、许可适用性仍分别验收，不伪造真实GET收据。

- **路径层级/传输语义必须实际测**：本轮Nexus核对器候选把`org/jetbrains/skiko/module/version/file`
  的module/version索引提前一层，自己的成功测试真实失败；父修正后7项过。再用带`?`/`#`/
  `%2f`/空白的成员文件名和缺少nofollow支持做出6项真实RED，再以Maven文件名字符集和
  fail-closed修正，9项过，实际`.6`115成员核对通过。保留原候选，不能说模型原样完成；
  证据`.omx/artifacts/copilot-f02-archive-validator-20261107/`与
  `artifacts/verification/f02-nexus-plan-archive-20261107/`。旧冻结计划/归档保持。
- **待办不是完成声明**：首次Nexus审查把`remainingBeforeUpload`列表内“Exact hosted destination
  confirmed”误当作当前已确认。它是前置条件列表；父处置否决此finding，不改旧证据。
  同次调用内部WebSocket`ECONNRESET`后CLI自然重试并完成，不是父代理重复请求或429。

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

- **提前修协议，保留非终态**：当前TTS新协议在设备暂存前已将旧最终`ln`改为`set -C; cat > 新路径`，
  partial和final全SHA都验，设备重建exit0；三项实际shell检查覆盖已有目标不可覆盖/损坏partial拒绝/成功发布。
  同次真实TTS主机255后第一份报告只到normal-start，保留后只再次回读，最终十阶段完整通过；
  不因非终态或USB中断重发推理。恢复前独立回读同SHA，原App及私有状态恢复后精确清理。
  34离线协议检查、真实运行与恢复证据`artifacts/verification/android-current-tts-20261107/`。
- **帮助文本仍须实际验证**：TECNO的toybox dd帮助列出`conv=excl`但实际拒绝；改用`set -C; cat > 新路径`。
  `iflag=skip_bytes,count_bytes`已用16固定字符stdin/stdout实测。135段重建完整SHA通过后，最终`ln`仍被设备
  Permission denied拒绝；保留exit1/已完成partial，复核整包SHA后禁止覆盖复制采用，不重跑构建或改签名。
  首次只读not-found与短unauthorized会自行恢复，不立即要求用户重连；只读观察可等待同一设备后复读一次，
  安装/删除/重建/推理不自动重放。27协议检查及真正新候选ASR/SR/恢复证据
  `artifacts/verification/android-current-device-20261107/`；旧b90e协议的“主包不可替换”仅约束它自身冻结实验。
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

# W3 durable prepare claims — 2026-11-13

本轮仅验收隔离诊断的 host 准备组件。`terminal_device_prepare.py` 已按免费
hy3 源码及精确补丁集成；新增29个父方法通过。完整新鲜回归为201观察器
+15冻结守卫+88终态=304方法、49个Python AST检查全部通过。没有真实设备
安装、识别、麦克风、AiAi 或模型下载；W3/R4/R5保持open，goal保持active。

`prepare(root, ctx, *, serial, cleanup, nonce_factory)` 使用实际适配FILE的
transport/transfer/helper，先验证本地两APK/六模型及严格绑定、新输出路径，
再校验TECNO身份、user0、解锁/亮屏、无活动instrumentation及至少2GiB可用内存。
设备快照包含普通包路径、APK摘要和模型摘要；stage及各alias的持久claim先于
对应mkdir/安装。stage claim预列全部8MiB part/final文件名；marker精确33字节
使用最多34字节回读以拒绝后缀，目录UID/dev/ino通过后先持久created收据再push。
最终新alias、精确runner、六模型、same-UID proc及普通包/模型均重验。

新增测试覆盖部分mkdir/marker/push/concat/安装/模型copy、created持久化失败、
首异常/恢复中断、每次实际ADB前freeze守卫、旧APK传输收据、路径祖先软链、
nonce/计划/元数据类型、marker后缀及transport exactbytes、stat格式、普通及
新alias APK后期漂移。新模块已加入DRIVERS派生的REQUIRED/REVIEWED；同时从
合同及双复核coherent删除依赖的真实RED现已GREEN。原120冻结、原失败报告与
裁定、95其他dirty文件、81准备源、两APK摘要新鲜核对无漂移。

原11方法首验1FAIL/3ERROR来自父夹具错误解析printf的backslash-n；真实/bin/sh
字节证据订正夹具后原候选11GREEN。随后实际10方法18FAIL/1ERROR、两APK漂移
2FAIL、剩余三方法7FAIL、marker类型2FAIL均保留，外部窄修后最终29GREEN。
首边界补丁有一块old文本换行转义错误、count0未应用；原补丁和八块精确应用
候选保留，下一两块窄修完整解决。未改正确共享API迎合候选。

独立hy3首审revise把“保留原异常”理解成cleanup(exc)。原明确API是零参数
cleanup()；补原契约、真实异常身份/次生中断测试、TEST单独已安装及活动
instrumentation零mutation复现后，澄清复核accept/无finding并撤回major。
原意见保持。pm前缀行为和实际FILE身份/gate绑定是明确依赖；root必须等于
实际OUT，未来dispatcher还须显式注入module-level digest/parse_kb。

实际ownership-proven恢复、完整outer dispatcher/收取/政策接线、完整双复核、
新冻结及同run真机仍未实现或未验。当前cleanup只是注入callback，不能据此
声称已恢复设备。下一按`prepare-parent/restore-next-brief.md`写父测试并完成
独立于collection的精确恢复；不得重放旧consumed run。

证据位于`.omx/artifacts/w3-terminal-observation-20261112/prepare-parent/`和
`prepare-repository-acceptance/`，结构化汇总见同名JSON。源码分支不push。

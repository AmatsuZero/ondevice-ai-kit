# Shared Multimodal active AX validation

2026-10-23。接续增强Demo集成69fae57；设计54b7951、Task1 2339c5d、Task2代码2915725。spec/plan `2026-10-23-multimodal-demo-active-ui`；ledger、任务日志和最终作者审查在 `.superpowers/sdd/2026-10-23-multimodal-demo-active-ui/`，保留全部材料。

## 真实设备结果

授权iPhone16ProMax/iOS27.0.1，UDID `00008140-0008703436D3001C`。实际增强Demo bundle `io.ondeviceai.multimodalmlxdemo`，源提交 `29157258921c324f2c08692e136ccb6da608d622`；built validation-metadata.json在安装前精确核对。PID21348、runId `43C3DAB4-ACD8-4384-ACAC-198065BB7D58`。原始报告 `artifacts/verification/multimodal-demo-active-final-20261023/demo-active-integration.json`，SHA256 `08dfd7005148935a6545c2fdb82902956ea2639853f82b01f4539ecd46d542bd`，安装/启动/copy/严格summary同目录。

11 gates全部true：bootstrap、available、pageOpened、inputAction、pickerReply、activeCancel、reentry、activeLeave、noAutoRestart、returnReentry、closed。四个有序场景均通过真实共享AX动作：

1. cancel：预算256，AX生成→实际VM update sequence0，紧邻取消动作前仍RUNNING、sequence0；共享cancel动作被接受→CANCELLED、无output/failure。
2. reentry：预算32，同PID共享generate→真实SUCCEEDED、非空文本与精确backend/model/runtime。
3. leave：预算256，实际update sequence0后仍RUNNING，AX speech-tab调用真实离页回调→CANCELLED、无output/failure；返回multimodal-tab，300ms内phase/update/output不变，无自动重启。
4. returnReentry：预算32，再次共享generate→真实SUCCEEDED与精确身份；最后host.close完成。

两个成功输出backend `mlx.smolvlm2-500m`，model `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`，revision `fa57db46815177fbdfd65cc85a2b3416a8332268`，runtime `6e338e5df49424fc0db7c6d58184d622f92ed18a`。更新后取消是首个**观察到的**真实delivered update之后，并不承诺在第一个callback同步取消。报告实际observed/action sequence均0。本包不报告性能；256预算仅提供实际运行控件重组窗口。

## 验证与隔离

Task1实际缺helper XCTest RED→新增2/2、helper7/7、全Demo40/40；严格report缺module RED→4/4 mutation tests，tools118/118。Task2 generator源集RED→Speech10/10、实际App缺suite符号RED→unsigned增强构建GREEN；freshDemo40/40、全tools118/118、default simulator与enhanced unsigned/signed device build成功、新Swift strict0。最后签名构建日志有BUILD SUCCEEDED；旧进程句柄过期后读取原日志，未重复构建。严格实际报告CLI `python3 -m tools.multimodal_active_report <report> --source-commit 29157258921c324f2c08692e136ccb6da608d622` 通过。

新增suite仅validation build源集且第三runtimearg `--multimodal-demo-active-validation`选择，须同时 `--multimodal-mlx-local-model --multimodal-demo-validation`。复用真实Demo准备/ModelStore/host/VM/共享控件，无新协调器/公共KMP导出/生产模型后端改动。默认与Android仍明确unavailable；旧导出/header未改。

模型14文件bytes/SHA和fixture `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`复核，复用自身App Documents固定资产，ModelStore acquire前再验证。生成fixed lock SHA256 `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858` 在安装前精确匹配。仅既有缓存，禁止自动解析/更新，team4X996UV8LC既有签名配置，无下载/签名资产变更/录音/私人媒体。仅本地提交，无push/merge/发布。两个保护文件hash/state/index保持不变。

## 边界与下一包

真实共享控件回调由App-local公开UIKit AX激活，证明活动取消/离页和重入接线。文件仍由真实picker delegate注入唯一自身fixture，**物理触摸/命中测试、视觉像素、系统文件选择、实际OS后台均unverified**；不覆盖之前程序化drain/lease/关闭拒绝证据。下一独立包先检查本地mobile工具能否在授权自身App进行真实OS前后台或触摸；不能执行则记录具体外部门槛，继续其他独立工作。

成功文本仍把合成图描述为flag，不算自然图片质量。process RSS不冒称Metal/GPU；本包无资源/能耗测量。旧Video runner初始化超时、P6实录、Android ASR MISSING_ASSETS、最低系统/自然质量/资源/分发与独立审查缺口保留。每Task与最终仅作者自审，独立review pending、无APPROVE。roadmap仍5/12完成、7未收口，P9 active。

# LiteRT-LM Context客户端及解绑线程验收 — 2026-11-08

当前Context客户端、专属进程协议及清理线程切片已验收。公开`LiteRtLm.createBackend`保持既有同步实现；这不是生产异步backend、ModelStore接入或W5整体通过。

## 行为与修复

`connect`只claim/bind/linkDeath，不发HELLO；显式reserve后才取得远端身份。共享coordinator保护跨客户端所有权，只有确认未发起HELLO的本地释放或原始Binder的exact death可以完成ownership等待。STATUS、取消、RETIRE回复、disconnect、超时均不能当作native静止。

Copilot聚焦审查发现非suspending的`releaseBeforeReservation`会在调用线程inline执行unlink/unbind。新Instrument测试从Main触发并gate清理：原源码实际1test/1failure，断言`unlinkToDeath must run off main`。Copilot最小修复只将两条清理/完成语句放入既有`SupervisorJob + Dispatchers.IO`生命周期scope，API和所有权协议不变；`awaitOwnershipReleased`在清理完成后才返回。

## 实际验证

- 修复后的同一测试APK SHA256：`5557c5adc88b48afa915765d23f3d27b652c58c9933f29d9999ac1f3dff09d85`。
- 专用API36 ARM64模拟器：新Main-thread回归1/1、既有真实Context/Binder9/9、本地取消/link失败/重复callback边界3/3，合计13/13、0失败。
- 当前完整模块90/90，0failure/error/skip；离线构建与lint通过，lint0error/2个既有dependency版本warning。源码摘要与同包绑定核对。
- 没有native推理、实体手机操作或新增模型下载。真实原型推理的既有3cycle证据是另一切片，不能替代此生产Service的实际推理。

代码由CBC hy4free初稿及Copilot小修直接交付；线程修复的实际源码已原样集成。该次Copilot缺少请求的DELIVERY文件并有实际工具权限/路径错误，父仅接受经diff/摘要/RED→GREEN验证的源码部分，不将CLI exit0或作者自述当作完整无错交付。

## 尚需完成

生产factory/session与共享coordinator、ModelStore lease/backend整合；实际Service推理与流式输出合同；固定安装二进制的callbackMode证据；GPU/能耗与持续预算。当前callbackMode仍`UNPROVEN`，进程死亡证明没有升级成callback native drain，W5/P11/release仍开放。

## 本地证据

- `artifacts/verification/litertlm-production-client-20261108-0826/context-client-offmain-parent-acceptance.json`
- 同目录`context-client-release-thread-{red,green}.json`、`context-client-offmain-{nine,edges}-green.json`、`context-client-release-thread-green-build.json`。
- `.omx/artifacts/copilot-litertlm-client-offmain-release-fix-20261108-1055/parent-assessment.json`保留原候选、完整工具错误和缺交付说明。

以上`artifacts/`和`.omx/artifacts/`为本地保留证据，不保证随Git交付。

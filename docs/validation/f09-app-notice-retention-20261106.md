# F09 App许可保留与内部候选包上传

## 已验证结果

原生成器把许可材料复制到consumer工程，却只在App构建时复制平台资源bundle。
冻结SDK的全新consumer实际Release模拟器构建成功（13.26秒），App仍缺全部24份材料。
此为真实缺陷；工程目录中存在材料不能证明App保留了材料。

最小修复复用原post-build阶段，逐项复制manifest已校验的notice，保持相对目录并对路径作shell引用。
回归在旧实现上首先因`App missing notice: NOTICES.txt`失败；修复后执行两个平台的真实脚本，
根目录notice、不同目录同名LICENSE以及含空格、单引号、`$`的路径均逐字节通过。
consumer模块19项通过；完整tools suite414项通过（58.757秒），两者不叠加计数。

修复后的全新consumer在generic iOS Simulator和generic iOS分别完成未签名Release构建，
耗时12.63秒和13.46秒；两个App的24份notice路径与manifest摘要全部匹配。
复用原20个SDK二进制，没有重编译SDK、签名或运行设备。原transport ZIP和旧生成工程保持。

首次修复后构建启动器因直接执行Python文件而缺少`tools`模块，构建尚未开始；
失败记录保留，改为`python -m tools.generate_mlxvlm_binary_consumer`后取得上述真实结果。
本机Ollama `qwen3-coder:30b`在15.85秒返回有界核查候选，指出遗漏及同名文件回归需求；
主代理采用现有复制脚本实现并实测，未照搬其`.bundle`建议，亦不据此取得独立发布批准。

证据：`artifacts/verification/f09-app-notices-20261106/`；
助手原文与处置：`.omx/artifacts/ollama-f09-app-notices-20261106/`。

## 已上传的精确内部候选包

用户选定Git，超过50 MB走Nexus；对具体25.3 MB Apple上传请求回复`ok`后才执行一次PUT。
现有极狐项目`home/common/ondevice-ai-kit`（461）为private，包功能已启用；上传前再次验证，
候选版本不存在且本地摘要正确。Developer访问由现有登录提供，没有更改认证或项目权限。

包名：`odai-apple-mlxvlm-internal-candidate`。
版本：`20261106-sha256-639205a78911289a604bc542a850d6d1d4772dc6a8b11e4d1b98635d41a152ed`。

[内部候选包下载](https://clients-gitlab.ugreeniot.com/api/v4/projects/461/packages/generic/odai-apple-mlxvlm-internal-candidate/20261106-sha256-639205a78911289a604bc542a850d6d1d4772dc6a8b11e4d1b98635d41a152ed/ODAIKitAppleMLXVLM-internal-notice-candidate-20261106.zip)
需要该私有项目的既有读取权限。

PUT返回HTTP201；同一坐标认证下载成功，字节数**25,288,674**，SHA256为
`639205a78911289a604bc542a850d6d1d4772dc6a8b11e4d1b98635d41a152ed`，与原候选完全一致。
没有自动重试、覆盖旧坐标、创建Release或tag。GitLab默认行为不提供已证明的原子不覆盖保证；
本次使用全摘要版本并在PUT前查空，不宣称具备条件写入保障。
Skiko `.4`的108,785,484 B包仍按Nexus渠道，未在本轮上传。

原compiled source仍为`f3899bbf282a32b05a3f3aafcb54c3d0dbcf5f9c`，项目许可来源单列`3b5150d`。
consumer构建使用与远端完全同摘要的本地解压包；此次下载证明传输字节，不是新远端环境的编译或推理。
上传计划、用户授权、意图、单次PUT与下载收据保留于
`.omx/artifacts/f09-git-channel-preflight-20261106/`，不保留认证输出或凭据。

## 审查和剩余条件

原agy7文件/cbc4文件仍与已审源码匹配；Copilot审查过的生成器已被本次修复改变，
其结果只适用于修复前版本。不得继续称旧12文件全部匹配。
后续fresh native `code-reviewer`已只读审查本次生成器改动及聚焦回归，返回
**APPROVE / 0 findings**；相对路径、shell引用、同名文件、可搬移和schema-v1边界均已核对，
并检查本轮RED及两个真实App结果。四处来源/材料的旧审查不被该结果替换；本次也不是
完整分支、法律、远端环境或发布批准。精确两文件摘要与结果保留于
`.omx/artifacts/native-incremental-reviews-20261106/f09-app-notices.json`。
历史冻结证据与失败记录不改写；新证据单独记录当前身份和差异。

本轮关闭App材料保留、内部候选包认证传输两个工程子项。
后续用户明确由其本人维护Apple/Skiko两类包，见[维护归属](delivery-maintenance.md)；
接收组织名称仍未指定。
第三方来源条款、完整分支/发布复审、生产资源与质量、最低系统延期及F10校准仍按原门槛保持。
没有新增benchmark样本或质量帧对；基础10/12、P4/P11 open、release not-ready。

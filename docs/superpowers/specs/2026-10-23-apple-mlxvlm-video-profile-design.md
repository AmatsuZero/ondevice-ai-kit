# Internal video snapshot profile coverage

2026-10-23，基线0811806，单人自动本地执行。前包已验证内部adapter，不重做。目标是补原Task1尚缺的负向profile与原生iOSdecode/cancel证据，不提供视频模型生成或公共API。

采用真实合成H.264 fixtures和AVFoundation执行，避免模拟decoder或文本检查。HDR fixture的H.264 VUI标明BT.2020/PQ；offset fixture保留非零track/edit时间；低帧率fixture只有2个真实帧，不足当前8个互异采样点时应fail closed（不在本包扩大profile或补假帧）；reference fixture只改现有合成MP4的dref URL entry指向合成外部媒体引用，禁止访问私人媒体。每项检查拒绝和私有copy清理；fixture命令、结构和hash留存。若现有行为已满足，准确记录baselineGREEN而非捏造初次RED；用有界临时fault mutation验证HDR测试能够发现移除transfer校验，然后立即恢复，再GREEN，不把mutation称产品bug。

替代方案：仅扩源码断言不能证明实际拒绝；扩大短clip支持会引入新采样策略；均不采用。若reference测试揭示已有不安全接受，先查容器和native行为，另行记录最小修复设计，不能默默降低断言。

iOS验证先检查现有连接、scheme可用destination及现有team/签名配置。只运行本项目native XCTest的snapshot类（不是旧UI测试），固定缓存和3项离线flags，不加载模型/不读设备媒体。不使用allowProvisioningUpdates、不创建/下载/修改签名资产；若现有条件缺必要profile或平台不支持，保留真实失败并明确未验，不以host/build代替。

固定模型/runtime/lock和两个保护文件不变；全部artifacts/失败日志保留；仅本地提交，无子代理/外部写入。作者自审不等于独立APPROVE；仍5/12/P9active。reference测试的拒绝不单独证明任意恶意容器都无外部访问；低帧率拒绝不等于支持所有<=5s片段。

## Native host recovery ruling

实际package测试exit70，在测试体前拒绝：`Tool-hosted testing is unavailable on device destinations`。按Xcode诊断改用独立生成的本项目空App作为unit-test host；不是重跑同一路径。空App沿用现有`io.ondeviceai.mlxvlmconsumer`及已使用的team4X996UV8LC/wildcardprofile，不运行原consumer/model初始化。测试target使用仓库同一snapshot/profile XCTest源码和fixtures，增加只在生成目录的Bundle.module桥；通过@testable引用当前package，明确ENABLE_TESTABILITY。所有生成项目/argv/hash留在artifacts/ledger，不改SDK公开API或签名资产，不用allowProvisioningUpdates。若仍有真实签名/runner阻塞则保留失败，不继续盲重试。

同一host另跑现有adapter6项测试，使用ByteTokenizer且不加载权重；test target显式链接当前固定MLXVLM/MLXLMCommon/MLX产品（与package test target相同），MLX约束必须与原exact0.32.3一致，不能把lock中的version改成revision要求。完成后重新安装普通consumer，不启动推理，避免设备留在空testhost版本。

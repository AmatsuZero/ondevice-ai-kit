# F09：远端制品消费者编译与模拟器／真机资源运行

本次从已发布的精确私有 GitLab 坐标重新认证 GET，取得 **25,288,674 B**，
SHA256 为 `639205a78911289a604bc542a850d6d1d4772dc6a8b11e4d1b98635d41a152ed`。
新下载包的344个文件逐项核对通过；不是复用此前本地解压内容作本次构建输入。

使用现有生成器，在全新consumer及独立DerivedData中构建 unsigned Release：模拟器
13.60秒、设备16.37秒，均exit0，两个App各24份许可材料与manifest摘要完全匹配。
没有重新编译SDK、切换生产依赖、修改签名资产或再次上传。

新消费者在既有 iPhone18Pro / iOS27.2 **模拟器**运行，PID4921。
持久报告的 `artifactManifestSHA256` 为
`dbafbc31d118d3962fba69a5b27e9bc2c4acfdc68d72ed516f7ac663920dbf3d`，
`modelBundleLookup`、`metalLibraryLoad` 和 `status` 均为 `passed`。
安装前确认该bundle不存在；运行后精确停止并卸载本轮测试App，未操作物理设备原App。

证据在 `artifacts/verification/f09-remote-consumer-20261107/`，摘要绑定787个文件；
全部九个保护文件保持。该结果关闭“真实远端取件后全新消费者双平台编译”及
“该消费者模拟器资源运行”子项。构建仍在**同一物理Mac**上；没有另一台机器的接受证据，
初始模拟器checkpoint没有实机资源或图像/视频推理；物理资源已在下节补齐。实际模型推理、资源质量、来源适用和完整发布复审
仍按各自门槛推进，不能据此将F09或roadmap整体判为完成。

## 同日补充：远端消费者物理 iPhone 资源运行

新证据`artifacts/verification/f09-remote-physical-20261107/summary.json`绑定上述新GET/解压/双平台构建，
无需重新编译consumer或SDK。把原unsigned设备App复制为安装前不存在的独立别名
`io.ondeviceai.remoteconsumer.f09.c20261107`，复用既有有效wildcard profile/identity签名；
原App的全部Mach-O有效section/UUID、24材料与源文件树保持，签名资产摘要未改。
实际iPhone上模型Bundle.module查找和设备Metal库加载均passed，报告manifest摘要
仍为dbafbc31…；未执行模型推理。停止精确测试PID后移除本次新别名，
原消费者及全部预装App安装URL保持，普通combined Demo已返回。
这关闭“这份远端consumer尚未做物理设备资源运行”的缺口；完整推理/质量/资源与发布复审仍开放。

## 同日后续：新消费者实际推理已补齐

上面“未执行模型推理”是资源检查时点的事实。后续已用同一远端制品构建新的隔离消费者，在真机完成图片/视频推理、模型摘要、close/租约释放与精确清理，见[新消费者实际推理](f09-remote-inference-20261107.md)。不覆盖本页冻结证据，不重建SDK。来源适用、质量资源及完整发布复审仍开放。

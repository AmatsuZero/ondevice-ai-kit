# 内部闭源交付约定

2026-11-06，项目使用方明确选择：**仅内部闭源使用和分发**。
项目自身适用根目录 [LICENSE](../../LICENSE)，不选择 MIT 或 Apache-2.0 开源许可。
条款不推定著作权人或组织名称；接收组织须获得相应权利人授权，内部副本保留条款及 notices。

## 已决定的交付范围

- 原项目代码可在获授权组织内部使用、修改、构建并以源码或二进制形式分发。
- 不授予对外源码/二进制分发、公开仓库、公共包仓库发布的权利；此类变更需另行决定。
- 第三方依赖、兼容补丁、模型和媒体继续遵循各自许可。项目条款不改变其原有权利。
- 内部分发方式不等于生产验收。当前 P4/P11 与发布仍 `open/not-ready`，独立审查继续待办。

本次不上传制品、不配置 registry 或凭据，不选定未经指定的组织域名/接收人。
后续实际内部交付沿用明确指定的组织渠道；不从 GitHub URL 或本机路径推定它是已授权发布渠道。

## F09 打包行为

`tools.prepare_mlxvlm_binary_snapshot` 从精确提交读取根 `LICENSE`，以 `packages/LICENSE`
加入快照 inventory 和 SHA256；dirty 工作树副本不参与。已有 Apple 包源码及生产 lock 校验不变。
`tools.package_mlxvlm_binary` 将该文件原样复制到 `notices/ondevice-ai-kit/LICENSE`，
SDK attribution 指向该文件，最终 artifact manifest 的 notices 表记录其 SHA256。
现有第三方 license/notice、两份隔离兼容补丁及本地验证声明继续随包交付。

旧提交或冻结快照没有 LICENSE 时，继续记录 `Project license unspecified; local validation only`，
不借用当前工作树的条款，不覆盖历史制品。本轮仅验证新快照与 notice 组装，不声称原生框架重新构建。
后续新制品沿用正式命令，并将原始许可证、资源、C headers 和完整 provenance 一并保留：

```sh
rtk proxy python3 -m tools.prepare_mlxvlm_binary_snapshot ROOT FULL_COMMIT FRESH_SNAPSHOT
rtk proxy sh scripts/package-apple-mlxvlm.sh FRESH_SNAPSHOT EXISTING_FIXED_SOURCE_PACKAGES FRESH_OUTPUT
```

`FULL_COMMIT` 须包含 LICENSE；`EXISTING_FIXED_SOURCE_PACKAGES` 沿用已有固定兼容候选，不允许自动获取依赖。
新制品仍须单独完成 artifact/consumer/runtime 检查和独立审查，不能因附带内部条款直接晋级为 release-ready。

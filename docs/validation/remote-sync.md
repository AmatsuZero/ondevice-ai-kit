# 远端同步状态

2026-10-15：GitHub 私有项目已创建：<https://github.com/AmatsuZero/ondevice-ai-kit>。
极狐镜像：<https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit>。

原 Daubert 个人空间创建请求因项目配额返回 HTTP403。用户随后创建并授权改用 `home/common/ondevice-ai-kit`；API 已核实项目 ID 461、private、初始为空，namespace full_path 为 `home/common`。`jihu` 使用 API 返回的 HTTPS clone URL，复用本机现有凭据；HTTPS 读取空仓库成功。

同步命令：`scripts/sync-remotes.sh main` 或显式工作分支；标签用 `scripts/sync-remotes.sh VERSION --tag`。
脚本预检两端可 fast-forward，先 origin 后 jihu，最后重新读取引用验证相等。部分失败标记 pending，分叉和已发布标签变化拒绝。
本地 bare-repository 测试覆盖幂等/fast-forward、极狐分叉保持两端不变、主仓成功后镜像失败及重试。尚无自动 CI 镜像。

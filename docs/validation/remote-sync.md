# 远端同步状态

2026-10-15：GitHub 私有项目已创建：<https://github.com/AmatsuZero/ondevice-ai-kit>。
极狐目标始终为 <https://clients-gitlab.ugreeniot.com/Daubert/ondevice-ai-kit>，命名空间已核实 full_path=Daubert、kind=user、id=572。

极狐创建请求返回 HTTP403：`You've reached your limit of 1 projects created. Contact your GitLab administrator.`
镜像项目尚不存在、没有推送；需要管理员提高 Daubert 个人项目配额，之后重新查询项目状态再创建。不会换到其它命名空间。

同步命令：`scripts/sync-remotes.sh main` 或显式工作分支；标签用 `scripts/sync-remotes.sh VERSION --tag`。
脚本预检两端可 fast-forward，先 origin 后 jihu，最后重新读取引用验证相等。部分失败标记 pending，分叉和已发布标签变化拒绝。
本地 bare-repository 测试覆盖幂等/fast-forward、极狐分叉保持两端不变、主仓成功后镜像失败及重试。尚无自动 CI 镜像。

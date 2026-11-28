#!/usr/bin/env bash
# 探测 job 的清理：只清本 job 起的资源，不碰 runner 上其它 job（主工程）的模拟器/进程。
# 由 GitLab after_script 调用（成功/失败都会跑）。
set -uo pipefail
cd "$(dirname "$0")/../.."

echo "=== 清理 per-job DerivedData ==="
# 只删本 job 唯一路径；没有就用探测脚本所在 job 的 BUILD 目录兜底
if [ -n "${DERIVED_DATA_PATH:-}" ] && [ -d "$DERIVED_DATA_PATH" ]; then
  case "$DERIVED_DATA_PATH" in
    *"$CI_JOB_ID"*) rm -rf "$DERIVED_DATA_PATH" && echo "已删 $DERIVED_DATA_PATH" ;;
    *) echo "跳过：DERIVED_DATA_PATH 不含本 job id，拒绝删除" ;;
  esac
else
  echo "无 DERIVED_DATA_PATH 或目录不存在，跳过"
fi

echo "=== 清理本 job 新起的 Gradle/KN 进程（PID 差集）==="
if [ -f .ci-probe-state/pids-before.txt ]; then
  ps -axo pid=,command= | grep -E 'GradleDaemon|konan|kotlin-native' | grep -v grep | awk '{print $1}' | sort > /tmp/ondevice-pids-after.txt || true
  new_pids=$(comm -13 .ci-probe-state/pids-before.txt /tmp/ondevice-pids-after.txt || true)
  if [ -n "$new_pids" ]; then
    for pid in $new_pids; do
      # 双重确认：进程命令行仍含本 job 特征才杀（GradleDaemon 文件名不含 job id，靠 PID 差集限定范围）
      if ps -p "$pid" -o command= 2>/dev/null | grep -qE 'GradleDaemon|konan|kotlin-native'; then
        kill "$pid" 2>/dev/null && echo "已终止 PID $pid" || true
      fi
    done
  else
    echo "无本 job 新起的残留进程"
  fi
else
  echo "无 pids-before 记录，跳过进程清理"
fi

echo "=== 不清理 simctl 设备（KMP Native 测试不创建模拟器，避免误删 runner 上别人的设备）==="
echo "CLEANUP_DONE"

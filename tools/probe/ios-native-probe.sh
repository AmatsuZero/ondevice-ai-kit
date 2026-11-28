#!/usr/bin/env bash
# 探测 macOS runner 能否跑苹果端原生测试 + 验证清理机制。
# 复杂命令集中在脚本里，GitLab job 的 script 只调 bash 本文件（避免 0-job）。
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "=== 环境 ==="
java -version 2>&1 | head -3
xcodebuild -version 2>&1 | head -3
./gradlew --version 2>&1 | grep -E 'Gradle|JVM' | head -4

# 记录跑前进程（供清理脚本做 PID 差集，只杀本 job 新起的，不碰 runner 上别人的）
mkdir -p .ci-probe-state
ps -axo pid=,command= | grep -E 'GradleDaemon|konan|kotlin-native' | grep -v grep | awk '{print $1}' | sort > .ci-probe-state/pids-before.txt || true
echo "跑前相关进程数: $(wc -l < .ci-probe-state/pids-before.txt)"

echo "=== 1) Apple 工具 Python 测试（7 文件 / 41 tests，Mac 原生工具依赖）==="
PYTHONPATH=".:tools:tools/tests" python3 -m unittest \
  test_apple_mlx_consumer test_mlxvlm_binary_consumer test_mlxvlm_report_writer \
  test_mlxvlm_source_bundle test_mlxvlm_static_consumer test_mlxvlm_static_framework \
  test_mlxvlm_xcframework_layout

echo "=== 2) KMP iOS Native 测试（kmp-binding + demo/shared 的 commonTest）==="
./gradlew :kmp-binding:iosSimulatorArm64Test :demo:shared:iosSimulatorArm64Test \
  --no-daemon --console=plain

echo "=== 跑后进程（供清理核对）==="
ps -axo pid=,command= | grep -E 'GradleDaemon|konan|kotlin-native' | grep -v grep | awk '{print $1}' | sort > .ci-probe-state/pids-after.txt || true
echo "跑后相关进程数: $(wc -l < .ci-probe-state/pids-after.txt)"
echo "本 job 新起进程 PID: $(comm -13 .ci-probe-state/pids-before.txt .ci-probe-state/pids-after.txt | tr '\n' ' ')"
echo "PROBE_OK"

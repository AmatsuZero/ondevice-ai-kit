#!/usr/bin/env bash
# 探测 job：一次验证三件事——NAS 缓存是否生效、苹果端原生测试能否跑、清理机制是否有效。
# 复杂命令集中在脚本里，GitLab job 的 script 只调 bash 本文件（避免 0-job）。
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "=== 环境 ==="
java -version 2>&1 | head -3
xcodebuild -version 2>&1 | head -3
echo "GRADLE_USER_HOME=${GRADLE_USER_HOME:-<unset>}"
echo "CI_BUILDS_DIR=${CI_BUILDS_DIR:-<unset>}"

# --- 1) NAS 缓存验证：检查 cache 恢复的 Gradle distribution ---
# 子模块配方：GRADLE_USER_HOME=$CI_PROJECT_DIR/.ci-gradle，cache paths 含 .ci-gradle/wrapper/caches。
# 这里 GRADLE_USER_HOME 由 job variables 设成 .ci-gradle；cache restore 会把 distribution 放回这里。
cache_root="${GRADLE_USER_HOME:-$CI_PROJECT_DIR/.ci-gradle}"
find_cached_gradle() {
  local root="$1"
  [ -d "$root" ] || return 1
  find "$root/wrapper/dists" -path '*/gradle-9.4.1/bin/gradle' -type f 2>/dev/null | head -1
}
cached_gradle="$(find_cached_gradle "$cache_root" || true)"
if [ -n "$cached_gradle" ]; then
  echo "NAS_CACHE_STATE=HIT  ($cached_gradle)"
else
  echo "NAS_CACHE_STATE=MISS (no gradle-9.4.1 in $cache_root/wrapper/dists)"
  # 首次 probe：从 runner 上其它 job 已缓存的 Gradle 复制过来（一次填充，让本次能跑且后续 cache 命中）。
  # 依次找 $HOME、CI_BUILDS_DIR 下其它工程留下的 gradle-9.4.1 distribution。
  donor_bin=""
  for root in "$HOME/.gradle/wrapper/dists" "${CI_BUILDS_DIR:-}"; do
    [ -n "$root" ] && [ -d "$root" ] || continue
    donor_bin="$(find "$root" -type d -name 'gradle-9.4.1-bin' 2>/dev/null | head -1 || true)"
    [ -n "$donor_bin" ] && break
  done
  if [ -n "$donor_bin" ]; then
    mkdir -p "$cache_root/wrapper/dists"
    cp -R "$donor_bin" "$cache_root/wrapper/dists/" 2>/dev/null || true
    echo "已从 donor 复制 Gradle: $donor_bin -> $cache_root/wrapper/dists/"
  else
    echo "警告：runner 上未找到任何 gradle-9.4.1，wrapper 需联网下载（无代理会卡住）"
  fi
fi

# 用仓库自带 gradle-local.py（注入代理、支持 ODAI_GRADLE_BIN 指向已缓存的发行版）
if [ -n "$cached_gradle" ] && [ -x "$cached_gradle" ]; then
  export ODAI_GRADLE_BIN="$cached_gradle"
elif [ -f "$cache_root/wrapper/dists/gradle-9.4.1-bin"/*/gradle-9.4.1/bin/gradle ]; then
  export ODAI_GRADLE_BIN="$(echo "$cache_root"/wrapper/dists/gradle-9.4.1-bin/*/gradle-9.4.1/bin/gradle | head -1)"
fi
timeout 120 python3 scripts/gradle-local.py --version 2>&1 | grep -E 'Gradle|JVM' || echo "gradle --version 超时或失败（见上）"

# 记录跑前进程（供清理脚本做 PID 差集）
mkdir -p .ci-probe-state
ps -axo pid=,command= | grep -E 'GradleDaemon|konan|kotlin-native' | grep -v grep | awk '{print $1}' | sort > .ci-probe-state/pids-before.txt || true
echo "跑前相关进程数: $(wc -l < .ci-probe-state/pids-before.txt)"

echo "=== 2) Apple 工具 Python 测试（7 文件 / 41 tests）==="
PYTHONPATH=".:tools:tools/tests" python3 -m unittest \
  test_apple_mlx_consumer test_mlxvlm_binary_consumer test_mlxvlm_report_writer \
  test_mlxvlm_source_bundle test_mlxvlm_static_consumer test_mlxvlm_static_framework \
  test_mlxvlm_xcframework_layout

echo "=== 3) KMP iOS Native 测试（kmp-binding + demo/shared 的 commonTest）==="
timeout 900 python3 scripts/gradle-local.py :kmp-binding:iosSimulatorArm64Test :demo:shared:iosSimulatorArm64Test \
  --no-daemon --console=plain

echo "=== 跑后进程（供清理核对）==="
ps -axo pid=,command= | grep -E 'GradleDaemon|konan|kotlin-native' | grep -v grep | awk '{print $1}' | sort > .ci-probe-state/pids-after.txt || true
echo "跑后相关进程数: $(wc -l < .ci-probe-state/pids-after.txt)"
echo "本 job 新起进程 PID: $(comm -13 .ci-probe-state/pids-before.txt .ci-probe-state/pids-after.txt | tr '\n' ' ')"
echo "PROBE_OK"

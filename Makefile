# 薄编排层：统一测试入口，本地与两端 CI 共用同一条命令。
# - test-offline：Python 离线测试子集，CI 镜像只需 make + python3（build-performance-python 自带）。
# - test-kotlin ：Android JVM 单测（需 JDK 17+ / Android SDK 36，GitHub ubuntu-latest 预装）。
# 真机/听感/通知关联/复审/签名不在此编排，保持人工验收。

PYTHON ?= python3
GRADLE ?= ./gradlew
MODULES = $(shell tr '\n' ' ' < tools/ci-pure-tests.txt)
# Kotlin：清单每行是 ":模块名"，拼成 :android:模块:testDebugUnitTest 参数列表。
KOTLIN_TASKS = $(shell sed 's|^|:android|; s|$$|:testDebugUnitTest|' tools/ci-kotlin-modules.txt | tr '\n' ' ')
export PYTHONPATH := .:tools:tools/tests

.PHONY: help test-offline test-kotlin

help:
	@echo "目标："
	@echo "  test-offline  跑离线纯 Python 测试子集（tools/ci-pure-tests.txt）"
	@echo "  test-kotlin   跑 Android JVM 单测（tools/ci-kotlin-modules.txt，需 JDK+Android SDK 36）"

# 离线测试子集：51 个模块 / 574 tests。命令主体在此一处定义，CI 不再重复长串。
test-offline:
	$(PYTHON) -m unittest $(MODULES)

# Android JVM 单测：14 个已验证可纯 JVM 跑的模块（sherpa-asr 等需本地 native 依赖的已排除）。
test-kotlin:
	$(GRADLE) $(KOTLIN_TASKS) --no-daemon --console=plain

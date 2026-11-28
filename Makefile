# 薄编排层：统一离线测试入口，本地与两端 CI 共用同一条命令。
# CI 镜像只需 make + python3（已验证 build-performance-python 镜像自带）。
# 真机/听感/通知关联/复审/签名不在此编排，保持人工验收。

PYTHON ?= python3
MODULES = $(shell tr '\n' ' ' < tools/ci-pure-tests.txt)
export PYTHONPATH := .:tools:tools/tests

.PHONY: help test-offline

help:
	@echo "目标："
	@echo "  test-offline  跑离线纯 Python 测试子集（tools/ci-pure-tests.txt，CI 守门用）"

# 离线测试子集：51 个模块 / 574 tests。命令主体在此一处定义，CI 不再重复长串。
test-offline:
	$(PYTHON) -m unittest $(MODULES)

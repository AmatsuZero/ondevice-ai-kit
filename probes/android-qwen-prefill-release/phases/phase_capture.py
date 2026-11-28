"""W3 单宿主阶段采集助手。

本模块不执行任何 adb/编排逻辑：fetch_log、run、collect 回调自行负责传输、
身份与限额。所有文件写入均为 'xb'，绝不覆盖；不做清理、删除或重试。
"""

import hashlib
import json
from collections.abc import Mapping
from pathlib import Path

_BEGINNING = b"--------- beginning of main"


def _is_fresh(before: bytes) -> bool:
    """仅允许空行与精确的 '--------- beginning of main' 行。"""
    for line in before.split(b"\n"):
        if line.endswith(b"\r"):
            line = line[:-1]
        if line == b"" or line == _BEGINNING:
            continue
        return False
    return True


def _cleanup(fetch_log, collect, phase_path: Path) -> list[BaseException]:
    """最终日志转储与终端/窗口采集彼此独立地尝试，返回所有失败。"""
    errors: list[BaseException] = []

    try:
        after = fetch_log()
        if not isinstance(after, (bytes, bytearray)):
            raise TypeError("最终 fetch_log() 必须返回原始字节")
        # 原样保留字节，即使内容不合法；不校验、不清理。
        with phase_path.open("xb") as fh:
            fh.write(after)
    except BaseException as exc:
        errors.append(exc)

    try:
        collect()
    except BaseException as exc:
        errors.append(exc)

    return errors


def capture(root, fetch_log, run, collect):
    """在准备完毕的新模拟器上调用一次；仅在成功时返回原始 run 结果。"""
    root = Path(root)
    before_path = root / "phase-before.log"
    phase_path = root / "phase.log"

    # 运行前检查：不通过则绝不调用 run。
    if phase_path.exists() or before_path.exists():
        raise FileExistsError("phase-before.log/phase.log 已存在，宿主不新鲜")

    before = fetch_log()
    if not isinstance(before, (bytes, bytearray)):
        raise TypeError("运行前的 fetch_log() 必须返回原始字节")

    # 独占写入运行前证据，随后再判定是否新鲜。
    with before_path.open("xb") as fh:
        fh.write(before)

    if not _is_fresh(before):
        raise ValueError("运行前存在陈旧 W3Phase 日志")

    try:
        result = run()
    except BaseException as run_exc:
        # 绝不覆盖原始 run 异常；其余失败用 add_note 附加。
        for err in _cleanup(fetch_log, collect, phase_path):
            run_exc.add_note(f"阶段清理失败: {err!r}")
        raise

    errors = _cleanup(fetch_log, collect, phase_path)
    if errors:
        first = errors[0]
        for err in errors[1:]:
            first.add_note(f"另有阶段清理失败: {err!r}")
        raise first

    return result


def quality(manifest_bytes, expected_digest, fixture_id, texts):
    """逐句比对：先校验清单 SHA256，再精确选取唯一用例，只返回布尔结果。"""
    if not isinstance(manifest_bytes, (bytes, bytearray)):
        raise TypeError("manifest_bytes 必须是原始字节")

    if isinstance(expected_digest, (bytes, bytearray)):
        expected_digest = expected_digest.decode("ascii")
    if not isinstance(expected_digest, str):
        raise TypeError("expected_digest 必须是十六进制字符串")

    if hashlib.sha256(manifest_bytes).hexdigest() != expected_digest.lower():
        raise ValueError("清单 SHA256 校验失败")

    data = json.loads(manifest_bytes)
    if isinstance(data, dict):
        cases = data.get("cases")
    elif isinstance(data, list):
        cases = data
    else:
        raise ValueError("清单应为用例列表或含 'cases' 的映射")
    if not isinstance(cases, list):
        raise ValueError("清单 cases 必须为列表")

    matched = [c for c in cases if isinstance(c, Mapping) and c.get("id") == fixture_id]
    if len(matched) != 1:
        raise ValueError(f"应恰有一个 id 为 {fixture_id!r} 的用例，实际 {len(matched)} 个")

    reference = matched[0].get("reference")
    if not isinstance(reference, str) or reference == "":
        raise ValueError("reference 必须是非空字符串")

    if not isinstance(texts, Mapping) or set(texts) != {"auto", "forced"}:
        raise TypeError("texts 必须是含 'auto' 与 'forced' 的映射")
    auto_text = texts["auto"]
    forced_text = texts["forced"]
    if not isinstance(auto_text, str) or not isinstance(forced_text, str):
        raise TypeError("texts['auto'] 与 texts['forced'] 必须为字符串")

    auto = auto_text == reference
    forced = forced_text == reference
    # 不返回任何原文，仅返回逐字比对结果。
    return {"auto": auto, "forced": forced, "bothExact": auto and forced}

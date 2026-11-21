"""Bind actual288 metadata using the unchanged quality/coverage algorithm."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'
BASE = ROOT / 'probes/android-qwen-arena/verify_report.py'


def once(text, before, after):
    assert text.count(before) == 1, before
    return text.replace(before, after, 1)


baseline_source = (ROOT / 'probes/android-qwen-file/verify_report.py').read_text()
baseline_source = once(baseline_source, "'maxTotalLen': 512", "'maxTotalLen': 288")
source = BASE.read_text()
source = once(source, "OUT = ROOT / 'artifacts/verification/w3-qwen-arena-wrapper-20261111'",
              "OUT = ROOT / 'artifacts/verification/w3-qwen-kv288-emulator-20261111'")
source = once(source, "'cpu-arena-off-v1'", "'cpu-arena-off-kv288-v1'")
source = once(source, 'spec.loader.exec_module(baseline)',
              "spec.loader.exec_module(baseline)\nexec(compile(kv_baseline_source, '<kv288-baseline-evaluator>', 'exec'), baseline.__dict__)")
scope = {'__file__': str(BASE), '__name__': 'kv288_report_evaluator', 'kv_baseline_source': baseline_source}
exec(compile(source, str(BASE) + '[kv288-report-adapter]', 'exec'), scope)
policy_matches = scope['policy_matches']
evaluate = scope['evaluate']
main = scope['main']


if __name__ == '__main__':
    assert len(sys.argv) <= 2
    main(output_name=sys.argv[1] if len(sys.argv) == 2 else 'parent-arena-acceptance.json')

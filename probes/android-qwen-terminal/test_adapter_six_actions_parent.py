"""Acceptance for routing all terminal actions through the exact dispatcher."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'probes/android-qwen-terminal'))
import test_terminal_source_adapter_parent as source_parent


class SixActionRoutingParent(unittest.TestCase):
    setUp = source_parent.TerminalSourceAdapterParent.setUp
    adapt = source_parent.TerminalSourceAdapterParent.adapt
    namespace = source_parent.TerminalSourceAdapterParent.namespace
    fake_context = source_parent.TerminalSourceAdapterParent.fake_context

    def test_all_six_actions_reach_dispatcher_with_only_guarded_identity_reads(self):
        for action in ('preflight', 'prepare', 'run', 'collect', 'collect-policy', 'restore'):
            with self.subTest(action=action):
                root, ns, home = self.fake_context()
                guards = []
                routed = []
                sentinel = object()
                def guard():
                    calls = (root / 'calls.jsonl').read_text().splitlines() if (root / 'calls.jsonl').exists() else []
                    guards.append(len(calls))
                def route(actual_action, context):
                    routed.append((actual_action, context))
                    return sentinel
                ns.update(_terminal_before_transport=guard, _terminal_dispatch=route)
                with patch.object(Path, 'home', return_value=home):
                    result = ns['main'](action)
                self.assertIs(result, sentinel)
                self.assertEqual(len(routed), 1)
                self.assertEqual(routed[0][0], action)
                self.assertEqual(routed[0][1]['plan']['caseCount'], 1)
                self.assertEqual(guards, [0, 1, 2, 3])
                self.assertEqual(len((root / 'calls.jsonl').read_text().splitlines()), 4)


if __name__ == '__main__':
    unittest.main(verbosity=2)

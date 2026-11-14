"""Regression for observed ADB rejoining and TECNO pm-path absence semantics."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import subprocess
import unittest

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent / 'android-terminal-observation')]
from terminal_prepare_fixture import PrepareFixture, APP, TEST, RUN
import test_terminal_restore_parent as restore_parent


def load(name, env, filename):
    target = Path(os.environ.get(env, str(HERE / filename)))
    spec = importlib.util.spec_from_file_location(name, target)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def real_shell(f, *, absent_code=1, response=None):
    """Make the subprocess double rejoin argv as adb does before remote parsing."""
    script = f.adb.read_text()
    anchor = "tokens=shlex.split(a[1]) if len(a)==2 else a[1:]"
    assert script.count(anchor) == 1
    script = script.replace(anchor, "tokens=shlex.split(' '.join(a[1:]))")
    stat_anchor = "if tokens[:2]==['stat','-c']:\n"
    assert script.count(stat_anchor) == 1
    script = script.replace(stat_anchor, stat_anchor +
        " if len(tokens)!=4 or tokens[2]!='%u %d %i':\n"
        "  print('2000');sys.stderr.write(\"stat: '%d': No such file or directory\\nstat: '%i': No such file or directory\\n\");sys.exit(1)\n")
    pm_anchor = "if tokens[:2]==['pm','path']:\n"
    assert script.count(pm_anchor) == 1
    pm_rule = (
        " if tokens[2] in (APP,TEST):\n"
        "  mode=json.loads((p/'oem-response.json').read_text()) if (p/'oem-response.json').exists() else None\n"
        "  if mode is not None:\n"
        "   sys.stdout.buffer.write(bytes.fromhex(mode['stdout']));sys.stderr.buffer.write(bytes.fromhex(mode['stderr']));sys.exit(mode['code'])\n"
        "  if not mapped('/data/app/'+tokens[2]+'/base.apk').exists():sys.exit(" + str(absent_code) + ")\n"
    )
    script = script.replace(pm_anchor, pm_anchor + pm_rule)
    f.adb.write_text(script)
    if response is not None:
        (f.root / 'oem-response.json').write_text(json.dumps(response))


class TerminalOemShellParent(unittest.TestCase):
    fixture = restore_parent.TerminalRestoreParent.fixture
    assert_ordinary = restore_parent.TerminalRestoreParent.assert_ordinary
    assert_cleaned = restore_parent.TerminalRestoreParent.assert_cleaned

    def setUp(self):
        self.prepare = load('oem_prepare', 'ODAI_PREPARE_SOURCE', 'terminal_device_prepare.py')
        self.module = load('oem_restore', 'ODAI_RESTORE_SOURCE', 'terminal_device_restore.py')

    def restore(self, f):
        return self.module.restore(f.out, f.context, serial='serial-01')

    def test_prepare_survives_real_adb_shell_argument_rejoining(self):
        f = PrepareFixture(self)
        real_shell(f)
        result = None
        error = None
        try:
            result = f.call(self.prepare)
        except Exception as exc:
            error = repr(exc)
        self.assertIsNone(error, 'prepare must quote the actual remote stat format: ' + str(error))
        self.assertEqual(result['nativeCalls'], 0)
        self.assertTrue((f.out / 'ownership-stage-created.json').is_file())

    def test_restore_after_success_accepts_tecno_absence_and_quotes_both_stats(self):
        f = self.fixture()
        real_shell(f)
        self.assert_cleaned(f, self.restore(f))

    def test_partial_prepare_before_created_record_recovers_with_tecno_absence(self):
        f = self.fixture('after-owner')
        real_shell(f)
        self.assert_cleaned(f, self.restore(f))
        self.assertTrue((f.out / 'ownership-stage-recovered.json').is_file())

    def test_empty_exit_zero_remains_supported(self):
        f = self.fixture()
        real_shell(f, absent_code=0)
        self.assert_cleaned(f, self.restore(f))

    def test_package_query_parser_requires_exact_evidence(self):
        for code, raw, expected in [
            (0, b'', True), (1, b'', True),
            (0, b'package:/data/app/a/base.apk', False),
            (0, b'package:/data/app/a/base.apk\n', False),
            (0, b'package:/data/app/a/base.apk\npackage:/data/app/a/split_config.apk\n', False),
        ]:
            with self.subTest(code=code, raw=raw):
                def call(*args, **kwargs):
                    self.assertEqual(args, ('shell', 'pm', 'path', APP))
                    self.assertEqual(kwargs, {'accepted': (0, 1)})
                    return subprocess.CompletedProcess(args, code, raw, b'')
                self.assertIs(self.module._alias_absent({'call': call}, APP), expected)
        for raw in [b'package:/data/app/a/base.apk\r\n', b'package:/data/app/a/base\n',
                    b'package:/data/app/a/base.apk \n', b'package:/data/app/a/base.apk\xc2\xa0\n',
                    b'package:/.apk\n', b'package:/data/app/a/base.apk\n\n']:
            with self.subTest(raw=raw):
                ctx = {'call': lambda *a, **kw: subprocess.CompletedProcess(a, 0, raw, b'')}
                with self.assertRaises(RuntimeError):
                    self.module._alias_absent(ctx, APP)

    def test_unknown_package_query_is_never_inferred_absent_or_uninstalled(self):
        cases = [
            (0, b'garbage\n', b''),
            (0, b'\n', b''),
            (1, b'package:/data/app/foreign/base.apk\n', b''),
            (0, b'', b'Error: package manager unavailable\n'),
            (1, b'', b'error: device offline\n'),
            (2, b'', b''),
            (0, b'package:/data/app/foreign/base.apk\n\n', b''),
            (0, b'package:/data/app/foreign/base.apk\r\n', b''),
            (0, b'package:/data/app/foreign/base.apk\x00\n', b''),
            (0, b'package:/data/app/foreign/base.apk\n', b'warning\n'),
            (0, b'package:/data/app/foreign/base.apk\nnot-a-path\n', b''),
            (0, b'package:/data/app/foreign/base\n', b''),
            (0, b'package:/data/app/foreign/base.apk \n', b''),
            (0, b'\xff', b''),
        ]
        for code, stdout, stderr in cases:
            with self.subTest(code=code, stdout=stdout, stderr=stderr):
                f = self.fixture()
                real_shell(f, response={'code': code, 'stdout': stdout.hex(), 'stderr': stderr.hex()})
                result = self.restore(f)
                self.assertIs(result['allRestored'], False)
                self.assertTrue(result['errors'])
                effects = f.mutations()[f.restore_start:]
                self.assertFalse(any(row['label'] in ('uninstall', 'force-stop') for row in effects))
                self.assertTrue((f.device / 'apps' / APP / 'base.apk').is_file())
                self.assertTrue((f.device / 'apps' / TEST / 'base.apk').is_file())
                self.assert_ordinary(f)


if __name__ == '__main__':
    unittest.main()

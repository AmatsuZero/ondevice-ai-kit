"""Regression checks using the retained local evidence, with isolated partial copies."""
import contextlib
import importlib.util
import io
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("complete_check", HERE / "check_complete.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
PARTIALS = ("downloads/pytorch_cpuinfo.zip", "unattempted/downloads/onnx.zip")


class PreservedPartialTests(unittest.TestCase):
    def test_original_evidence_passes(self):
        with contextlib.redirect_stdout(io.StringIO()):
            result = MODULE.main()
        self.assertTrue(result["originalTimeoutsAndPartialsPreserved"])

    def assert_tamper_rejected(self, relative_path):
        with tempfile.TemporaryDirectory() as tmp:
            isolated_old = Path(tmp)
            receipts = json.loads((MODULE.OLD / "available-downloads.json").read_bytes())
            (isolated_old / "available-downloads.json").write_text(json.dumps(receipts))
            # Complete archives are only read; mutated partials use independent copies.
            for row in receipts["receipts"]:
                source = MODULE.OLD / row["archivePath"]
                target = isolated_old / row["archivePath"]
                target.parent.mkdir(parents=True, exist_ok=True)
                os.link(source, target)
            for name in PARTIALS:
                target = isolated_old / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(MODULE.OLD / name, target)
            target = isolated_old / relative_path
            before_size = target.stat().st_size
            with target.open("r+b") as stream:
                first = stream.read(1)
                stream.seek(0)
                stream.write(bytes([first[0] ^ 1]))
            self.assertEqual(target.stat().st_size, before_size)
            with patch.object(MODULE, "OLD", isolated_old), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(AssertionError):
                    MODULE.main()

    def test_same_size_cpuinfo_tamper_is_rejected(self):
        self.assert_tamper_rejected(PARTIALS[0])

    def test_same_size_onnx_tamper_is_rejected(self):
        self.assert_tamper_rejected(PARTIALS[1])


if __name__ == "__main__":
    unittest.main()

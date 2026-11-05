from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from tools.generate_mlxvlm_binary_consumer import REPORT_WRITER


@unittest.skipUnless(shutil.which("swiftc"), "Swift compiler required")
class BinaryReportWriterTests(unittest.TestCase):
    def test_actual_foundation_persistence_controls_returned_verdict(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "main.swift"
            source.write_text("import Foundation\n" + REPORT_WRITER + '''
let directory = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
let original = ["status": "passed", "scope": "binary-resource-only"]
let good = persistResourceReport(original, directory: directory)
let persisted = try JSONSerialization.jsonObject(with: Data(contentsOf: directory.appendingPathComponent("binary-resource-report.json"))) as! [String: String]
precondition(good == original && persisted == original)
let blocker = directory.appendingPathComponent("regular-file")
try Data("keep".utf8).write(to: blocker)
let bad = persistResourceReport(original, directory: blocker)
precondition(bad["status"] == "failed-report-write" && bad["errorType"] != nil)
let preserved = try String(contentsOf: blocker, encoding: .utf8)
precondition(preserved == "keep")
let missing = persistResourceReport(original, directory: nil)
precondition(missing["status"] == "failed-report-write")
print("actual-file-roundtrip-and-failures-passed")
''')
            binary = root / "writer"
            subprocess.run(["swiftc", str(source), "-o", str(binary)], check=True, capture_output=True)
            result = subprocess.check_output([str(binary), str(root / "output")], text=True)
            self.assertIn("actual-file-roundtrip-and-failures-passed", result)

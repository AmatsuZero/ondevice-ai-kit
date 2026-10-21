import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
ASR_SOURCE = "Sources/ODAIKitAppleSpeech/AppleSpeechRecognizerBackend.swift"


class DistributionProjectTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / "scripts").mkdir()
        shutil.copy(ROOT / "scripts/generate-distribution-project.rb", self.root / "scripts")
        shutil.copytree(ROOT / "apple/Sources", self.root / "apple/Sources")
        self.project = self.root / "apple/OnDeviceAIKit.xcodeproj"
        shutil.copytree(ROOT / "apple/OnDeviceAIKit.xcodeproj", self.project)
        self.ruby = os.environ.get("RUBY", "ruby")

    def generate(self):
        return subprocess.run(
            [self.ruby, str(self.root / "scripts/generate-distribution-project.rb")],
            capture_output=True, text=True, check=False,
        )

    def testAsrSourceAndFrameworkBelongOnlyToAppleSpeech(self):
        generated = self.generate()
        self.assertEqual(generated.returncode, 0, generated.stderr)
        inspected = subprocess.run(
            [self.ruby, "-rxcodeproj", "-rjson", "-e", """
project = Xcodeproj::Project.open(ARGV.fetch(0))
puts JSON.generate(project.targets.to_h { |target|
  [target.name, {
    sources: target.source_build_phase.files.map { |file| file.file_ref.path },
    frameworks: target.frameworks_build_phase.files.map { |file| File.basename(file.file_ref.path) }
  }]
})
""", str(self.project)], capture_output=True, text=True, check=True,
        )
        targets = json.loads(inspected.stdout)
        self.assertEqual(len(targets), 9)
        for name, target in targets.items():
            expected = 1 if name == "ODAIKitAppleSpeech" else 0
            self.assertEqual(target["sources"].count(ASR_SOURCE), expected, name)
            self.assertEqual(target["frameworks"].count("Speech.framework"), expected, name)

    def testIdentityIncludesAllSourcesAndOnlyNativeDependencies(self):
        generated = self.generate()
        self.assertEqual(generated.returncode, 0, generated.stderr)
        inspected = subprocess.run(
            [self.ruby, "-rxcodeproj", "-rjson", "-e", """
project = Xcodeproj::Project.open(ARGV.fetch(0))
target = project.targets.find { |candidate| candidate.name == "ODAIKitIdentity" }
abort("Missing Identity distribution target") unless target
puts JSON.generate({
  sources: target.source_build_phase.files.map { |file| file.file_ref.path },
  dependencies: target.dependencies.map { |dependency| dependency.target.name }
})
""", str(self.project)], capture_output=True, text=True, check=False,
        )
        self.assertEqual(inspected.returncode, 0, inspected.stderr)
        target = json.loads(inspected.stdout)
        expected = sorted(str(path.relative_to(self.root / "apple")) for path in
                          (self.root / "apple/Sources/ODAIKitIdentity").glob("*.swift"))
        self.assertTrue(expected)
        self.assertEqual(sorted(target["sources"]), expected)
        self.assertEqual(sorted(target["dependencies"]), ["ODAIKitCore", "ODAIKitModelStore"])

    def testMissingAsrSourceRejectsGenerationWithoutSavingProject(self):
        before = (self.project / "project.pbxproj").read_bytes()
        (self.root / "apple" / ASR_SOURCE).unlink()
        generated = self.generate()
        self.assertNotEqual(generated.returncode, 0)
        self.assertIn(ASR_SOURCE, generated.stderr)
        self.assertEqual((self.project / "project.pbxproj").read_bytes(), before)

    def testVideoIncludesAllSourcesAndOnlyCoreDependency(self):
        generated = self.generate()
        self.assertEqual(generated.returncode, 0, generated.stderr)
        inspected = subprocess.run(
            [self.ruby, "-rxcodeproj", "-rjson", "-e", """
project = Xcodeproj::Project.open(ARGV.fetch(0))
target = project.targets.find { |candidate| candidate.name == "ODAIKitVideo" }
abort("Missing Video distribution target") unless target
puts JSON.generate({
  sources: target.source_build_phase.files.map { |file| file.file_ref.path },
  dependencies: target.dependencies.map { |dependency| dependency.target.name },
  frameworks: target.frameworks_build_phase.files.map { |file| File.basename(file.file_ref.path) }
})
""", str(self.project)], capture_output=True, text=True, check=False,
        )
        self.assertEqual(inspected.returncode, 0, inspected.stderr)
        target = json.loads(inspected.stdout)
        expected = sorted(str(path.relative_to(self.root / "apple")) for path in
                          (self.root / "apple/Sources/ODAIKitVideo").glob("*.swift"))
        self.assertTrue(expected)
        self.assertEqual(sorted(target["sources"]), expected)
        self.assertEqual(target["dependencies"], ["ODAIKitCore"])
        self.assertEqual(sorted(target["frameworks"]), ["Foundation.framework", "ODAIKitCore.framework"])

    def testRepeatedGenerationKeepsProjectAndSchemeUnchanged(self):
        first = self.generate()
        self.assertEqual(first.returncode, 0, first.stderr)
        paths = [self.project / "project.pbxproj",
                 self.project / "xcshareddata/xcschemes/SDKDistribution.xcscheme"]
        before = [path.read_bytes() for path in paths]
        second = self.generate()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual([path.read_bytes() for path in paths], before)

    def testAppleVideoIncludesAllSourcesAndOnlyCoreVideoDependencies(self):
        generated = self.generate()
        self.assertEqual(generated.returncode, 0, generated.stderr)
        inspected = subprocess.run(
            [self.ruby, "-rxcodeproj", "-rjson", "-e", """
project = Xcodeproj::Project.open(ARGV.fetch(0))
target = project.targets.find { |candidate| candidate.name == "ODAIKitAppleVideo" }
abort("Missing AppleVideo distribution target") unless target
puts JSON.generate({
  sources: target.source_build_phase.files.map { |file| file.file_ref.path },
  dependencies: target.dependencies.map { |dependency| dependency.target.name }
})
""", str(self.project)], capture_output=True, text=True, check=False,
        )
        self.assertEqual(inspected.returncode, 0, inspected.stderr)
        target = json.loads(inspected.stdout)
        expected = sorted(str(path.relative_to(self.root / "apple")) for path in
                          (self.root / "apple/Sources/ODAIKitAppleVideo").glob("*.swift"))
        self.assertTrue(expected)
        self.assertEqual(sorted(target["sources"]), expected)
        self.assertEqual(sorted(target["dependencies"]), ["ODAIKitCore", "ODAIKitVideo"])


if __name__ == "__main__":
    unittest.main()

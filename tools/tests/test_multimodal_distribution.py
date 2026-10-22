import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PRODUCTS = ["ODAIKitCore", "ODAIKitMultimodal"]


class MultimodalDistributionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / "scripts").mkdir()
        for script in ["generate-distribution-project.rb", "generate-binary-consumer.rb", "package-apple.sh"]:
            shutil.copy(ROOT / "scripts" / script, self.root / "scripts")
        shutil.copytree(ROOT / "apple", self.root / "apple",
                        ignore=shutil.ignore_patterns(".build"))
        shutil.copytree(ROOT / "examples", self.root / "examples",
                        ignore=shutil.ignore_patterns("build", ".gradle"))
        self.ruby = os.environ.get("RUBY", "ruby")
        self.artifacts = self.root / "artifacts"

    def run_script(self, name, *arguments):
        return subprocess.run([self.ruby, str(self.root / "scripts" / name), *arguments],
                              capture_output=True, text=True,
                              env={**os.environ, "ODAI_ARTIFACTS_DIR": str(self.artifacts)})

    def frameworks(self):
        for product in PRODUCTS:
            (self.artifacts / "xcframeworks" / (product + ".xcframework")).mkdir(parents=True)

    def inspect(self, project):
        result = subprocess.run([self.ruby, "-rxcodeproj", "-rjson", "-e", """
project = Xcodeproj::Project.open(ARGV.fetch(0))
puts JSON.generate(project.targets.to_h { |t| [t.name, {
  sources: t.source_build_phase.files.map { |f| f.file_ref.path },
  dependencies: t.dependencies.map { |d| d.target.name },
  frameworks: t.frameworks_build_phase.files.map { |f| File.basename(f.file_ref.path) },
  deployments: t.build_configurations.map { |c| c.build_settings["IPHONEOS_DEPLOYMENT_TARGET"] }
}] })
""", str(project)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def testDistributionIncludesAllMultimodalSourcesOnlyCoreAndScheme(self):
        generated = self.run_script("generate-distribution-project.rb")
        self.assertEqual(generated.returncode, 0, generated.stderr)
        project = self.root / "apple/OnDeviceAIKit.xcodeproj"
        targets = self.inspect(project)
        self.assertIn("ODAIKitMultimodal", targets)
        target = targets["ODAIKitMultimodal"]
        expected = sorted(str(p.relative_to(self.root / "apple")) for p in
                          (self.root / "apple/Sources/ODAIKitMultimodal").glob("*.swift"))
        self.assertEqual(sorted(target["sources"]), expected)
        self.assertEqual(target["dependencies"], ["ODAIKitCore"])
        self.assertEqual(sorted(target["frameworks"]), ["Foundation.framework", "ODAIKitCore.framework"])
        self.assertTrue(all(value == "15.0" for value in target["deployments"]))
        scheme = ET.parse(project / "xcshareddata/xcschemes/SDKDistribution.xcscheme")
        self.assertIn("ODAIKitMultimodal", [e.attrib["BlueprintName"] for e in scheme.iter("BuildableReference")])

    def testPackageIncludesMultimodal(self):
        package = (self.root / "scripts/package-apple.sh").read_text()
        products = next(line for line in package.splitlines() if line.startswith("products="))
        self.assertIn("ODAIKitMultimodal", products.split("'")[1].split())

    def testBinaryConsumerUsesOnlyCoreMultimodalAndActualContract(self):
        self.frameworks()
        generated = self.run_script("generate-binary-consumer.rb", "--multimodal")
        self.assertEqual(generated.returncode, 0, generated.stderr)
        output = self.artifacts / "MultimodalBinaryConsumer"
        targets = self.inspect(output / "MultimodalBinaryConsumer.xcodeproj")
        target = targets["MultimodalBinaryConsumer"]
        self.assertEqual(sorted(target["frameworks"]), sorted(["Foundation.framework"] + [p + ".xcframework" for p in PRODUCTS]))
        self.assertEqual(target["deployments"], ["15.0", "15.0"])
        source = (output / "MultimodalConsumerApp.swift").read_text()
        for api in ["import ODAIKitMultimodal", "MultimodalRequest", "MultimodalGenerationClient"]:
            self.assertIn(api, source)
        for forbidden in ["import MLX", "import ODAIKitVideo", "import ODAIKitSpeech", "import ODAIKitIdentity"]:
            self.assertNotIn(forbidden, source)

    def testMissingArtifactAndExistingOutputRejectBeforeWriting(self):
        self.frameworks()
        shutil.rmtree(self.artifacts / "xcframeworks/ODAIKitMultimodal.xcframework")
        missing = self.run_script("generate-binary-consumer.rb", "--multimodal")
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("Missing framework", missing.stderr)
        output = self.artifacts / "MultimodalBinaryConsumer"
        self.assertFalse(output.exists())
        (self.artifacts / "xcframeworks/ODAIKitMultimodal.xcframework").mkdir()
        output.mkdir()
        sentinel = output / "keep.txt"
        sentinel.write_text("preserve")
        existing = self.run_script("generate-binary-consumer.rb", "--multimodal")
        self.assertNotEqual(existing.returncode, 0)
        self.assertIn("Refusing to replace", existing.stderr)
        self.assertEqual(list(output.iterdir()), [sentinel])
        self.assertEqual(sentinel.read_text(), "preserve")

    def testConflictingConsumerModesRejectWithoutOutput(self):
        self.frameworks()
        for other in ["--speech", "--identity"]:
            generated = self.run_script("generate-binary-consumer.rb", "--multimodal", other)
            self.assertNotEqual(generated.returncode, 0)
            self.assertIn("Usage:", generated.stderr)
            self.assertFalse((self.artifacts / "MultimodalBinaryConsumer").exists())


if __name__ == "__main__":
    unittest.main()

import json
import hashlib
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools.tests import test_mlxvlm_static_artifact as fixtures
from tools.tests.test_mlxvlm_static_artifact import tree_sha
from tools.generate_mlxvlm_binary_consumer import generate
from tools.validate_mlxvlm_binary_artifact import validate_manifest


class StaticBinaryConsumerTests(unittest.TestCase):
    make_fixture = fixtures.StaticArtifactManifestTests.make_fixture

    def test_relocated_static_project_uses_matching_platform_resources_and_no_sources(self):
        root, manifest, payload = self.make_fixture()
        original = payload["artifacts"][0]
        artifact = root / original["path"]
        module = "ODAIKitMultimodal"
        extra = artifact.with_name(module + ".xcframework")
        shutil.copytree(artifact, extra)
        for path in sorted(extra.rglob("*"), key=lambda p: len(p.parts), reverse=True):
            if "ODAIKitAppleMLXVLM" in path.name:
                path.rename(path.with_name(path.name.replace("ODAIKitAppleMLXVLM", module)))
        metadata = plistlib.loads((extra / "Info.plist").read_bytes())
        for entry in metadata["AvailableLibraries"]:
            entry["LibraryPath"] = module + ".framework"
        (extra / "Info.plist").write_bytes(plistlib.dumps(metadata))
        entry = json.loads(json.dumps(original))
        entry.update(path=str(extra.relative_to(root)), modules=[module], sha256=tree_sha(extra))
        for interface in entry["interfaces"]:
            interface["path"] = interface["path"].replace("ODAIKitAppleMLXVLM", module)
        payload["artifacts"].append(entry)
        payload["moduleClosure"].append(module)
        payload["sourceAttributions"].append({**payload["sourceAttributions"][0], "module": module})
        for resource in payload["resources"]:
            original_path = root / resource["path"]
            bundle = "model.bundle" if original_path.suffix == ".json" else "metal.bundle"
            target = original_path.parent / bundle / original_path.name
            target.parent.mkdir(exist_ok=True)
            original_path.rename(target)
            resource["path"] = str(target.relative_to(root))
        manifest.write_text(json.dumps(payload))
        with patch("tools.inspect_mlxvlm_xcframework._inspect_binary", side_effect=lambda p: {
            "architectures": ["arm64"], "minimumOS": "18.0",
            "platforms": {"7" if "simulator" in str(p) else "2"}}):
            output = root / "consumer"
            generate(root, manifest, output)
        moved = root / "relocated consumer"
        output.rename(moved)
        validate_manifest(moved / "artifact-manifest.json", moved / "artifacts")
        spec = json.loads((moved / "project.json").read_text())
        target = spec["targets"]["MLXVLMBinaryConsumer"]
        self.assertTrue(all(d.get("embed") is False for d in target["dependencies"]))
        settings = target["settings"]["base"]
        self.assertIn("$(SRCROOT)/artifacts/CModules", settings["SWIFT_INCLUDE_PATHS"])
        self.assertIn("-lc++", settings["OTHER_LDFLAGS"])
        objects = json.loads(subprocess.check_output(["plutil", "-convert", "json", "-o", "-", str(moved / "MLXVLMBinaryConsumer.xcodeproj/project.pbxproj")]))["objects"]
        self.assertFalse(any("SwiftPackage" in o.get("isa", "") for o in objects.values()))
        self.assertFalse(any(o.get("isa") == "PBXCopyFilesBuildPhase" and o.get("files") for o in objects.values()))
        scripts = [o["shellScript"] for o in objects.values() if o.get("isa") == "PBXShellScriptBuildPhase"]
        self.assertEqual(len(scripts), 1)
        self.assertIn("iphonesimulator)", scripts[0])
        self.assertIn("resources/simulator/metal.bundle", scripts[0])
        self.assertIn("resources/device/metal.bundle", scripts[0])
        self.assertNotIn(str(root), scripts[0])
        # Execute the actual generated Xcode build phase for both destinations.
        for platform, sdk in (("device", "iphoneos"), ("simulator", "iphonesimulator")):
            products = moved / ("products-" + platform)
            app = products / "Probe.app"
            app.mkdir(parents=True)
            subprocess.run(["/bin/sh", "-c", scripts[0]], check=True, env={
                **os.environ, "PLATFORM_NAME": sdk, "SRCROOT": str(moved),
                "TARGET_BUILD_DIR": str(products), "UNLOCALIZED_RESOURCES_FOLDER_PATH": "Probe.app"})
            original_metal = root / "resources" / platform / "metal.bundle/default.metallib"
            self.assertEqual((app / "metal.bundle/default.metallib").read_bytes(), original_metal.read_bytes())
            self.assertEqual({p.name for p in app.iterdir()}, {"model.bundle", "metal.bundle"})
        source = (moved / "ConsumerApp.swift").read_text()
        self.assertIn(hashlib.sha256(manifest.read_bytes()).hexdigest(), source)
        self.assertIn("#if targetEnvironment(simulator)", source)
        for resource in payload["resources"]:
            self.assertIn(resource["sha256"], source)
        subprocess.run(["xcrun", "swiftc", "-frontend", "-parse", str(moved / "ConsumerApp.swift")], check=True, capture_output=True)

"""The optional consumer must use the real isolated package without raising base floors."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AppleMLXConsumerTests(unittest.TestCase):
    def test_independent_generated_consumer_links_real_sdk_products(self):
        spec = ROOT / 'examples/ios-mlxvlm-native/project.yml'
        self.assertTrue(spec.is_file(), 'optional MLXVLM consumer spec is missing')
        with tempfile.TemporaryDirectory() as output:
            subprocess.run([sys.executable, str(ROOT / 'tools/generate_mlxvlm_consumer.py'), output], check=True, capture_output=True)
            self.assertTrue((Path(output) / 'MLXVLMConsumerApp.swift').is_file())
            retry = subprocess.run([sys.executable, str(ROOT / 'tools/generate_mlxvlm_consumer.py'), output], capture_output=True)
            self.assertNotEqual(retry.returncode, 0, 'existing consumer was overwritten')
            pbx = Path(output) / 'MLXVLMConsumer.xcodeproj/project.pbxproj'
            objects = json.loads(subprocess.check_output(['plutil', '-convert', 'json', '-o', '-', str(pbx)]))['objects']
            names = {v['productName'] for v in objects.values() if v.get('isa') == 'XCSwiftPackageProductDependency'}
            self.assertEqual(names, {'ODAIKitAppleMLXVLM', 'ODAIKitModelStore', 'ODAIKitMultimodal'})
            targets = [v for v in objects.values() if v.get('isa') == 'PBXNativeTarget']
            self.assertEqual(len(targets), 1)
            configs = [v['buildSettings'] for v in objects.values() if v.get('isa') == 'XCBuildConfiguration']
            self.assertTrue(any(v.get('IPHONEOS_DEPLOYMENT_TARGET') == '18.0' for v in configs))

    def test_base_products_remain_mlx_free(self):
        value = subprocess.check_output(['swift', 'package', '--package-path', str(ROOT / 'apple'), 'dump-package'])
        manifest = json.loads(value)
        self.assertEqual(manifest['dependencies'], [])
        platforms = {v['platformName']: v['version'] for v in manifest['platforms']}
        self.assertEqual(platforms['ios'], '15.0')
        self.assertEqual(platforms['macos'], '12.0')
        target = next(v for v in manifest['targets'] if v['name'] == 'ODAIKitMultimodal')
        self.assertEqual(target['dependencies'], [{'byName': ['ODAIKitCore', None]}])


if __name__ == '__main__':
    unittest.main()

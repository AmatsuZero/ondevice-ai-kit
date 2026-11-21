"""Parent-owned isolated build/package verification; never installs or loads a model."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/verification/w3-qwen-load-20261110'
SOURCE = OUT / 'source'
SDK = Path.home() / 'Library/Android/sdk'
JDK = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home'
AAR = ROOT / 'artifacts/verification/sherpa-asr-20261105/inputs/downloads/sherpa-onnx-2.0.1.aar'
REPO = ROOT / 'artifacts/verification/sherpa-asr-20261105/inputs/m2'
env = os.environ.copy()
env.update(JAVA_HOME=str(JDK), ANDROID_HOME=str(SDK), ANDROID_SDK_ROOT=str(SDK))
env['PATH'] = str(JDK / 'bin') + os.pathsep + env['PATH']


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def command(args):
    proc = subprocess.run(['rtk', 'proxy'] + list(map(str, args)), cwd=SOURCE, env=env, capture_output=True, text=True)
    if proc.returncode:
        raise RuntimeError({'argv': list(map(str, args)), 'exitCode': proc.returncode,
                            'stdout': proc.stdout[-2500:], 'stderr': proc.stderr[-2500:]})
    return proc.stdout


action = sys.argv[1]
assert action in ('build', 'verify')
if action == 'build':
    assert (OUT / 'candidate-accepted.json').is_file(), 'host acceptance must precede build'
    receipt = json.loads((OUT / 'candidate-accepted.json').read_text())
    for rel, digest in receipt['sourceFiles'].items():
        assert sha(SOURCE / rel) == digest, rel
    args = ['./gradlew', '--offline', '--no-daemon', '--max-workers=2',
            '-PsherpaMavenRepo=' + str(REPO), 'assembleDebug', 'assembleDebugAndroidTest', 'lintDebug', '--console=plain']
    start = time.monotonic()
    with (OUT / 'build.log').open('x') as log:
        proc = subprocess.run(['rtk', 'proxy'] + args, cwd=SOURCE, env=env, stdout=log, stderr=subprocess.STDOUT)
    result = {'exitCode': proc.returncode, 'seconds': time.monotonic() - start, 'argv': args,
              'sourceFiles': receipt['sourceFiles'], 'deviceOperations': 0}
    (OUT / 'build-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    raise SystemExit(proc.returncode)

assert json.loads((OUT / 'build-result.json').read_text())['exitCode'] == 0
assert sha(AAR) == 'a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7'
for rel, digest in json.loads((OUT / 'candidate-accepted.json').read_text())['sourceFiles'].items():
    assert sha(SOURCE / rel) == digest, rel
main = list((SOURCE / 'build/outputs/apk/debug').glob('*.apk'))
test = list((SOURCE / 'build/outputs/apk/androidTest/debug').glob('*.apk'))
assert len(main) == len(test) == 1
aapt = SDK / 'build-tools/36.0.0/aapt'
analyzer = SDK / 'cmdline-tools/latest/bin/apkanalyzer'
signer = SDK / 'build-tools/36.0.0/apksigner'
result = {'files': {}, 'deviceOperations': 0, 'actualClassesVerified': [], 'runtimeLibraries': {}, 'permissions': {}}
certs = []
for apk, name, package, classes in [
    (main[0], 'main.apk', 'io.ondeviceai.acceptance.qwenload',
     ['QwenLoadActivity', 'ModelSpec', 'QwenModelFiles', 'LoadState', 'LoadCoordinator', 'LoadHandle']),
    (test[0], 'test.apk', 'io.ondeviceai.acceptance.qwenload.test', ['QwenLoadInstrumentation'])
]:
    badging = command([aapt, 'dump', 'badging', apk])
    permissions = command([aapt, 'dump', 'permissions', apk])
    assert "package: name='" + package + "'" in badging
    assert not re.search(r'uses-permission', permissions), permissions
    listing = command([analyzer, 'dex', 'packages', '--defined-only', apk])
    for cls in classes:
        assert cls in listing, cls
        result['actualClassesVerified'].append(cls)
    signing = command([signer, 'verify', '--verbose', '--print-certs', apk])
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signing)
    assert cert
    certs.append(cert.group(1))
    for label, content in [('badging', badging), ('permissions', permissions), ('dex', listing), ('signing', signing)]:
        (OUT / (name + '.' + label + '.txt')).write_text(content)
    target = OUT / name
    assert not target.exists()
    shutil.copyfile(apk, target)
    result['files'][name] = {'bytes': target.stat().st_size, 'sha256': sha(target), 'package': package}
    result['permissions'][name] = []
assert certs[0] == certs[1]
result['commonSigningCertificateSHA256'] = certs[0]
with zipfile.ZipFile(AAR) as source, zipfile.ZipFile(main[0]) as built:
    for entry in built.namelist():
        if entry.startswith('lib/') and entry.endswith('.so'):
            assert entry.startswith('lib/arm64-v8a/'), entry
            original = 'jni/' + entry.removeprefix('lib/')
            built_digest = hashlib.sha256(built.read(entry)).hexdigest()
            assert built_digest == hashlib.sha256(source.read(original)).hexdigest(), entry
            result['runtimeLibraries'][entry] = built_digest
assert result['runtimeLibraries'] and any('sherpa' in n for n in result['runtimeLibraries'])
manifest = command([aapt, 'dump', 'xmltree', main[0], 'AndroidManifest.xml'])
assert 'QwenLoadActivity' in manifest and 'android:exported' in manifest
(OUT / 'main.apk.manifest.txt').write_text(manifest)
result['sourceFingerprint'] = json.loads((OUT / 'candidate-accepted.json').read_text())['sourceFingerprint']
(OUT / 'package-verified.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))

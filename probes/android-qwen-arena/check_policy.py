"""Parent-owned real Kotlin/filesystem checks; no Android/JNI/model execution."""
from pathlib import Path
import hashlib, json, subprocess, sys

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'

def jar(group, name, version):
    return next((CACHE / group / name / version).glob('*/' + name + '-' + version + '.jar'))

def main():
    candidate, output = map(lambda p: Path(p).resolve(), sys.argv[1:])
    output.mkdir(parents=True, exist_ok=False)
    if not candidate.is_file():
        result = {'status': 'RED', 'reason': 'arena policy implementation absent', 'nativeCalls': 0, 'deviceOperations': 0}
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result)); return 1
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
    annotations = jar('org.jetbrains', 'annotations', '26.1.0')
    compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'), stdlib, annotations,
                jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
                jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
                jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
    java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    tests = Path(__file__).with_name('PolicyChecks.kt')
    classes = output / 'classes'; classes.mkdir()
    args = [str(java), '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-Werror', '-no-reflect', '-no-stdlib', '-classpath', f'{stdlib}:{annotations}', '-d', str(classes),
            str(candidate), str(tests)]
    p = subprocess.run(['rtk', 'proxy', *args], capture_output=True)
    (output / 'compile.stdout').write_bytes(p.stdout); (output / 'compile.stderr').write_bytes(p.stderr)
    result = {'sourceSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(),
              'testsSHA256': hashlib.sha256(tests.read_bytes()).hexdigest(), 'compileExitCode': p.returncode,
              'nativeCalls': 0, 'deviceOperations': 0}
    if p.returncode == 0:
        p = subprocess.run(['rtk', 'proxy', str(java), '-cp', f'{classes}:{stdlib}',
                            'io.ondeviceai.acceptance.qwenarena.PolicyChecksKt'], capture_output=True)
        (output / 'runtime.stdout').write_bytes(p.stdout); (output / 'runtime.stderr').write_bytes(p.stderr)
        result.update(runtimeExitCode=p.returncode, output=p.stdout.decode())
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n'); print(json.dumps(result))
    return p.returncode

if __name__ == '__main__':
    raise SystemExit(main())

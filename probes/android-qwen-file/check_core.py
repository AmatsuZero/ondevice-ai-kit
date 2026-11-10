"""Parent-owned JVM execution of real candidate with injected native boundaries."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def jar(group, name, version):
    return next(p for p in (CACHE / group / name / version).glob('*/*.jar')
                if not p.name.endswith(('-sources.jar', '-all.jar', '-common.jar')))


def main():
    candidate, output = map(Path, sys.argv[1:])
    candidate = candidate.resolve()
    output.mkdir(parents=True, exist_ok=False)
    if not candidate.is_file():
        receipt = {'status': 'RED', 'reason': 'two-stage implementation absent',
                   'nativeCalls': 0, 'deviceOperations': 0}
        (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt))
        return 1
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
    annotations = jar('org.jetbrains', 'annotations', '26.1.0')
    compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'), stdlib, annotations,
                jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
                jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
                jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
    java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    classes = output / 'classes'
    classes.mkdir()
    tests = Path(__file__).with_name('CoreChecks.kt')
    args = [str(java), '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-no-reflect', '-no-stdlib', '-classpath', f'{stdlib}:{annotations}', '-d', str(classes),
            str(candidate), str(tests)]
    p = subprocess.run(['rtk', 'proxy'] + args, capture_output=True, text=True)
    (output / 'compile.log').write_text(p.stdout + p.stderr)
    receipt = {'sourceSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(),
               'testsSHA256': hashlib.sha256(tests.read_bytes()).hexdigest(),
               'compileExitCode': p.returncode, 'nativeCalls': 0, 'deviceOperations': 0}
    if p.returncode == 0:
        p = subprocess.run(['rtk', 'proxy', str(java), '-cp', f'{classes}:{stdlib}',
                            'io.ondeviceai.acceptance.qwenfile.CoreChecksKt'], capture_output=True, text=True)
        (output / 'runtime.log').write_text(p.stdout + p.stderr)
        receipt.update(runtimeExitCode=p.returncode, output=p.stdout)
    (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt))
    return p.returncode


if __name__ == '__main__':
    raise SystemExit(main())

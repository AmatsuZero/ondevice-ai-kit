"""Parent-owned host acceptance for the intermediate heap recorder fragment; no device/native calls."""
from pathlib import Path
import hashlib
import importlib.util
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    candidate, output = (Path(value).resolve() for value in sys.argv[1:])
    output.mkdir(parents=True, exist_ok=False)
    receipt = {'realNativeCalls': 0, 'deviceOperations': 0}
    if not candidate.is_file():
        receipt.update(status='RED', reason='distinct recorder candidate absent')
        (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt))
        return 1
    helper = ROOT / 'probes/android-qwen-file/check_core.py'
    spec = importlib.util.spec_from_file_location('recorder_host_dependencies', helper)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    jar = module.jar
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
    annotations = jar('org.jetbrains', 'annotations', '26.1.0')
    jsonjar = jar('org.json', 'json', '20250517')
    compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'), stdlib, annotations,
                jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
                jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
                jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
    java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    classes = output / 'classes'
    classes.mkdir()
    sources = [candidate, ROOT / 'probes/android-qwen-file/QwenTwoStageCore.kt',
               Path(__file__).with_name('RecorderChecks.kt')]
    args = [str(java), '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-no-reflect', '-no-stdlib', '-Werror', '-classpath', ':'.join(map(str, [stdlib, annotations, jsonjar])),
            '-d', str(classes)] + list(map(str, sources))
    result = subprocess.run(['rtk', 'proxy'] + args, capture_output=True, text=True)
    (output / 'compile.log').write_text(result.stdout + result.stderr)
    receipt.update(compileExitCode=result.returncode,
                   sourceSHA256={str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources})
    if result.returncode == 0:
        result = subprocess.run(['rtk', 'proxy', str(java), '-cp', ':'.join(map(str, [classes, stdlib, jsonjar])),
                                 'io.ondeviceai.acceptance.qwenfile.RecorderChecksKt'], capture_output=True, text=True)
        (output / 'runtime.log').write_text(result.stdout + result.stderr)
        receipt.update(runtimeExitCode=result.returncode, output=result.stdout)
    (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt))
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())

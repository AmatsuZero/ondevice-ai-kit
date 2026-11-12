"""Parent-owned Kotlin acceptance runner; no Android, native inference or downloads."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def main(output):
    output.mkdir(parents=True, exist_ok=False)
    spec = importlib.util.spec_from_file_location(
        'terminal_compiler_dependencies', ROOT / 'probes/android-qwen-file/check_core.py')
    dependencies = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(dependencies)
    jar = dependencies.jar
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
    annotations = jar('org.jetbrains', 'annotations', '26.1.0')
    compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'),
                stdlib, annotations, jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
                jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
                jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
    java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    sources = [HERE / name for name in ('TerminalObservationBarrier.kt',
               'TerminalObservationChecks.kt', 'CrossIterationClockCheck.kt', 'AckFixture.kt')]
    hashes = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    classes = output / 'classes'
    classpath = ':'.join(map(str, (classes, stdlib, annotations)))
    receipt = {'scope': 'Pure Kotlin contract only; not IO, Android or device acceptance',
               'sourceSHA256': hashes, 'deviceOperations': 0, 'inferenceCalls': 0,
               'accepted': False, 'java': str(java), 'runtimeClasspath': classpath}

    def execute(name, args, timeout):
        result = subprocess.run(['rtk', 'proxy'] + args, capture_output=True, timeout=timeout)
        (output / (name + '.stdout')).write_bytes(result.stdout)
        (output / (name + '.stderr')).write_bytes(result.stderr)
        receipt[name + 'ExitCode'] = result.returncode
        return result

    try:
        compiled = execute('compile', [str(java), '-cp', ':'.join(map(str, compiler)),
                           'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-reflect',
                           '-no-stdlib', '-Werror', '-classpath',
                           ':'.join(map(str, (stdlib, annotations))), '-d', str(classes)]
                           + list(map(str, sources)), 60)
        if compiled.returncode != 0:
            return 1
        checks = execute('checks', [str(java), '-cp', classpath,
                         'io.ondeviceai.acceptance.terminal.TerminalObservationChecksKt'], 15)
        cross = execute('crossIteration', [str(java), '-cp', classpath,
                        'io.ondeviceai.acceptance.terminal.CrossIterationClockCheckKt'], 15)
        match = re.search(rb'TOTAL TESTS: (\d+)  PASSED: (\d+)  FAILED: 0', checks.stdout)
        assert match and match[1] == match[2] and int(match[1]) > 0
        assert checks.returncode == cross.returncode == 0
        assert b'PASS cross-iteration' in cross.stdout
        assert hashes == {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
        receipt.update(accepted=True, checks=int(match[1]), crossIterationRegression=True)
        return 0
    except (AssertionError, subprocess.TimeoutExpired, OSError) as error:
        receipt.update(errorClass=type(error).__name__, errorMessage=str(error))
        return 1
    finally:
        (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt))


if __name__ == '__main__':
    assert len(sys.argv) == 2, 'usage: check_barrier.py NEW_EVIDENCE_DIRECTORY'
    raise SystemExit(main(Path(sys.argv[1]).resolve()))

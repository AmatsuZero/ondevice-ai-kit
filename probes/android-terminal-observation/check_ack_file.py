"""Parent acceptance: cached compiler, real fixtures, no device or downloads."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--source', type=Path, default=HERE / 'TerminalAckFile.kt')
    parser.add_argument('--android-jar', type=Path)
    parser.add_argument('--tests', type=Path, default=HERE / 'TerminalAckFileChecks.kt')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    spec = importlib.util.spec_from_file_location(
        'cached_dependencies', ROOT / 'probes/android-qwen-file/check_core.py')
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
    sources = [ROOT / 'probes/android-terminal-observation/TerminalObservationBarrier.kt',
               args.tests.resolve(),
               HERE / 'AckBarrierFileIntegrationChecks.kt',
               HERE / 'TerminalAckFileBoundaryChecks.kt']
    if args.source:
        sources.append(args.source.resolve())
    if args.android_jar:
        assert args.source, 'Android compilation requires candidate source'
        sources = [sources[0], args.source.resolve()]
    hashes = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    classes = output / 'classes'
    runtime = ':'.join(map(str, [classes, stdlib, annotations]))
    receipt = {'sourceSHA256': hashes, 'accepted': False, 'deviceOperations': 0,
               'scope': 'JVM real-file fixtures; no Android IO or device acceptance'}
    compile_classpath = [stdlib, annotations]
    jdk_options = []
    if args.android_jar:
        compile_classpath.append(args.android_jar.resolve())
        jdk_options = ['-no-jdk']
        receipt.update(scope='Cached Android API classpath compilation only; no Android runtime IO',
                       androidJar=str(args.android_jar.resolve()),
                       androidJarSHA256=hashlib.sha256(args.android_jar.read_bytes()).hexdigest())

    def run(name, command):
        result = subprocess.run(['rtk', 'proxy'] + list(map(str, command)),
                                capture_output=True, timeout=60)
        (output / (name + '.stdout')).write_bytes(result.stdout)
        (output / (name + '.stderr')).write_bytes(result.stderr)
        receipt[name + 'ExitCode'] = result.returncode
        return result

    try:
        result = run('compile', [java, '-cp', ':'.join(map(str, compiler)),
                     'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-reflect',
                     '-no-stdlib', '-Werror', '-classpath',
                     ':'.join(map(str, compile_classpath)), '-d', classes] + jdk_options + sources)
        if result.returncode:
            receipt['missingFeatureRED'] = b"unresolved reference 'TerminalAckFile'" in result.stderr and not args.source
            return 1
        if args.android_jar:
            receipt['accepted'] = True
            return 0
        for name, klass in [('externalChecks', 'TerminalAckFileChecksKt'),
                            ('parentChecks', 'AckBarrierFileIntegrationChecksKt'),
                            ('boundaryChecks', 'TerminalAckFileBoundaryChecksKt')]:
            result = run(name, [java, '-cp', runtime,
                         'io.ondeviceai.acceptance.terminal.' + klass])
            match = re.search(rb'TOTAL TESTS: (\d+)  PASSED: (\d+)  FAILED: 0', result.stdout)
            if result.returncode or not match or match[1] != match[2]:
                return 1
            receipt[name + 'Passed'] = int(match[1])
        assert hashes == {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
        receipt['accepted'] = True
        return 0
    finally:
        (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt))


if __name__ == '__main__':
    raise SystemExit(main())

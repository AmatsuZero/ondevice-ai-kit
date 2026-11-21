"""Parent-only source preparation, gated on actual wrapper host output; never builds or runs."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'artifacts/verification/w3-qwen-heap-20261111'
OLD = ROOT/'artifacts/verification/w3-qwen-file-20261111'
SHARED_MAIN = ('QwenTwoStageCore.kt', 'QwenFileActivity.kt', 'QwenFileInputs.kt',
               'QwenFileReport.kt', 'QwenLoadCore.kt')


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)


def verify_host(path, root):
    host = json.loads(path.read_bytes())
    for key in ('compileExitCode', 'runtimeExitCode', 'realNativeCalls', 'deviceOperations'):
        assert type(host.get(key)) is int and host[key] == 0, key
    assert host.get('realCaseCore') is True and host.get('hostAndroidAndNativeDoubles') is True
    assert re.search(r'ASSERTIONS=[1-9][0-9]*', host.get('output', ''))
    sources = host['sourceSHA256']
    required = {str(root/'probes/android-qwen-heap/QwenHeapInstrumentation.kt'),
                str(root/'probes/android-qwen-arena/QwenArenaPolicy.kt')}
    required |= {str(root/'probes/android-qwen-file'/name) for name in SHARED_MAIN if name != 'QwenLoadCore.kt'}
    assert required <= set(sources), 'host source hashes incomplete'
    for name, wanted in sources.items():
        actual = Path(name).resolve()
        assert actual.is_relative_to(root.resolve()) and sha(actual) == wanted, name
    base_path = path.with_name('base-hashes.json')
    base = json.loads(base_path.read_bytes())
    for key, name in [('adapterSHA256', 'probes/android-qwen-heap/check_wrapper.py'),
                      ('scaffoldSHA256', 'probes/android-qwen-file/check_wrapper.py'),
                      ('heapChecksSHA256', 'probes/android-qwen-heap/HeapWrapperChecks.kt')]:
        assert base.get(key) == sha(root/name), key
    assert base['oldProbeSourcesModified'] is False
    assert type(base['selectedOriginalIndex']) is int and base['selectedOriginalIndex'] == 25
    return host, base_path


def main(host_path, *, root=ROOT, out=OUT, old=OLD):
    if (out/'source').exists() or (out/'candidate-accepted.json').exists():
        raise FileExistsError('output already prepared')
    host, base_path = verify_host(host_path, root)
    original = json.loads((old/'candidate-accepted.json').read_bytes())
    files = original['sourceFiles']
    assert files and original['sourceFingerprint'] == hashlib.sha256(
        json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    required = {'gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
                'settings.gradle.kts', 'build.gradle.kts', 'AndroidManifest.xml', 'test-assets/cases.json'}
    required |= {'main-src/'+name for name in SHARED_MAIN}
    assert required <= set(files), 'original source receipt incomplete'
    content = {}
    for name, wanted in files.items():
        relative = PurePosixPath(name)
        assert not relative.is_absolute() and '..' not in relative.parts and '\\' not in name
        path = old/'source'/name
        assert path.resolve().is_relative_to((old/'source').resolve()) and sha(path) == wanted, name
        if name != 'test-src/QwenFileInstrumentation.kt':
            content[name] = path.read_bytes()
    for name in SHARED_MAIN:
        directory = 'android-qwen-load' if name == 'QwenLoadCore.kt' else 'android-qwen-file'
        assert sha(root/'probes'/directory/name) == files['main-src/'+name], name
    gradle = content['build.gradle.kts'].decode().replace('io.ondeviceai.acceptance.qwenfile', 'io.ondeviceai.acceptance.qwenheap')
    gradle = replace_once(gradle, 'io.ondeviceai.acceptance.qwenheap.QwenFileInstrumentation',
                          'io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation')
    content['build.gradle.kts'] = gradle.encode()
    content['AndroidManifest.xml'] = replace_once(content['AndroidManifest.xml'].decode(),
                                                'Qwen FILE Acceptance', 'Qwen Heap Acceptance').encode()
    for name, relative in [('main-src/QwenArenaPolicy.kt', 'probes/android-qwen-arena/QwenArenaPolicy.kt'),
                           ('test-src/QwenHeapInstrumentation.kt', 'probes/android-qwen-heap/QwenHeapInstrumentation.kt')]:
        content[name] = (root/relative).read_bytes()
    source = out/'source'
    source.mkdir(parents=True, exist_ok=False)
    for name, data in content.items():
        path=source/name;path.parent.mkdir(parents=True,exist_ok=True)
        with path.open('xb') as stream:
            stream.write(data)
        if name in files:
            shutil.copymode(old/'source'/name, path)
    source_files={name:sha(source/name) for name in content}
    result={'sourceFiles':source_files,
            'sourceFingerprint':hashlib.sha256(json.dumps(source_files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'hostReceipt':{'path':str(host_path.relative_to(root)), 'sha256':sha(host_path),
                           'baseHashesPath':str(base_path.relative_to(root)), 'baseHashesSHA256':sha(base_path)},
            'oldSourceFingerprint':original['sourceFingerprint'], 'deviceOperations':0,
            'newNativeRunFrozen':False, 'nativePolicyEffectVerified':False}
    with (out/'candidate-accepted.json').open('x') as stream:
        json.dump(result,stream,indent=2);stream.write('\n')
    print(json.dumps({'sourceFingerprint':result['sourceFingerprint'],'deviceOperations':0,'newNativeRunFrozen':False}))
    return result


if __name__=='__main__':
    assert len(sys.argv)==2, 'supply the existing host result.json; no helper or device invocation'
    main(Path(sys.argv[1]).resolve())

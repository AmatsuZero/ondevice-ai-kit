"""Reuse the frozen FILE host scaffolding; add real policy source and boundary faults."""
from pathlib import Path
import hashlib, json, sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'probes/android-qwen-file/check_wrapper.py'
TESTS = ROOT / 'probes/android-qwen-file/WrapperChecks.kt'

def replace_once(source, old, new):
    assert source.count(old) == 1, old[:80]
    return source.replace(old, new, 1)

def main():
    candidate, output = map(lambda p: Path(p).resolve(), sys.argv[1:])
    if not candidate.is_file():
        output.mkdir(parents=True, exist_ok=False)
        r={'status':'RED','reason':'distinct arena wrapper absent','nativeCalls':0,'deviceOperations':0}
        (output/'result.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r));return 1
    baseline = BASE.read_text()
    tests = TESTS.read_text().replace('QwenFileInstrumentation','QwenArenaInstrumentation').replace('w3-qwen-file/','w3-qwen-arena/')
    tests = replace_once(tests, 'Runtime.streamEvents.clear(); Runtime.accepted.clear()', 'Runtime.streamEvents.clear(); Runtime.accepted.clear();Runtime.capturedProvider=null;Runtime.snapshotAttempts.clear()')
    extra = Path(__file__).with_name('ArenaWrapperChecks.kt').read_text()
    tests = replace_once(tests, '    println("ASSERTIONS=', extra+'\n    println("ASSERTIONS=')
    scaffold = replace_once(baseline, "stubs['Checks.kt'] = Path(__file__).with_name('WrapperChecks.kt').read_text()", "stubs['Checks.kt'] = arena_tests")
    inject = '''
    stubs['Runtime.kt'] = stubs['Runtime.kt'].replace('var writes=0;', 'val snapshotAttempts=mutableListOf<org.json.JSONObject>();var capturedProvider:String?=null;var writes=0;')
    old = '  out.close()'
    assert stubs['AtomicFile.kt'].count(old)==1
    stubs['AtomicFile.kt'] = stubs['AtomicFile.kt'].replace(old, old + '\\n  Runtime.snapshotAttempts+=JSONObject(pending.readText())')
    old = 'm.provider=="cpu"'
    assert stubs['Native.kt'].count(old)==1
    stubs['Native.kt'] = stubs['Native.kt'].replace(old, 'm.provider.startsWith("cpu:")')
    old = 'val m=c.modelConfig;val q=m.qwen3Asr'
    assert stubs['Native.kt'].count(old)==1
    stubs['Native.kt'] = stubs['Native.kt'].replace(old, old + '\\n  Runtime.capturedProvider=m.provider;require(File(m.provider.removePrefix("cpu:")).readText()=="EnableCpuMemArena=0\\\\n")')
'''
    scaffold = replace_once(scaffold, '    for name, content in stubs.items():', inject+'\n    for name, content in stubs.items():')
    scaffold = replace_once(scaffold, 'sources = [core, activity, wrapper,', 'sources = [arena_policy, core, activity, wrapper,')
    g={'__name__':'arena_parent_adapter','__file__':str(BASE),'arena_tests':tests,
       'arena_policy':ROOT/'probes/android-qwen-arena/QwenArenaPolicy.kt'}
    oldargv=sys.argv
    try:
        sys.argv=[str(BASE),str(ROOT/'probes/android-qwen-file/QwenTwoStageCore.kt'),
                  str(ROOT/'probes/android-qwen-file/QwenFileActivity.kt'),str(candidate),str(output)]
        exec(compile(scaffold,str(BASE)+'[arena-test-adapter]','exec'),g)
        code=g['main']()
    finally:
        sys.argv=oldargv
    (output/'base-hashes.json').write_text(json.dumps({'scaffoldSHA256':hashlib.sha256(BASE.read_bytes()).hexdigest(),
        'baselineChecksSHA256':hashlib.sha256(TESTS.read_bytes()).hexdigest(),
        'adapterSHA256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'oldProbeSourcesModified':False},indent=2)+'\n')
    return code

if __name__=='__main__':
    raise SystemExit(main())

"""Parent-owned acceptance: reuse fixed FILE Android/JNI doubles for one heap case."""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'probes/android-qwen-file/check_wrapper.py'


def once(source, old, new):
    assert source.count(old) == 1, old[:100]
    return source.replace(old, new, 1)


def main():
    candidate, output = [Path(p).resolve() for p in sys.argv[1:]]
    if not candidate.is_file():
        output.mkdir(parents=True, exist_ok=False)
        receipt = {'status': 'RED', 'reason': 'distinct heap wrapper absent', 'deviceOperations': 0, 'nativeCalls': 0}
        (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt))
        return 1

    tests = (ROOT / 'probes/android-qwen-file/WrapperChecks.kt').read_text()
    tests = tests.replace('QwenFileInstrumentation', 'QwenHeapInstrumentation').replace('w3-qwen-file/', 'w3-qwen-heap/')
    tests = tests.replace('complete65 cases', 'complete one selected case').replace('==130', '==2').replace('==65', '==1').replace('until 65', 'until 1')
    tests = tests.replace('            expect(j.getJSONArray("rows").getJSONObject(1).getString("status")=="planned")\n', '')
    tests = tests.replace('            expect(json().getJSONArray("rows").getJSONObject(1).getString("status")=="planned")\n', '')
    tests = once(tests, 'Runtime.streamEvents.clear(); Runtime.accepted.clear()',
                 'Runtime.streamEvents.clear(); Runtime.accepted.clear();Runtime.snapshotAttempts.clear();Runtime.heapFailWhen=null;Runtime.heapNegative=false;Runtime.heapReads=0;Runtime.capturedProvider=null')
    tests = once(tests, 'expect(Runtime.creates==1 && Runtime.decodes==0 && Runtime.releases==1)',
                 'expect(Runtime.creates==0 && Runtime.decodes==0 && Runtime.releases==0)')
    extra = Path(__file__).with_name('HeapWrapperChecks.kt').read_text()
    tests = once(tests, '    println("ASSERTIONS=', extra + '\n    println("ASSERTIONS=')
    scaffold = once(BASE.read_text(), "stubs['Checks.kt'] = Path(__file__).with_name('WrapperChecks.kt').read_text()", "stubs['Checks.kt'] = heap_tests")
    inject = '''
    stubs['Runtime.kt'] = stubs['Runtime.kt'].replace('var writes=0;', 'val snapshotAttempts=mutableListOf<org.json.JSONObject>();var capturedProvider:String?=null;var heapReads=0;var heapNegative=false;var heapFailWhen:(()->Boolean)?=null;var writes=0;')
    stubs['Debug.kt'] = """package android.os
object Debug {
 private fun value(n:Long):Long {checks.Runtime.heapReads++;if(checks.Runtime.heapFailWhen?.invoke()==true)throw IllegalStateException("injected-heap");return if(checks.Runtime.heapNegative)-1L else n}
 fun getNativeHeapSize():Long=value(1000L)
 fun getNativeHeapAllocatedSize():Long=value(600L)
 fun getNativeHeapFreeSize():Long=value(400L)
}
"""
    stubs['AtomicFile.kt'] = stubs['AtomicFile.kt'].replace('  out.close()', '  out.close()\\n  Runtime.snapshotAttempts+=JSONObject(pending.readText())', 1)
    stubs['Native.kt'] = stubs['Native.kt'].replace('m.provider=="cpu"','m.provider.startsWith("cpu:")')
    stubs['Native.kt'] = stubs['Native.kt'].replace('val m=c.modelConfig;val q=m.qwen3Asr', 'val m=c.modelConfig;val q=m.qwen3Asr\\n  Runtime.capturedProvider=m.provider;require(File(m.provider.removePrefix("cpu:")).readText()=="EnableCpuMemArena=0\\\\n")')
    old='rows.getJSONObject((index-1)/(if(Runtime.emptyAuto)1 else 2))'
    assert stubs['Native.kt'].count(old)==1
    stubs['Native.kt'] = stubs['Native.kt'].replace(old,'rows.getJSONObject(25)')
'''
    scaffold = once(scaffold, '    for name, content in stubs.items():', inject + '\n    for name, content in stubs.items():')
    scaffold = once(scaffold, 'sources = [core, activity, wrapper,', 'sources = [arena_policy, core, activity, wrapper,')
    scope = {'__name__': 'heap_parent_adapter', '__file__': str(BASE), 'heap_tests': tests,
             'arena_policy': ROOT / 'probes/android-qwen-arena/QwenArenaPolicy.kt'}
    old_args = sys.argv
    try:
        sys.argv = [str(BASE), str(ROOT / 'probes/android-qwen-file/QwenTwoStageCore.kt'),
                    str(ROOT / 'probes/android-qwen-file/QwenFileActivity.kt'), str(candidate), str(output)]
        exec(compile(scaffold, str(BASE) + '[heap-adapter]', 'exec'), scope)
        code = scope['main']()
    finally:
        sys.argv = old_args
    (output / 'base-hashes.json').write_text(json.dumps({
        'scaffoldSHA256': hashlib.sha256(BASE.read_bytes()).hexdigest(),
        'adapterSHA256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'heapChecksSHA256': hashlib.sha256(Path(__file__).with_name('HeapWrapperChecks.kt').read_bytes()).hexdigest(),
        'oldProbeSourcesModified': False, 'selectedOriginalIndex': 25,
    }, indent=2) + '\n')
    return code


if __name__ == '__main__':
    raise SystemExit(main())

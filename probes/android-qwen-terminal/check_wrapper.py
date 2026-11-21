"""Host acceptance for the distinct terminal wrapper, using fixed heap doubles."""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
HEAP = ROOT / 'probes/android-qwen-heap/check_wrapper.py'


def once(text, old, new):
    assert text.count(old) == 1, old[:120]
    return text.replace(old, new, 1)


def main():
    candidate, output = [Path(arg).resolve() for arg in sys.argv[1:]]
    fragments = Path(__file__).with_name('TerminalWrapperChecks.kt').read_text()
    factory, extra = fragments.split('// TERMINAL-CASES\n')
    adapter = HEAP.read_text().replace('QwenHeapInstrumentation', 'QwenTerminalInstrumentation')
    adapter = adapter.replace('w3-qwen-heap/', 'w3-qwen-terminal/')
    adapter = once(adapter, "    extra = Path(__file__).with_name('HeapWrapperChecks.kt').read_text()", '''
    tests = once(tests, 'QwenTerminalInstrumentation().onCreate(b)', 'terminalInstrumentation().onCreate(b)')
    tests = once(tests, 'private var assertions = 0', terminal_factory + '\\nprivate var assertions = 0')
    tests = once(tests, 'Runtime.streamEvents.clear();', 'Runtime.streamEvents.clear();Runtime.terminalWaits=0;Runtime.terminalStatReads=0;Runtime.terminalStatMode="valid";Runtime.terminalAckMode="ok";Runtime.terminalObservedBytes=null;Runtime.terminalBundle=null;')
    extra = Path(__file__).with_name('HeapWrapperChecks.kt').read_text() + '\\n' + terminal_extra
''')
    adapter = once(adapter,
        "inject + '\\n    for name, content in stubs.items():'",
        "inject + terminal_inject + '\\n    for name, content in stubs.items():'")
    adapter = once(adapter, "'sources = [arena_policy, core, activity, wrapper,'",
        "'sources = terminal_sources + [arena_policy, core, activity, wrapper,'")
    adapter = once(adapter, "'heap_tests': tests,", "'heap_tests': tests, 'terminal_sources': terminal_sources,")
    inject = '''
    stubs['Runtime.kt'] = stubs['Runtime.kt'].replace('var writes=0;', 'var terminalWaits=0;var terminalStatReads=0;var terminalStatMode="valid";var terminalAckMode="ok";var terminalObservedBytes:ByteArray?=null;var terminalBundle:android.os.Bundle?=null;var writes=0;')
    stubs['Bundle.kt'] = stubs['Bundle.kt'].replace(' fun putString(k:String,value:String){v[k]=value}', ' fun putString(k:String,value:String){v[k]=value}\\n fun putBoolean(k:String,value:Boolean){v[k]=value.toString()}\\n fun getBoolean(k:String):Boolean=v[k]?.toBooleanStrictOrNull() ?: false')
    stubs['Bundle.kt'] = stubs['Bundle.kt'].replace('object SystemClock {fun elapsedRealtimeNanos()', 'object SystemClock {fun elapsedRealtime():Long=elapsedRealtimeNanos()/1000000L;fun elapsedRealtimeNanos()')
    stubs['Activity.kt'] = stubs['Activity.kt'].replace('Runtime.code=code;Runtime.finished.countDown()', 'Runtime.terminalBundle=b;Runtime.code=code;Runtime.finished.countDown()')
'''
    scope = {
        '__name__': 'terminal_parent_adapter', '__file__': str(HEAP),
        'terminal_factory': factory, 'terminal_extra': extra, 'terminal_inject': inject,
        'terminal_sources': [ROOT/'probes/android-terminal-observation'/name
            for name in ['TerminalObservationBarrier.kt', 'TerminalAckFile.kt']],
    }
    old_args = sys.argv
    try:
        sys.argv = [str(HEAP), str(candidate), str(output)]
        exec(compile(adapter, str(HEAP)+'[terminal-adapter]', 'exec'), scope)
        code = scope['main']()
    finally:
        sys.argv = old_args
    (output/'terminal-adapter-hashes.json').write_text(json.dumps({
        str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in [HEAP, Path(__file__), Path(__file__).with_name('TerminalWrapperChecks.kt')]
    }, indent=2)+'\n')
    return code


if __name__ == '__main__':
    raise SystemExit(main())

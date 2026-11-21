"""Parent-owned host checks for the distinct KV288 wrapper; no real JNI/device."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'probes/android-qwen-arena/check_wrapper.py'


def once(source, before, after):
    assert source.count(before) == 1, before
    return source.replace(before, after, 1)


def main():
    source = BASE.read_text()
    source = source.replace('QwenArenaInstrumentation', 'QwenKv288Instrumentation')
    source = source.replace("'w3-qwen-arena/'", "'w3-qwen-kv288/'")
    old = "extra = Path(__file__).with_name('ArenaWrapperChecks.kt').read_text()"
    source = once(source, old, old + ".replace('cpu-arena-off-v1', 'cpu-arena-off-kv288-v1')\n    extra += kv_checks")
    inject = '''    inject += r"""
    stubs['Runtime.kt'] = stubs['Runtime.kt'].replace('var capturedProvider:String?=null;', 'var capturedProvider:String?=null;var capturedMaxTotalLen:Int?=null;')
    assert stubs['Native.kt'].count('q.maxTotalLen==512') == 1
    stubs['Native.kt'] = stubs['Native.kt'].replace('q.maxTotalLen==512', 'q.maxTotalLen==288')
    stubs['Native.kt'] = stubs['Native.kt'].replace('Runtime.capturedProvider=m.provider;', 'Runtime.capturedMaxTotalLen=q.maxTotalLen;Runtime.capturedProvider=m.provider;')
"""
'''
    # Insert extra stub checks before the inherited scaffold is compiled.
    target = "    scaffold = replace_once(scaffold, '    for name, content in stubs.items():'"
    assert source.count(target) == 1
    source = source.replace(target, inject + target, 1)
    checks = '''
    case("constructor uses capacity288 and every snapshot reports it truthfully") {
        for (mode in 0..2) {
            reset(); try {
                Runtime.capturedMaxTotalLen=null
                Runtime.decodeFail=mode==1; Runtime.constructorFail=mode==2
                start()
                expect(Runtime.capturedMaxTotalLen==288)
                expect(Runtime.snapshotAttempts.isNotEmpty())
                for(j in Runtime.snapshotAttempts) {
                    val c=j.getJSONObject("config")
                    expect(c.getInt("maxTotalLen")==288)
                    expect(c.getInt("maxNewTokens")==128)
                }
                expect(json().getBoolean("terminal"))
                expect(json().getString("status")==if(mode==0)"completed" else "failed")
            } finally { Runtime.root.deleteRecursively() }
        }
    }
    case("pre-native locked failure also reports288 without a constructor") {
        reset(); try {
            Runtime.capturedMaxTotalLen=null;Runtime.locked=true;start();failed()
            expect(Runtime.creates==0 && Runtime.capturedMaxTotalLen==null)
            for(j in Runtime.snapshotAttempts) expect(j.getJSONObject("config").getInt("maxTotalLen")==288)
        } finally { Runtime.root.deleteRecursively() }
    }
'''
    scope = {'__name__': 'kv288_parent_check_adapter', '__file__': str(BASE), 'kv_checks': checks}
    exec(compile(source, str(BASE) + '[kv288-host-adapter]', 'exec'), scope)
    return scope['main']()


if __name__ == '__main__':
    raise SystemExit(main())

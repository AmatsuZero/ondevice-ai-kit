"""Parent acceptance of the small visible Activity with host Android doubles."""
from pathlib import Path
import ast
import hashlib
import importlib.util
import json
import subprocess
import sys


def main():
    candidate,output=map(Path,sys.argv[1:]);output.mkdir(parents=True,exist_ok=False)
    if not candidate.is_file():
        result={'status':'RED','reason':'QwenFileActivity absent','deviceOperations':0}
        (output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));return 1
    root=Path(__file__).resolve().parents[2]
    tree=ast.parse((root/'probes/android-qwen-load/check_wrapper.py').read_text())
    stubs=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
    for name in ['Runtime.kt','Bundle.kt','Context.kt','Activity.kt','TextView.kt']:
        (output/name).write_text(stubs[name].replace('io.ondeviceai.acceptance.qwenload.QwenLoadActivity','io.ondeviceai.acceptance.qwenfile.QwenFileActivity'))
    (output/'Checks.kt').write_text('''package checks
import io.ondeviceai.acceptance.qwenfile.QwenFileActivity
fun main(){
 val a=QwenFileActivity();check(!a.resumeAcked && !a.pauseAcked && !a.destroyAcked)
 a.onCreate(null);check(!a.resumeAcked && !a.pauseAcked && !a.destroyAcked)
 a.onResume();check(a.resumeAcked && a.resumeArrived.count==0L && !a.pauseAcked)
 a.onPause();check(a.pauseAcked && !a.destroyAcked)
 a.onDestroy();check(a.destroyAcked && a.destroyArrived.count==0L)
 val b=QwenFileActivity();check(!b.resumeAcked && !b.destroyAcked && b.resumeArrived.count==1L)
 println("ASSERTIONS=6 FAILURES=0")
}
''')
    spec=importlib.util.spec_from_file_location('core_check',Path(__file__).with_name('check_core.py'));core=importlib.util.module_from_spec(spec);spec.loader.exec_module(core);jar=core.jar
    stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','2.2.20');annotations=jar('org.jetbrains','annotations','26.1.0')
    compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.2.20'),stdlib,annotations,
              jar('org.jetbrains.kotlin','kotlin-reflect','2.0.21'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.2.20'),
              jar('org.jetbrains.intellij.deps','trove4j','1.0.20200330'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0')]
    classes=output/'classes';classes.mkdir();java=root/'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    p=subprocess.run(['rtk','proxy',str(java),'-cp',':'.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                      '-no-reflect','-no-stdlib','-classpath',f'{stdlib}:{annotations}','-d',str(classes),str(candidate)]+list(map(str,output.glob('*.kt'))),capture_output=True,text=True)
    (output/'compile.log').write_text(p.stdout+p.stderr)
    result={'compileExitCode':p.returncode,'sourceSHA256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'hostAndroidDoubles':True,'deviceOperations':0}
    if p.returncode==0:
        p=subprocess.run(['rtk','proxy',str(java),'-cp',f'{classes}:{stdlib}','checks.ChecksKt'],capture_output=True,text=True)
        (output/'runtime.log').write_text(p.stdout+p.stderr);result.update(runtimeExitCode=p.returncode,output=p.stdout)
    (output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));return p.returncode


if __name__=='__main__':raise SystemExit(main())

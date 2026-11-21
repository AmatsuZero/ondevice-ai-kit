"""Real JVM fixture-verification acceptance; no Android/native/device execution."""
from pathlib import Path
import importlib.util
import hashlib
import json
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]


def main():
    candidate,output=map(Path,sys.argv[1:]);output.mkdir(parents=True,exist_ok=False)
    if not candidate.is_file():
        result={'status':'RED','reason':'QwenFileReport implementation absent','nativeCalls':0,'deviceOperations':0}
        (output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));return 1
    spec=importlib.util.spec_from_file_location('core_check',Path(__file__).with_name('check_core.py'))
    core=importlib.util.module_from_spec(spec);spec.loader.exec_module(core)
    jar=core.jar;stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','2.2.20');annotations=jar('org.jetbrains','annotations','26.1.0');jsonjar=jar('org.json','json','20250517')
    compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.2.20'),stdlib,annotations,
              jar('org.jetbrains.kotlin','kotlin-reflect','2.0.21'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.2.20'),
              jar('org.jetbrains.intellij.deps','trove4j','1.0.20200330'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0')]
    assets=output/'assets';(assets/'inputs').mkdir(parents=True)
    (assets/'cases.json').write_bytes(Path(__file__).with_name('cases.json').read_bytes())
    protocol=json.loads((ROOT/'.omx/artifacts/w3-model-reselection-20261110/independent-protocol.json').read_bytes())
    for c in protocol['cases']:(assets/'inputs'/(c['id']+'.pcm')).symlink_to(c['pcmPath'])
    classes=output/'classes';classes.mkdir();java=ROOT/'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    tests=Path(__file__).with_name('ReportChecks.kt')
    p=subprocess.run(['rtk','proxy',str(java),'-cp',':'.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                      '-no-reflect','-no-stdlib','-classpath',':'.join(map(str,[stdlib,annotations,jsonjar])),
                      '-d',str(classes),str(candidate),str(tests),str(ROOT/'probes/android-qwen-file/QwenTwoStageCore.kt'),str(ROOT/'probes/android-qwen-file/QwenFileInputs.kt'),str(ROOT/'probes/android-qwen-load/QwenLoadCore.kt')],capture_output=True,text=True)
    (output/'compile.log').write_text(p.stdout+p.stderr)
    result={'sourceSHA256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'testsSHA256':hashlib.sha256(tests.read_bytes()).hexdigest(),'compileExitCode':p.returncode,'nativeCalls':0,'deviceOperations':0}
    if p.returncode==0:
        p=subprocess.run(['rtk','proxy',str(java),'-cp',':'.join(map(str,[classes,stdlib,jsonjar])),
                          'io.ondeviceai.acceptance.qwenfile.ReportChecksKt'],capture_output=True,text=True)
        (output/'runtime.log').write_text(p.stdout+p.stderr);result.update(runtimeExitCode=p.returncode,output=p.stdout)
    (output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));return p.returncode


if __name__=='__main__':raise SystemExit(main())

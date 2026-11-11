"""Parent wrapper acceptance: real core/fixture bytes with Android and JNI doubles."""
from pathlib import Path
import ast
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def jar(group, name, version):
    return next(p for p in (CACHE / group / name / version).glob('*/*.jar')
                if not p.name.endswith(('-sources.jar', '-all.jar', '-common.jar')))


def main():
    core, activity, wrapper, output = map(Path, sys.argv[1:])
    output.mkdir(parents=True, exist_ok=False)
    if not all(p.is_file() for p in (core, activity, wrapper)):
        receipt = {'status': 'RED', 'reason': 'Android two-stage wrapper absent', 'deviceOperations': 0}
        (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt)); return 1
    # Reuse the already accepted host Android lifecycle doubles, never their top-level execution.
    tree = ast.parse((ROOT / 'probes/android-qwen-load/check_wrapper.py').read_text())
    stubs = ast.literal_eval(next(n.value for n in tree.body if isinstance(n, ast.Assign)
                                 and any(isinstance(t, ast.Name) and t.id == 'stubs' for t in n.targets)))
    stubs['Runtime.kt'] = '''package checks
import java.io.File
import java.util.concurrent.CountDownLatch
import org.json.JSONObject
object Runtime {
 lateinit var root:File; lateinit var assetsRoot:File
 var writes=0;var failWriteAt=0;var creates=0;var releases=0;var verifies=0
 var locked=false;var verifyFail=false;var constructorFail=false;var releaseFail=false
 var teardownAck=true;var lockAfterVerify=false;var code:Int?=null
 var finished=CountDownLatch(1)
 var streams=0;var streamReleases=0;var decodes=0;var streamReleaseFail=false
 var decodeFail=false;var emptyAuto=false;var badAsset:String?=null
 var failWhen:((JSONObject)->Boolean)?=null
 val streamEvents=mutableListOf<String>();val accepted=mutableListOf<FloatArray>()
}
'''
    stubs['Activity.kt'] = stubs['Activity.kt'].replace('io.ondeviceai.acceptance.qwenload.QwenLoadActivity',
                                                       'io.ondeviceai.acceptance.qwenfile.QwenFileActivity')
    stubs['Activity.kt'] = stubs['Activity.kt'].replace('val targetContext=Context()',
                                                       'val targetContext=Context(); val context=Context()')
    model_manifest = json.loads((ROOT / '.omx/artifacts/w3-model-reselection-20261110/model-manifest.json').read_bytes())
    models = {name:row for name,row in model_manifest['files'].items()
              if ('/' not in name and name.endswith('.onnx')) or name.startswith('tokenizer/')}
    kotlin_specs = ','.join('ModelSpec('+json.dumps(name)+','+str(row['bytes'])+'L,'+json.dumps(row['sha256'])+')'
                            for name,row in models.items())
    stubs['CoreDoubles.kt'] = stubs['CoreDoubles.kt'].replace('object QwenModelFiles {',
                                              'object QwenModelFiles { val frozenSpecs=listOf('+kotlin_specs+')')
    stubs['CoreDoubles.kt'] = stubs['CoreDoubles.kt'].replace('listOf(ModelSpec("mock",1,"0".repeat(64)))','frozenSpecs')
    stubs['Context.kt'] = stubs['Context.kt'].replace('open class Context {',
                  'open class Context { val assets=android.content.res.AssetManager()')
    stubs['Bundle.kt'] += '\nobject SystemClock {fun elapsedRealtimeNanos()=System.nanoTime()}\n'
    stubs['Assets.kt'] = '''package android.content.res
import checks.Runtime
import java.io.File
import java.io.InputStream
class AssetManager {
 fun open(name:String):InputStream {
  val b=File(Runtime.assetsRoot,name).readBytes()
  if(Runtime.badAsset==name && b.isNotEmpty()) b[b.size-1]=(b.last().toInt() xor 1).toByte()
  return b.inputStream()
 }
}
'''
    stubs['AtomicFile.kt'] = '''package android.util
import checks.Runtime
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
class AtomicFile(val baseFile:File) {
 val pending=File(baseFile.path+".new")
 fun startWrite():FileOutputStream {
  Runtime.writes++;if(Runtime.writes==Runtime.failWriteAt) throw IOException("injected-write")
  return FileOutputStream(pending)
 }
 fun finishWrite(out:FileOutputStream) {
  out.close()
  if(Runtime.failWhen?.invoke(JSONObject(pending.readText()))==true) {
   Runtime.failWhen=null;throw IOException("injected-conditional-write")
  }
  Files.move(pending.toPath(),baseFile.toPath(),StandardCopyOption.REPLACE_EXISTING)
 }
 fun failWrite(out:FileOutputStream){out.close();pending.delete()}
}
'''
    stubs['Native.kt'] += '''
class OfflineStream(val index:Int) {
 var language="unset"
 fun setOption(key:String,value:String){require(key=="language");language=value;Runtime.streamEvents+="option:$value"}
 fun acceptWaveform(samples:FloatArray,sampleRate:Int) {
  require(sampleRate==16000)
  val rows=org.json.JSONObject(File(Runtime.assetsRoot,"cases.json").readText()).getJSONArray("cases")
  val row=rows.getJSONObject((index-1)/(if(Runtime.emptyAuto)1 else 2))
  val pcm=File(Runtime.assetsRoot,row.getString("asset")).readBytes()
  require(samples.size==row.getInt("samples") && pcm.size==samples.size*2)
  for(i in samples.indices){val s=((pcm[2*i].toInt() and 255) or (pcm[2*i+1].toInt() shl 8)).toShort();require(samples[i].toRawBits()==(s.toFloat()/32768f).toRawBits())}
  Runtime.accepted+=samples;Runtime.streamEvents+="accept"
 }
 fun release(){Runtime.streamReleases++;if(Runtime.streamReleaseFail)throw IllegalStateException("injected-stream-release")}
}
class OfflineRecognizerResult(val text:String)
'''
    stubs['Native.kt'] = stubs['Native.kt'].replace(' fun release(){Runtime.releases++;', '''
 fun createStream():OfflineStream {Runtime.streams++;return OfflineStream(Runtime.streams)}
 fun decode(stream:OfflineStream){Runtime.decodes++;if(Runtime.decodeFail)throw IllegalStateException("injected-decode")}
 fun getResult(stream:OfflineStream)=OfflineRecognizerResult(if(stream.language=="") if(Runtime.emptyAuto) "" else "auto text" else "forced text")
 fun release(){Runtime.releases++;''')
    stubs['Checks.kt'] = Path(__file__).with_name('WrapperChecks.kt').read_text()
    for name, content in stubs.items():
        (output / name).write_text(content)
    assets = output / 'assets'; assets.mkdir(); (assets / 'inputs').mkdir()
    manifest = ROOT / 'probes/android-qwen-file/cases.json'
    (assets / 'cases.json').write_bytes(manifest.read_bytes())
    protocol = json.loads((ROOT / '.omx/artifacts/w3-model-reselection-20261110/independent-protocol.json').read_bytes())
    for c in protocol['cases']:
        (assets / 'inputs' / (c['id'] + '.pcm')).symlink_to(c['pcmPath'])
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
    annotations = jar('org.jetbrains', 'annotations', '26.1.0')
    jsonjar = jar('org.json', 'json', '20250517')
    compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'), stdlib, annotations,
                jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
                jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
                jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
    java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
    classes = output / 'classes'; classes.mkdir()
    sources = [core, activity, wrapper, Path(__file__).with_name('QwenFileInputs.kt'),
               Path(__file__).with_name('QwenFileReport.kt')]
    args = [str(java), '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-no-reflect', '-no-stdlib', '-classpath', ':'.join(map(str, [stdlib, annotations, jsonjar])),
            '-d', str(classes)] + list(map(str, sources + list(output.glob('*.kt'))))
    p = subprocess.run(['rtk', 'proxy'] + args, capture_output=True, text=True)
    (output / 'compile.log').write_text(p.stdout + p.stderr)
    receipt = {'sourceSHA256': {str(f): hashlib.sha256(f.read_bytes()).hexdigest() for f in sources},
               'compileExitCode': p.returncode, 'realNativeCalls': 0, 'deviceOperations': 0,
               'realCaseCore': True, 'hostAndroidAndNativeDoubles': True}
    if p.returncode == 0:
        p = subprocess.run(['rtk', 'proxy', str(java), '-cp', ':'.join(map(str, [classes, stdlib, jsonjar])),
                            'checks.ChecksKt', str(assets)], capture_output=True, text=True)
        (output / 'runtime.log').write_text(p.stdout + p.stderr)
        receipt.update(runtimeExitCode=p.returncode, output=p.stdout)
    (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt)); return p.returncode


if __name__ == '__main__':
    raise SystemExit(main())

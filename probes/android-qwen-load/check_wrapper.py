"""Parent wrapper acceptance with host Android/native doubles; no real model/device."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.omx/artifacts/w3-model-reselection-20261110/android-wrapper-host'
OUT.mkdir(exist_ok=True)
label, candidate = sys.argv[1], Path(sys.argv[2]).resolve()
assert label.replace('-', '').isalnum()
work = OUT / label
work.mkdir(exist_ok=False)
stubs = {
'Runtime.kt': '''package checks
import java.io.File
import java.util.concurrent.CountDownLatch
object Runtime {
 lateinit var root:File
 var writes=0;var failWriteAt=0;var creates=0;var releases=0;var verifies=0
 var locked=false;var verifyFail=false;var constructorFail=false;var releaseFail=false
 var teardownAck=true;var lockAfterVerify=false;var code:Int?=null
 var finished=CountDownLatch(1)
}
''',
'Bundle.kt': '''package android.os
class Bundle {
 private val v=mutableMapOf<String,String>()
 fun getString(k:String):String?=v[k]
 fun putString(k:String,value:String){v[k]=value}
}
class PowerManager {val isInteractive get()=true}
object Process {fun myPid()=1234}
''',
'Context.kt': '''package android.content
import checks.Runtime
import java.io.File
open class Context {
 val filesDir get()=File(Runtime.root,"files").also{it.mkdirs()}
 val noBackupFilesDir get()=File(Runtime.root,"no_backup").also{it.mkdirs()}
 fun getSystemService(name:String):Any=if(name==POWER_SERVICE)android.os.PowerManager() else android.app.KeyguardManager()
 companion object {const val POWER_SERVICE="power";const val KEYGUARD_SERVICE="keyguard"}
}
class Intent(c:Context,klass:Class<*>) {fun addFlags(flags:Int)=this
 companion object{const val FLAG_ACTIVITY_NEW_TASK=1}}
''',
'Activity.kt': '''package android.app
import checks.Runtime
import android.os.Bundle
import android.content.Context
import android.content.Intent
open class Activity:Context() {
 open fun onCreate(b:Bundle?){}
 open fun onResume(){}
 open fun onPause(){}
 open fun onDestroy(){}
 fun setContentView(v:Any){}
 fun finish(){if(Runtime.teardownAck){onPause();onDestroy()}}
 companion object{const val RESULT_OK=-1;const val RESULT_CANCELED=0}
}
class KeyguardManager {val isKeyguardLocked get()=Runtime.locked}
open class Instrumentation {
 val targetContext=Context()
 open fun onCreate(b:Bundle?){}
 open fun onStart(){}
 fun start(){onStart()}
 fun startActivitySync(i:Intent):Activity=io.ondeviceai.acceptance.qwenload.QwenLoadActivity().also{it.onCreate(null);it.onResume()}
 fun runOnMainSync(r:Runnable){r.run()}
 fun finish(code:Int,b:Bundle){Runtime.code=code;Runtime.finished.countDown()}
}
''',
'TextView.kt': '''package android.widget
class TextView(c:android.content.Context){var text:String="";var textSize:Float=0f;fun setPadding(a:Int,b:Int,c:Int,d:Int){}}
''',
'AtomicFile.kt': '''package android.util
import checks.Runtime
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
class AtomicFile(val baseFile:File){
 val pending=File(baseFile.path+".new")
 fun startWrite():FileOutputStream{Runtime.writes++;if(Runtime.writes==Runtime.failWriteAt)throw IOException("injected-write");return FileOutputStream(pending)}
 fun finishWrite(out:FileOutputStream){out.close();Files.move(pending.toPath(),baseFile.toPath(),StandardCopyOption.REPLACE_EXISTING)}
 fun failWrite(out:FileOutputStream){out.close();pending.delete()}
}
''',
'Native.kt': '''package com.k2fsa.sherpa.onnx
import checks.Runtime
import java.io.File
data class FeatureConfig(val sampleRate:Int=16000,val featureDim:Int=80)
data class OfflineQwen3AsrModelConfig(val convFrontend:String="",val encoder:String="",val decoder:String="",val tokenizer:String="",val maxTotalLen:Int=512,val maxNewTokens:Int=128,val temperature:Float=1e-6f,val topP:Float=.8f,val seed:Int=42,val hotwords:String="")
data class OfflineModelConfig(val qwen3Asr:OfflineQwen3AsrModelConfig=OfflineQwen3AsrModelConfig(),val numThreads:Int=1,val debug:Boolean=false,val provider:String="cpu")
data class OfflineRecognizerConfig(val featConfig:FeatureConfig=FeatureConfig(),val modelConfig:OfflineModelConfig=OfflineModelConfig(),val decodingMethod:String="greedy_search")
class OfflineRecognizer(a:Any?,c:OfflineRecognizerConfig){
 init{
  Runtime.creates++
  require(a==null && c.featConfig==FeatureConfig(16000,128))
  val m=c.modelConfig;val q=m.qwen3Asr
  require(m.numThreads==2 && !m.debug && m.provider=="cpu" && c.decodingMethod=="greedy_search")
  require(q.tokenizer.endsWith("/tokenizer")){"tokenizer must reference tokenizer child"}
  require(File(q.tokenizer).isDirectory && q.maxTotalLen==512 && q.maxNewTokens==128 && q.seed==42 && q.temperature==1e-6f && q.topP==.8f && q.hotwords=="")
  require(q.convFrontend.endsWith("/conv_frontend.onnx") && q.encoder.endsWith("/encoder.int8.onnx") && q.decoder.endsWith("/decoder.int8.onnx"))
  if(Runtime.constructorFail)throw UnsupportedOperationException("injected-constructor")
 }
 fun release(){Runtime.releases++;if(Runtime.releaseFail)throw IllegalArgumentException("injected-release")}
}
''',
'CoreDoubles.kt': '''package io.ondeviceai.acceptance.qwenload
import checks.Runtime
import java.io.File
data class ModelSpec(val relativePath:String,val bytes:Long,val sha256:String)
object QwenModelFiles {
 fun verify(root:File):List<ModelSpec>{Runtime.verifies++;if(Runtime.verifyFail)throw SecurityException("injected-model-invalid");require(root.name=="qwen3-asr-0.6b-int8-393f8a14");if(Runtime.lockAfterVerify)Runtime.locked=true;return listOf(ModelSpec("mock",1,"0".repeat(64)))}
}
''',
}
# Exact coordinator already independently compiled/tested; fake only model verification here.
core = (ROOT / 'probes/android-qwen-load/QwenLoadCore.kt').read_text()
stubs['Coordinator.kt'] = 'package io.ondeviceai.acceptance.qwenload\n' + core[core.index('fun interface LoadHandle'):]
stubs['Checks.kt'] = r'''package checks
import io.ondeviceai.acceptance.qwenload.QwenLoadInstrumentation
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
private var count=0
private val failures=mutableListOf<String>()
private fun expect(ok:Boolean,msg:String="assertion"){check(ok){msg};count++}
private fun run(name:String,f:()->Unit){try{f();println("PASS "+name)}catch(t:Throwable){failures.add(name+":"+t)}}
private fun reset(){
 Runtime.root=Files.createTempDirectory(File("/private/tmp").toPath(),"odai-qwen-wrapper-").toFile()
 Runtime.writes=0;Runtime.failWriteAt=0;Runtime.creates=0;Runtime.releases=0;Runtime.verifies=0
 Runtime.locked=false;Runtime.verifyFail=false;Runtime.constructorFail=false;Runtime.releaseFail=false
 Runtime.teardownAck=true;Runtime.lockAfterVerify=false;Runtime.code=null;Runtime.finished=java.util.concurrent.CountDownLatch(1)
 File(Runtime.root,"no_backup/qwen3-asr-0.6b-int8-393f8a14/tokenizer").mkdirs()
}
private fun report()=File(Runtime.root,"files/w3-qwen-load/report-check01.json")
private fun start(id:String="check01"){
 val b=Bundle();b.putString("runId",id);b.putString("sourceFingerprint","a".repeat(64));QwenLoadInstrumentation().onCreate(b)
 expect(Runtime.finished.await(7,TimeUnit.SECONDS),"instrumentation did not finish")
}
fun main(){
 run("success validates real fixed config and native lifecycle"){
  reset();try{start();val j=JSONObject(report().readText());expect(j.getString("status")=="completed",j.toString());expect(j.getBoolean("terminal"));expect(Runtime.creates==1 && Runtime.releases==1 && Runtime.verifies==1);expect(j.getJSONObject("foreground").getBoolean("teardownAck"));expect(j.getJSONObject("core").getBoolean("releaseSucceeded"));for(k in listOf("streamsCreated","nativeDecodeCalls","microphoneStarts","networkRequests"))expect(j.getInt(k)==0)}finally{Runtime.root.deleteRecursively()}
 }
 run("invalid run argument no report side effects"){
  reset();try{start("../unsafe");expect(Runtime.creates==0 && Runtime.writes==0 && !report().exists());expect(Runtime.code==0)}finally{Runtime.root.deleteRecursively()}
 }
 run("existing output not overwritten"){
  reset();try{report().parentFile.mkdirs();report().writeText("preserve");start();expect(report().readText()=="preserve");expect(Runtime.creates==0 && Runtime.writes==0)}finally{Runtime.root.deleteRecursively()}
 }
 run("report symlink rejected"){
  reset();try{report().parentFile.mkdirs();val other=File(Runtime.root,"other").also{it.writeText("preserve")};Files.createSymbolicLink(report().toPath(),other.toPath());start();expect(other.readText()=="preserve" && Runtime.creates==0)}finally{Runtime.root.deleteRecursively()}
 }
 run("initial write failure no native"){
  reset();try{Runtime.failWriteAt=1;start();expect(Runtime.creates==0 && Runtime.code==0)}finally{Runtime.root.deleteRecursively()}
 }
 run("locked no model validation or constructor"){
  reset();try{Runtime.locked=true;start();expect(Runtime.creates==0 && Runtime.verifies==0);expect(JSONObject(report().readText()).getString("status")=="failed")}finally{Runtime.root.deleteRecursively()}
 }
 run("model invalid no constructor"){
  reset();try{Runtime.verifyFail=true;start();expect(Runtime.creates==0 && Runtime.releases==0 && Runtime.verifies==1);val j=JSONObject(report().readText());expect(j.getString("status")=="failed");expect(j.getString("errorClass").contains("SecurityException"))}finally{Runtime.root.deleteRecursively()}
 }
 run("lock after verification blocks constructor"){
  reset();try{Runtime.lockAfterVerify=true;start();expect(Runtime.creates==0 && Runtime.releases==0 && Runtime.code==0)}finally{Runtime.root.deleteRecursively()}
 }
 run("constructor failure actual attempt no release"){
  reset();try{Runtime.constructorFail=true;start();expect(Runtime.creates==1 && Runtime.releases==0);val j=JSONObject(report().readText());expect(j.getString("status")=="failed");expect(j.getJSONObject("core").getInt("constructorAttempts")==1)}finally{Runtime.root.deleteRecursively()}
 }
 run("release failure retained and no false completion"){
  reset();try{Runtime.releaseFail=true;start();expect(Runtime.creates==1 && Runtime.releases==1);val j=JSONObject(report().readText());expect(j.getString("status")=="failed");expect(j.getJSONObject("core").getString("releaseErrorClass").contains("IllegalArgumentException"))}finally{Runtime.root.deleteRecursively()}
 }
 run("after construction write failure still releases"){
  reset();try{Runtime.failWriteAt=5;start();expect(Runtime.creates==1 && Runtime.releases==1);expect(Runtime.code==0 && JSONObject(report().readText()).getString("status")=="failed")}finally{Runtime.root.deleteRecursively()}
 }
 run("missing destroy acknowledgement fails full wrapper"){
  reset();try{Runtime.teardownAck=false;start();expect(Runtime.creates==1 && Runtime.releases==1);val j=JSONObject(report().readText());expect(j.getString("status")=="failed" && !j.getJSONObject("foreground").getBoolean("teardownAck"));expect(!j.isNull("teardownErrorClass"))}finally{Runtime.root.deleteRecursively()}
 }
 println("ASSERTIONS="+count+" FAILURES="+failures.size);failures.forEach{println("FAIL "+it)};check(failures.isEmpty())
}
'''
for name, text in stubs.items():
    (work / name).write_text(text)
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'


def jar(g, n, v):
    return next(f for f in (cache / g / n / v).glob('*/*.jar')
                if not f.name.endswith(('-sources.jar', '-all.jar', '-common.jar')))


stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
annotations = jar('org.jetbrains', 'annotations', '26.1.0')
jsonjar = jar('org.json', 'json', '20250517')
compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'), stdlib, annotations,
            jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
            jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
            jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
            jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
files = [candidate, Path(sys.argv[3]).resolve()]
classes = work / 'classes'
classes.mkdir()
args = [str(java), '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-classpath', ':'.join(map(str, [stdlib, annotations, jsonjar])),
        '-d', str(classes)] + list(map(str, files + list(work.glob('*.kt'))))
proc = subprocess.run(['rtk', 'proxy'] + args, capture_output=True, text=True)
(work / 'compile.log').write_text(proc.stdout + proc.stderr)
result = {'compileExitCode': proc.returncode, 'sources': {str(f): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
          'hostOnlyNativeDouble': True, 'realNativeCalls': 0, 'deviceOperations': 0,
          'modelVerificationDouble': True, 'coreVerifiedSeparately': True}
if proc.returncode == 0:
    proc = subprocess.run(['rtk', 'proxy', str(java), '-cp', ':'.join(map(str, [classes, stdlib, jsonjar])),
                           'checks.ChecksKt'], capture_output=True, text=True)
    (work / 'runtime.log').write_text(proc.stdout + proc.stderr)
    result.update(runtimeExitCode=proc.returncode, output=proc.stdout)
(work / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
raise SystemExit(proc.returncode)

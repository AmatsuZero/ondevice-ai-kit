"""Parent-owned real-file and fake-native acceptance; no Android/device operations."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.omx/artifacts/w3-model-reselection-20261110/android-load-host'
OUT.mkdir(exist_ok=True)
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def jar(group, name, version):
    return next(p for p in (CACHE / group / name / version).glob('*/*.jar')
                if not p.name.endswith(('-sources.jar', '-all.jar', '-common.jar')))


stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.20')
annotations = jar('org.jetbrains', 'annotations', '26.1.0')
compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.20'), stdlib, annotations,
            jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.0.21'),
            jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.20'),
            jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
            jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
java = ROOT / 'artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home/bin/java'
candidate = Path(sys.argv[1]).resolve()
label = sys.argv[2]
assert label.replace('-', '').isalnum()
checks = OUT / 'Checks.kt'
checks.write_text(r'''package io.ondeviceai.acceptance.qwenload
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

private var assertions = 0
private val failures = mutableListOf<String>()
private fun expect(ok: Boolean, msg: String = "assertion") { check(ok) { msg }; assertions++ }
private fun case(name: String, f: () -> Unit) {
    try { f(); println("PASS " + name) } catch (e: Throwable) { failures += name + ": " + e.toString() }
}
private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
private fun temp() = Files.createTempDirectory(File("/private/tmp").toPath(), "odai-qwen-core-check-").toFile().canonicalFile
private fun expectRejected(f: () -> Unit) { expect(runCatching { f() }.exceptionOrNull() != null) }

fun main() {
 case("success constructs once releases once and persists release evidence") {
  var creates=0; var releases=0; val seen=mutableListOf<LoadState>()
  val r=LoadCoordinator({ creates++; LoadHandle { releases++ } }, { true }, { seen.add(it) }).execute()
  expect(creates==1 && releases==1); expect(r.status=="completed")
  expect(r.constructorAttempts==1 && r.constructorReturned); expect(r.releaseAttempts==1 && r.releaseSucceeded)
  expect(r.loadNanos!=null && r.loadNanos!!>=0); expect(r.releaseNanos!=null && r.releaseNanos!!>=0)
  expect(seen.first().constructorAttempts==0); expect(seen.last().releaseSucceeded)
 }
 case("locked before run never constructs") {
  var creates=0
  val r=LoadCoordinator({creates++;LoadHandle{}},{false},{}).execute()
  expect(creates==0 && r.constructorAttempts==0 && r.releaseAttempts==0); expect(r.status=="error")
 }
 case("lock arrives after running persistence never constructs") {
  var gateCalls=0; var creates=0
  val r=LoadCoordinator({creates++;LoadHandle{}},{++gateCalls==1},{}).execute()
  expect(creates==0 && r.constructorAttempts==0);expect(r.status=="error")
 }
 case("gate exception before construction is contained") {
  var creates=0
  val r=LoadCoordinator({creates++;LoadHandle{}},{throw IllegalStateException("gate")},{}).execute()
  expect(creates==0);expect(r.status=="error" && r.errorClass?.contains("IllegalStateException")==true)
 }
 case("running persistence failure blocks native") {
  var creates=0
  val r=LoadCoordinator({creates++;LoadHandle{}},{true},{throw IOException("disk")}).execute()
  expect(creates==0 && r.constructorAttempts==0);expect(r.status=="error")
  expect(r.persistErrorClass?.contains("IOException")==true)
 }
 case("constructor failure counts actual dispatch no release") {
  var creates=0
  val r=LoadCoordinator({creates++;throw UnsupportedOperationException("constructor")},{true},{}).execute()
  expect(creates==1 && r.constructorAttempts==1 && !r.constructorReturned)
  expect(r.releaseAttempts==0 && !r.releaseSucceeded);expect(r.status=="error")
  expect(r.errorClass?.contains("UnsupportedOperationException")==true)
 }
 case("after load persistence failure still releases") {
  var releases=0;var injected=false
  val r=LoadCoordinator({LoadHandle{releases++}},{true},{if(it.constructorReturned && it.releaseAttempts==0 && !injected){injected=true;throw IOException("after load")}}).execute()
  expect(injected && releases==1);expect(r.releaseAttempts==1 && r.releaseSucceeded)
  expect(r.status=="error" && r.persistErrorClass?.contains("IOException")==true)
 }
 case("release failure cannot claim success") {
  var releases=0
  val r=LoadCoordinator({LoadHandle{releases++;throw IllegalArgumentException("release")}},{true},{}).execute()
  expect(releases==1 && r.releaseAttempts==1 && !r.releaseSucceeded);expect(r.status=="error")
  expect(r.releaseErrorClass?.contains("IllegalArgumentException")==true)
 }
 case("foreground loss after constructor still releases") {
  var created=false;var releases=0
  val r=LoadCoordinator({created=true;LoadHandle{releases++}},{!created},{}).execute()
  expect(releases==1 && r.releaseSucceeded);expect(r.status=="error")
 }
 case("primary persistence error and release error both survive") {
  var injected=false
  val r=LoadCoordinator({LoadHandle{throw IllegalArgumentException("release")}},{true},{if(it.constructorReturned && it.releaseAttempts==0 && !injected){injected=true;throw IOException("primary")}}).execute()
  expect(r.status=="error");expect(r.errorClass?.contains("IOException")==true)
  expect(r.persistErrorClass?.contains("IOException")==true && r.releaseErrorClass?.contains("IllegalArgumentException")==true)
 }
 case("terminal core write failure is an error after release") {
  var releases=0;var injected=false
  val r=LoadCoordinator({LoadHandle{releases++}},{true},{if(it.releaseSucceeded){injected=true;throw IOException("final")}}).execute()
  expect(injected && releases==1 && r.releaseSucceeded);expect(r.status=="error")
  expect(r.persistErrorClass?.contains("IOException")==true)
 }
 case("clock failure after acquired handle still releases") {
  var created=false;var released=false;var releases=0
  val r=LoadCoordinator({created=true;LoadHandle{releases++;released=true}},{true},{},
      {if(created && !released)throw IllegalStateException("clock");System.nanoTime()}).execute()
  expect(releases==1 && r.constructorReturned && r.releaseSucceeded, r.toString())
  expect(r.constructorAttempts==1 && r.releaseAttempts==1 && r.status=="error", r.toString())
  expect(r.errorClass?.contains("IllegalStateException")==true, r.toString())
 }
 case("clock failure after release cannot hide successful return") {
  var released=false;var releases=0
  val r=LoadCoordinator({LoadHandle{releases++;released=true}},{true},{},
      {if(released)throw IllegalStateException("clock");System.nanoTime()}).execute()
  expect(releases==1 && r.releaseSucceeded && r.releaseAttempts==1, r.toString())
  expect(r.status=="error" && r.errorClass?.contains("IllegalStateException")==true, r.toString())
 }
 case("constructor error survives failing duration observation") {
  var thrown=false
  val r=LoadCoordinator({thrown=true;throw UnsupportedOperationException("primary constructor")},{true},{},
      {if(thrown)throw IllegalStateException("clock");System.nanoTime()}).execute()
  expect(r.constructorAttempts==1 && !r.constructorReturned && r.releaseAttempts==0)
  expect(r.errorClass?.contains("UnsupportedOperationException")==true && r.errorMessage=="primary constructor")
 }
 case("model checks use real disk bytes and refuse corruption") {
  val dir=temp()
  try {
   val data=ByteArray(1048579){(it%127).toByte()};File(dir,"model.onnx").writeBytes(data)
   val specs=listOf(ModelSpec("model.onnx",data.size.toLong(),hash(data)))
   expect(QwenModelFiles.verify(dir,specs)==specs)
   File(dir,"model.onnx").writeBytes(data.clone().also{it[1048578]=1})
   expectRejected{QwenModelFiles.verify(dir,specs)}
   File(dir,"model.onnx").writeBytes(data.copyOf(9));expectRejected{QwenModelFiles.verify(dir,specs)}
   File(dir,"model.onnx").delete();expectRejected{QwenModelFiles.verify(dir,specs)}
   File(dir,"model.onnx").mkdir();expectRejected{QwenModelFiles.verify(dir,specs)}
  } finally {dir.deleteRecursively()}
 }
 case("bad relative paths hashes sizes duplicates and empty specs rejected") {
  val dir=temp()
  try {
   val data=byteArrayOf(1,2,3);File(dir,"safe").writeBytes(data);val spec=ModelSpec("safe",3,hash(data))
   for(path in listOf("/safe","../safe","./safe","a/../safe","a//safe","safe/",""))
    expectRejected{QwenModelFiles.verify(dir,listOf(spec.copy(relativePath=path)))}
   expectRejected{QwenModelFiles.verify(dir,listOf(spec,spec))};expectRejected{QwenModelFiles.verify(dir,emptyList())}
   expectRejected{QwenModelFiles.verify(dir,listOf(spec.copy(bytes=0)))}
   expectRejected{QwenModelFiles.verify(dir,listOf(spec.copy(sha256="0")))}
   expectRejected{QwenModelFiles.verify(dir,listOf(spec.copy(sha256=hash(data).uppercase())))}
  } finally {dir.deleteRecursively()}
 }
 case("root ancestor directory and file symlinks rejected before resolving") {
  val outer=temp();val actual=File(outer,"actual").also{it.mkdir()};val data=byteArrayOf(1,2,3)
  val spec=ModelSpec("safe",3,hash(data));File(actual,"safe").writeBytes(data)
  try {
   val link=File(outer,"link");Files.createSymbolicLink(link.toPath(),actual.toPath())
   expectRejected{QwenModelFiles.verify(link,listOf(spec))}
   val child=File(actual,"child").also{it.mkdir()};File(child,"safe").writeBytes(data)
   expectRejected{QwenModelFiles.verify(File(link,"child"),listOf(spec))}
   val fileLink=File(actual,"fileLink");Files.createSymbolicLink(fileLink.toPath(),File(actual,"safe").toPath())
   expectRejected{QwenModelFiles.verify(actual,listOf(spec.copy(relativePath="fileLink")))}
   val dirLink=File(actual,"dirLink");Files.createSymbolicLink(dirLink.toPath(),child.toPath())
   expectRejected{QwenModelFiles.verify(actual,listOf(spec.copy(relativePath="dirLink/safe")))}
  } finally {outer.deleteRecursively()}
 }
 println("ASSERTIONS="+assertions+" FAILURES="+failures.size)
 failures.forEach{println("FAIL "+it)}
 check(failures.isEmpty())
}
''')
classes = OUT / (label + '-classes')
classes.mkdir(exist_ok=False)
argv = [str(java), '-cp', ':'.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-reflect', '-no-stdlib', '-classpath', str(stdlib) + ':' + str(annotations),
        '-d', str(classes), str(candidate), str(checks)]
proc = subprocess.run(['rtk', 'proxy'] + argv, capture_output=True, text=True)
(OUT / (label + '-compile.log')).write_text(proc.stdout + proc.stderr)
result = {'sourceSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(), 'compileExitCode': proc.returncode,
          'realNativeCalls': 0, 'deviceOperations': 0}
if proc.returncode == 0:
    proc = subprocess.run(['rtk', 'proxy', str(java), '-cp', str(classes) + ':' + str(stdlib),
                           'io.ondeviceai.acceptance.qwenload.ChecksKt'], capture_output=True, text=True)
    (OUT / (label + '-runtime.log')).write_text(proc.stdout + proc.stderr)
    result.update(runtimeExitCode=proc.returncode, output=proc.stdout)
(OUT / (label + '-result.json')).write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
raise SystemExit(proc.returncode)

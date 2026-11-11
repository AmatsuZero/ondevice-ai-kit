package io.ondeviceai.acceptance.qwenfile

import java.io.File

private var assertions=0
private val failures=mutableListOf<String>()
private fun expect(ok:Boolean,msg:String="assertion"){check(ok){msg};assertions++}
private fun case(name:String,body:()->Unit){try{body();println("PASS $name")}catch(t:Throwable){failures+="$name:$t"}}
private fun rejected(body:()->Unit){expect(runCatching{body()}.exceptionOrNull()!=null)}

fun main(args:Array<String>){
 val root=File(args.single());val owned=mutableListOf<ByteArray>();var reads=0
 val read:(String)->ByteArray={name -> reads++;File(root,name).readBytes().also{owned+=it}}
 var cases:List<InputCase> = emptyList()
 case("all65 real fixture bytes hashes references and original IDs accepted"){
  cases=QwenFileInputs.verifyAll(read)
  expect(cases.size==65 && cases.map{it.id}.toSet().size==65)
  expect(cases[0].id=="fleurs-cmn_hans_cn-1805")
  expect(cases.any{it.id=="ami-ES2003a.sync.171-C"})
  expect(cases[0].reference=="Scotturb 403 路公共汽车定期发车前往辛特拉 (Sintra)，在罗卡角停靠。")
  expect(reads==66 && owned.all{a->a.all{it==0.toByte()}},"temporary bytes must clear")
 }
 case("fresh read gives full bit-exact original samples and clears owned bytes"){
  expect(cases.size==65)
  for(c in listOf(cases.first(),cases[40],cases.last())){
   val pcm=File(root,c.asset).readBytes();val samples=QwenFileInputs.readSamples(c,read)
   expect(samples.size==c.samples)
   for(i in samples.indices){
    val short=((pcm[2*i].toInt() and 255) or (pcm[2*i+1].toInt() shl 8)).toShort()
    expect(samples[i].toRawBits()==(short.toFloat()/32768f).toRawBits(),"sample mismatch $i")
   }
   expect(owned.last().all{it==0.toByte()});samples.fill(0f)
  }
 }
 case("manifest corruption blocks all PCM reads"){
  var count=0
  rejected{QwenFileInputs.verifyAll{name->count++;File(root,name).readBytes().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}}}
  expect(count==1)
 }
 case("PCM corruption before native rejects and clears rejected buffer"){
  var bad:ByteArray?=null;var count=0
  rejected{QwenFileInputs.verifyAll{name->count++;File(root,name).readBytes().also{if(name!="cases.json"){it[0]=(it[0].toInt() xor 1).toByte();bad=it}}}}
  expect(count==2 && bad!=null && bad!!.all{it==0.toByte()})
 }
 case("later fresh read corruption is rejected not converted"){
  var bad:ByteArray?=null
  rejected{QwenFileInputs.readSamples(cases.first()){name->File(root,name).readBytes().also{it[0]=(it[0].toInt() xor 1).toByte();bad=it}}}
  expect(bad!!.all{it==0.toByte()})
 }
 case("short odd or empty PCM rejected and cleared"){
  for(b in listOf(byteArrayOf(),byteArrayOf(1),byteArrayOf(1,2))){
   rejected{QwenFileInputs.readSamples(cases.first()){b}}
   expect(b.all{it==0.toByte()})
  }
 }
 case("unsafe metadata is rejected before asset provider is invoked"){
  val c=cases.first()
  for(bad in listOf(c.copy(id="../bad",asset="inputs/../bad.pcm"),c.copy(asset="../escape.pcm"),
   c.copy(locale="en"),c.copy(samples=0),c.copy(samples=480001),c.copy(sha256="no"))){
   var count=0;rejected{QwenFileInputs.readSamples(bad){count++;ByteArray(0)}};expect(count==0)
  }
 }
 case("asset provider failure preserved without fabricated input"){
  val e=runCatching{QwenFileInputs.verifyAll{throw IllegalStateException("asset unavailable")}}.exceptionOrNull()
  expect(e is IllegalStateException && e.message=="asset unavailable")
 }
 println("ASSERTIONS=$assertions FAILURES=${failures.size}");failures.forEach{println("FAIL $it")};check(failures.isEmpty())
}

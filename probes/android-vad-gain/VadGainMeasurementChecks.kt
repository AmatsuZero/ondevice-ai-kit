package io.ondeviceai.sherpaasr
private var checks=0
private val failures=mutableListOf<String>()
private fun expect(b:Boolean){check(b);checks++}
private fun case(name:String,f:()->Unit){try{f()}catch(t:Throwable){failures.add(name+":"+t.javaClass.name+":"+t.message)}}
private class Fake(val probability:(FloatArray,Int)->Float,val closeFailure:Boolean=false):Detector {
 var calls=0;var closes=0;val seen=mutableListOf<FloatArray>()
 override fun compute(window:FloatArray):Float{calls++;seen.add(window);return probability(window,calls)}
 override fun release(){closes++;if(closeFailure)throw UnsupportedOperationException("release failure")}
}
fun main(){
 case("successful input and real crop"){
  val input=FloatArray(64000){.005f};val original=input.copyOf();val f=Fake({_,n->if(n in 5..20).8f else .1f})
  val o=VadGainMeasurement.measure(input,1f){f}
  expect(o.detected);expect(o.processedCount==26240);expect(o.preparedSamples==13440)
  expect(o.classifierCalls==51 && f.calls==51);expect(o.speechSamples==8192);expect(f.closes==1);expect(input.contentEquals(original))
 }
 case("seven speech windows remain no speech"){
  val f=Fake({_,_->.8f});val o=VadGainMeasurement.measure(FloatArray(7*512){.005f},1f){f}
  expect(!o.detected);expect(o.preparedSamples==0);expect(o.speechSamples==3584);expect(f.closes==1)
 }
 case("eight speech windows accepted and tail preserved"){
  val f=Fake({_,_->.8f});val o=VadGainMeasurement.measure(FloatArray(8*512+17){.005f},1f){f}
  expect(o.detected);expect(o.preparedSamples==4113);expect(o.classifierCalls==8);expect(f.closes==1)
 }
 case("gain applied once and captured temporary windows cleared"){
  val input=FloatArray(4096){.005f};val f=Fake({w,_->expect(w.all{it==.05f || kotlin.math.abs(it-.05f)<1e-7});.8f})
  val o=VadGainMeasurement.measure(input,10f){f}
  expect(o.detected);expect(input.all{it==.005f});expect(f.seen.all{w->w.all{it==0f}});expect(f.closes==1)
 }
 case("silence stays negative"){
  val f=Fake({_,_->.1f});val o=VadGainMeasurement.measure(FloatArray(10000),10f){f}
  expect(!o.detected);expect(o.preparedSamples==0);expect(o.classifierCalls==19);expect(f.closes==1)
 }
 case("compute failure releases and clears"){
  val f=Fake({_,_->throw UnsupportedOperationException("compute failure")});val input=FloatArray(4096){.005f}
  val e=runCatching{VadGainMeasurement.measure(input,1f){f}}.exceptionOrNull()
  expect(e is UnsupportedOperationException);expect(f.closes==1);expect(input.all{it==.005f});expect(f.seen.all{w->w.all{it==0f}})
 }
 case("release failure never success"){
  val f=Fake({_,_->.8f},true);expect(runCatching{VadGainMeasurement.measure(FloatArray(4096){.005f},1f){f}}.exceptionOrNull() is UnsupportedOperationException);expect(f.closes==1)
 }
 case("invalid probabilities fail and release"){
  for(p in listOf(Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.01f)){
   val f=Fake({_,_->p});expect(runCatching{VadGainMeasurement.measure(FloatArray(4096){.005f},1f){f}}.exceptionOrNull() is IllegalArgumentException);expect(f.closes==1)
  }
 }
 case("invalid input rejected before native"){
  var creates=0
  for(input in listOf(FloatArray(0),FloatArray(160001),floatArrayOf(Float.NaN),floatArrayOf(Float.POSITIVE_INFINITY),floatArrayOf(.1f),floatArrayOf(-.1f))){expect(runCatching{VadGainMeasurement.measure(input,10f){creates++;Fake({_,_->.8f})}}.exceptionOrNull() is IllegalArgumentException)}
  expect(runCatching{VadGainMeasurement.measure(floatArrayOf(.01f),2f){creates++;Fake({_,_->.8f})}}.exceptionOrNull() is IllegalArgumentException);expect(creates==0)
 }
 case("source mutation is detected before clearing its snapshot"){
  val input=FloatArray(4096){.005f};val f=Fake({_,_->input[0]=.006f;.8f})
  expect(runCatching{VadGainMeasurement.measure(input,1f){f}}.exceptionOrNull() is IllegalArgumentException);expect(f.closes==1)
 }
 println("CHECKS="+checks+" FAILURES="+failures.size);failures.forEach{println("FAIL "+it)};check(failures.isEmpty())
}

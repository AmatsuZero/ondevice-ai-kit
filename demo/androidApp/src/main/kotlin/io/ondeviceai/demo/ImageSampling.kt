package io.ondeviceai.demo
internal fun imageSampleSize(width:Int,height:Int):Int {
 require(width>0 && height>0) { "invalid image dimensions" }
 var sample=1
 while(maxOf(width,height)/sample>2048) sample*=2
 return sample
}

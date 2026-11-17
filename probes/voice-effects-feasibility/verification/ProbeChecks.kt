import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

fun main(args: Array<String>) {
    val out=File(args[0]); out.mkdirs()
    var rejected=0
    val bad=listOf<()->Unit>(
        { PitchCore.shift(floatArrayOf(),16000,4) },
        { PitchCore.shift(floatArrayOf(Float.NaN),16000,4) },
        { PitchCore.shift(floatArrayOf(Float.POSITIVE_INFINITY),16000,4) },
        { PitchCore.shift(floatArrayOf(1.1f),16000,4) },
        { PitchCore.shift(floatArrayOf(0f),8000,4) },
        { PitchCore.shift(floatArrayOf(0f),16000,3) },
        { PitchCore.shift(FloatArray(16000*30+1),16000,4) }
    )
    for (f in bad) { try { f();error("invalid input accepted") } catch(e: IllegalArgumentException) { rejected++ } }
    for (rate in listOf(16000,44100,48000)) {
        for (freq in listOf(220,440,880)) {
            val input=FloatArray(rate*2) { (0.5*sin(2*PI*freq*it/rate)).toFloat() }
            val before=input.copyOf()
            for (semi in listOf(0,4,-4)) {
                val start=System.nanoTime(); val result=PitchCore.shift(input,rate,semi)
                check(result.size==input.size && result!==input && input.contentEquals(before))
                check(result.all { it.isFinite() && abs(it)<=1.001f })
                if(semi==0)check(result.contentEquals(input))
                val buf=ByteBuffer.allocate(result.size*4).order(ByteOrder.LITTLE_ENDIAN)
                result.forEach { buf.putFloat(it) }
                File(out,"${rate}_${freq}_${semi}.f32").writeBytes(buf.array())
                println("render rate=$rate frequency=$freq semitones=$semi frames=${result.size} ms=${(System.nanoTime()-start)/1000000.0}")
            }
        }
        for (semi in listOf(0,4,-4)) {
            check(PitchCore.shift(FloatArray(rate),rate,semi).all { it==0f })
            check(PitchCore.shift(floatArrayOf(0.5f),rate,semi).size==1)
            val full=PitchCore.shift(FloatArray(rate*30),rate,semi)
            check(full.size==rate*30 && full.all { it==0f })
        }
    }
    println("CHECKS_OK invalidRejected=$rejected renders=27 plus silence/one-frame/max-length")
}

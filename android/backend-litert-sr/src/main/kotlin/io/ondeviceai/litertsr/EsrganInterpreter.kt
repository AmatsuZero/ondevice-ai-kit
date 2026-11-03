package io.ondeviceai.litertsr

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/** Request-local CPU interpreter. It is only closed after the blocking invocation returns. */
internal class EsrganInterpreter(model: File) : AutoCloseable {
    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
    private val input = ByteBuffer.allocateDirect(50 * 50 * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(200 * 200 * 3 * 4).order(ByteOrder.nativeOrder())
    init {
        try {
            interpreter.allocateTensors()
            check(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1)
            check(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 50, 50, 3)))
            check(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 200, 200, 3)))
            check(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32 && interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
        } catch (error: Throwable) { interpreter.close(); throw error }
    }
    fun infer(pixels: IntArray): IntArray {
        require(pixels.size == 2500)
        input.clear(); output.clear()
        pixels.forEach { pixel ->
            input.putFloat(((pixel shr 16) and 255).toFloat())
            input.putFloat(((pixel shr 8) and 255).toFloat())
            input.putFloat((pixel and 255).toFloat())
        }
        input.rewind(); interpreter.run(input, output); output.rewind()
        fun channel(): Int { val value = output.float; check(value.isFinite()); return value.coerceIn(0f, 255f).toInt() }
        return IntArray(200 * 200) { (255 shl 24) or (channel() shl 16) or (channel() shl 8) or channel() }
    }
    override fun close() = interpreter.close()
}

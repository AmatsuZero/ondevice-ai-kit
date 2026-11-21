import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object WaveProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 3) throw IllegalArgumentException("Usage: input.wav output.wav semitones")
        val input = File(args[0])
        val output = File(args[1])
        val semitones = args[2].toInt()
        convert(input, output, semitones)
    }

    fun convert(input: File, output: File, semitones: Int) {
        if (!input.isFile) throw IllegalArgumentException("Input is not a file")
        val length = input.length()
        if (length > 2880044) throw IllegalArgumentException("Input file too large")
        if (output.exists()) throw IllegalArgumentException("Output file already exists")
        if (input.canonicalFile == output.canonicalFile) throw IllegalArgumentException("Input and output are the same file")

        val bytes = input.readBytes()
        if (bytes.size < 46) throw IllegalArgumentException("Input file too small")

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (String(bytes, 0, 4) != "RIFF") throw IllegalArgumentException("Not a RIFF file")
        if (String(bytes, 8, 4) != "WAVE") throw IllegalArgumentException("Not a WAVE file")
        if (String(bytes, 12, 4) != "fmt ") throw IllegalArgumentException("Missing fmt chunk")
        if (String(bytes, 36, 4) != "data") throw IllegalArgumentException("Missing data chunk")

        val riffSize = bb.getInt(4)
        val fmtSize = bb.getInt(16)
        val format = bb.getShort(20).toInt() and 0xffff
        val channels = bb.getShort(22).toInt() and 0xffff
        val sampleRate = bb.getInt(24)
        val byteRate = bb.getInt(28)
        val blockAlign = bb.getShort(32).toInt() and 0xffff
        val bitsPerSample = bb.getShort(34).toInt() and 0xffff
        val dataSize = bb.getInt(40)
        if (blockAlign != 2) throw IllegalArgumentException("Invalid block align")
        bb.position(44)

        if (riffSize != bytes.size - 8) throw IllegalArgumentException("RIFF size mismatch")
        if (fmtSize != 16) throw IllegalArgumentException("Invalid fmt chunk size")
        if (format != 1) throw IllegalArgumentException("Not PCM format")
        if (channels != 1) throw IllegalArgumentException("Only mono supported")
        if (bitsPerSample != 16) throw IllegalArgumentException("Only 16-bit samples supported")
        if (sampleRate !in listOf(16000, 44100, 48000)) throw IllegalArgumentException("Unsupported sample rate")
        if (byteRate != sampleRate * 2) throw IllegalArgumentException("Invalid byte rate")
        if (dataSize != bytes.size - 44) throw IllegalArgumentException("Data size mismatch")
        if (dataSize <= 0 || dataSize % 2 != 0 || dataSize > sampleRate * 30 * 2) throw IllegalArgumentException("Invalid data size")

        if (semitones !in listOf(0, 4, -4)) throw IllegalArgumentException("Only semitones 0, 4, -4 supported")

        val samples = FloatArray(dataSize / 2)
        for (i in samples.indices) {
            samples[i] = bb.getShort().toFloat() / 32768f
        }

        val shifted = PitchCore.shift(samples, sampleRate, semitones)

        val tempOutput = File.createTempFile("w9-probe-", ".wav", output.absoluteFile.parentFile)
        val outBytes = ByteArray(44 + shifted.size * 2)
        val outBb = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Copy header
        System.arraycopy(bytes, 0, outBytes, 0, 44)
        outBb.position(40)
        outBb.putInt(shifted.size * 2)

        // Write shifted samples
        for (sample in shifted) {
            val shortSample = sample.times(32768).roundToInt().coerceIn(-32768, 32767).toShort()
            outBb.putShort(shortSample)
        }

        try {
            tempOutput.writeBytes(outBytes)
            if (output.exists()) throw IllegalStateException("Output file created during processing")
            val success = tempOutput.renameTo(output)
            if (!success) throw IllegalStateException("Failed to rename temp file")
        } finally {
            tempOutput.delete()
        }
    }
}

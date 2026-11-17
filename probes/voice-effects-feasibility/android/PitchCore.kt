object PitchCore {
    private const val WINDOW_MS = 80
    private const val PI = Math.PI

    fun shift(input: FloatArray, sampleRate: Int, semitones: Int): FloatArray {
        require(sampleRate in intArrayOf(16000, 44100, 48000)) { "Unsupported sample rate" }
        require(semitones in intArrayOf(0, 4, -4)) { "Unsupported semitones" }
        require(input.isNotEmpty() && input.size <= sampleRate * 30) { "Invalid input length" }
        require(input.all { it.isFinite() && it >= -1f && it <= 1f }) { "Invalid input values" }

        if (semitones == 0) return input.copyOf()

        val W = sampleRate * 0.080
        val ratio = Math.pow(2.0, semitones / 12.0)
        val step = (1.0 - ratio) / W
        val output = FloatArray(input.size)

        for (n in input.indices) {
            val pos = n * step
            val p = pos - Math.floor(pos)
            val q = (p + 0.5) - Math.floor(p + 0.5)

            val weightP = 0.5 - 0.5 * Math.cos(2.0 * PI * p)
            val weightQ = 0.5 - 0.5 * Math.cos(2.0 * PI * q)

            val posP = n + W * (0.5 - p)
            val posQ = n + W * (0.5 - q)

            val readP = read(posP, input)
            val readQ = read(posQ, input)

            output[n] = (weightP * readP + weightQ * readQ).toFloat()

        }

        return output
    }

    private fun read(pos: Double, input: FloatArray): Double {
        val base = Math.floor(pos).toInt()
        val fraction = pos - base
        val a = input.getOrNull(base)?.toDouble() ?: 0.0
        val b = input.getOrNull(base + 1)?.toDouble() ?: 0.0
        return (1.0 - fraction) * a + fraction * b
    }
}
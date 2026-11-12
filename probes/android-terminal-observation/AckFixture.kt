package io.ondeviceai.acceptance.terminal

/** Host acceptance fixture: emit actual Kotlin ACK bytes for cross-language comparison. */
fun main(args: Array<String>) {
    require(args.size == 5)
    val binding = TerminalObservationBinding(args[0], args[1], args[2].toInt(), args[3], args[4])
    System.out.write(binding.ackBytes())
}

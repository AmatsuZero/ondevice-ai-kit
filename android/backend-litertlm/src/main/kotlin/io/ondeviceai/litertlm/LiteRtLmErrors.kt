package io.ondeviceai.litertlm

sealed class LiteRtLmError(message: String) : IllegalStateException(message) {
    class InvalidModel : LiteRtLmError("invalid LiteRT-LM model")
    class RuntimeFailure : LiteRtLmError("LiteRT-LM runtime failed")
    class NoResult : LiteRtLmError("LiteRT-LM produced no text")
    class DrainFailure : LiteRtLmError("LiteRT-LM native drain failed")
    class RuntimeUnavailable : LiteRtLmError("LiteRT-LM runtime unavailable")
}

package io.ondeviceai.opencvidentity

import io.ondeviceai.modelstore.ModelIntegrityException
import java.io.IOException

enum class OpenCvIdentityStage { MODEL_IMPORT, MODEL_STORE, MODEL_BINDING, RUNTIME_LOAD, DETECTOR_LOAD, RECOGNIZER_LOAD, IMAGE_DECODE, DETECTION, ALIGNMENT, EMBEDDING, SESSION }
enum class OpenCvIdentityFailureKind { MODEL_INTEGRITY, IO, RUNTIME_LINKAGE, NATIVE, INVALID_INPUT, UNEXPECTED }

/** Contains no exception message, path, image, vector or authorization value. */
data class OpenCvIdentityDiagnostic(val stage: OpenCvIdentityStage, val kind: OpenCvIdentityFailureKind) {
    companion object {
        fun from(stage: OpenCvIdentityStage, error: Throwable) = OpenCvIdentityDiagnostic(stage, when {
            error is ModelIntegrityException -> OpenCvIdentityFailureKind.MODEL_INTEGRITY
            error is LinkageError -> OpenCvIdentityFailureKind.RUNTIME_LINKAGE
            error is IOException -> OpenCvIdentityFailureKind.IO
            error.javaClass.name == "org.opencv.core.CvException" -> OpenCvIdentityFailureKind.NATIVE
            error is IllegalArgumentException -> OpenCvIdentityFailureKind.INVALID_INPUT
            else -> OpenCvIdentityFailureKind.UNEXPECTED
        })
    }
}

internal fun reportIdentityDiagnostic(callback: (OpenCvIdentityDiagnostic) -> Unit,
    stage: OpenCvIdentityStage, error: Throwable) {
    try { callback(OpenCvIdentityDiagnostic.from(stage, error)) } catch (_: Exception) { }
}

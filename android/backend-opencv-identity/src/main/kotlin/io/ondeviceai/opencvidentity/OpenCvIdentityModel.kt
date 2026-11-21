package io.ondeviceai.opencvidentity

import io.ondeviceai.identity.*
import io.ondeviceai.modelstore.*
import kotlin.math.sqrt

/** Pinned experimental assets; recognition/PAD policies are deliberately unverified. */
object OpenCvIdentityModel {
    const val BACKEND_ID = "opencv.identity.android"
    const val VERSION = "4.12.0"
    const val UPSTREAM = "47534e27c9851bb1128ccc0102f1145e27f23f98"
    const val PREPROCESS = "opencv-4.12-yunet640-sface112-rgb-l2-v1"
    const val DETECTOR = "face_detection_yunet_2023mar.onnx"
    const val RECOGNIZER = "face_recognition_sface_2021dec.onnx"
    val ref = ModelRef("opencv-yunet-sface", "zoo-47534e27-v1")
    val embeddingSpace = EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION, IdentityCapability.IDENTIFICATION),
        BACKEND_ID, VERSION, ModelBinding(ModelBindingKind.MODEL, ref.modelId, ref.revision),
        "1", 128, FeatureDType.FLOAT32, Endianness.LITTLE, "l2", PREPROCESS, "cosine", ScoreDirection.HIGHER_IS_BETTER)

    fun manifest(): ModelManifest {
        val upstream = "https://github.com/opencv/opencv_zoo"
        fun asset(folder: String, name: String, bytes: Long, hash: String) = ModelFile(name,
            "https://media.githubusercontent.com/media/opencv/opencv_zoo/$UPSTREAM/models/$folder/$name", bytes, hash)
        return ModelManifest(modelId = ref.modelId, revision = ref.revision,
            upstreamURL = upstream, upstreamRevision = UPSTREAM, capability = "face-identity", backendId = BACKEND_ID,
            backendVersion = VersionRange(VERSION, "4.13.0"), minimumOS = "26", architectures = listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86"),
            formatVersion = "onnx-yunet-sface-v1", files = listOf(
                asset("face_detection_yunet", DETECTOR, 232_589, "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"),
                asset("face_recognition_sface", RECOGNIZER, 38_696_353, "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79")),
            processing = ModelProcessing("upright-encoded-image-bgr-yunet-long-edge-640", "float32-128-l2", PREPROCESS, null, null, 640 * 640 * 3),
            resources = ModelResources(38_928_942, 77_857_884, 38_928_942),
            licenseIdentifier = "MIT AND Apache-2.0", licenseURL = "$upstream/tree/$UPSTREAM/models",
            attribution = "OpenCV Zoo YuNet (MIT) and SFace (Apache-2.0); retain both upstream licenses.",
            redistribution = "Local prototype approved; production use, training-data provenance and redistribution review remain open.")
    }

    fun unverifiedPolicy(kind: IdentityPolicyKind) = ThresholdPolicy("opencv-prototype-${kind.name.lowercase()}", "unverified-v1",
        embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.UNVERIFIED, kind)
}

internal object SFaceFeatures {
    fun normalized(raw: FloatArray): List<Float> {
        if (raw.size != 128 || raw.any { !it.isFinite() }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val norm = sqrt(raw.sumOf { it.toDouble() * it })
        if (!norm.isFinite() || norm == 0.0) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return raw.map { (it.toDouble() / norm).toFloat() }
    }
}

/** Binary geometric admissibility only; neither detector confidence nor recognition quality. */
internal object FaceGeometry {
    fun usable(face: FloatArray, width: Int, height: Int): Boolean {
        if (face.size != 15 || face.any { !it.isFinite() } || face[2] <= 0 || face[3] <= 0) return false
        for (index in 4..12 step 2) {
            if (face[index] !in 0f..(width - 1).toFloat() || face[index + 1] !in 0f..(height - 1).toFloat()) return false
        }
        fun distanceSquared(a: Int, b: Int): Float = (face[a] - face[b]).let { it * it } + (face[a + 1] - face[b + 1]).let { it * it }
        return distanceSquared(4, 6) >= 1 && distanceSquared(10, 12) >= 1
    }
}

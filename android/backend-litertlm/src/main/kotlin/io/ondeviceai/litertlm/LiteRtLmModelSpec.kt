package io.ondeviceai.litertlm

import io.ondeviceai.modelstore.ModelManifest
import java.io.File

/** Explicit execution selection; generation validates the selected model backends. */
enum class LiteRtLmBackendPolicy { CPU_ONLY, CPU_TEXT_GPU_VISION }

internal data class LiteRtLmEngineConfig(
    val backendPolicy: LiteRtLmBackendPolicy = LiteRtLmBackendPolicy.CPU_ONLY,
    val maxNumTokens: Int = 512,
    val maxNumImages: Int = 1,
    val cacheDir: File,
)

internal data class LiteRtLmModelSpec(
    val modelPathRelative: String,
    val metadataPathsRelative: Set<String>,
    val backendId: String = "litertlm.android",
    val capability: String = "multimodal.image",
    val formatVersion: String = "litertlm-model-v1",
    val inputSignature: String = "image-file+text-prompt-v1",
    val runtimeRevision: String = "litertlm-android:0.18.1",
    val maxOutputTokens: Int = 512,
    val maxImageBytes: Long = 16L * 1024 * 1024,
    val maxNumImages: Int = 1,
    val backendPolicy: LiteRtLmBackendPolicy = LiteRtLmBackendPolicy.CPU_ONLY,
) {
    init {
        require(isSafeRelativePath(modelPathRelative))
        require(metadataPathsRelative.isNotEmpty() && metadataPathsRelative.all(::isSafeRelativePath))
        require(modelPathRelative !in metadataPathsRelative)
        require(backendId == "litertlm.android")
        require(capability == "multimodal.image")
        require(formatVersion == "litertlm-model-v1")
        require(inputSignature == "image-file+text-prompt-v1")
        require(runtimeRevision == "litertlm-android:0.18.1")
        require(maxOutputTokens == 512)
        require(maxImageBytes == 16L * 1024 * 1024)
        require(maxNumImages == 1)
    }

    fun resolveModelPath(leaseDirectory: File): File = File(leaseDirectory, modelPathRelative)

    fun matchesManifest(manifest: ModelManifest): Boolean {
        if (manifest.backendId != backendId || manifest.capability != capability ||
            manifest.formatVersion != formatVersion || manifest.processing.inputSignature != inputSignature) return false
        if (!coversVersion(manifest.backendVersion.minimum, manifest.backendVersion.maximumExclusive, "0.18.1")) return false
        if (manifest.licenseIdentifier.isBlank() || manifest.licenseURL.isBlank() ||
            manifest.attribution.isBlank() || manifest.redistribution.isBlank()) return false
        val paths = manifest.files.map { it.path }.toSet()
        return paths.contains(modelPathRelative) && paths.containsAll(metadataPathsRelative)
    }

    companion object {
        internal fun supportsRuntime(minimum: String): Boolean =
            runCatching { compareVersions(minimum, "0.18.1") <= 0 }.getOrDefault(false)

        private fun isSafeRelativePath(path: String): Boolean =
            path.isNotBlank() && !path.startsWith('/') && !path.startsWith(".odai-") &&
                '\\' !in path && '\u0000' !in path &&
                path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

        private fun coversVersion(min: String, maxExclusive: String, current: String): Boolean =
            runCatching { compareVersions(min, current) <= 0 && compareVersions(current, maxExclusive) < 0 }.getOrDefault(false)

        private fun compareVersions(left: String, right: String): Int {
            fun parse(value: String): List<Int> = value.split('.').map {
                require(it.isNotEmpty() && it.all(Char::isDigit))
                it.toInt()
            }
            val a = parse(left)
            val b = parse(right)
            for (index in 0 until maxOf(a.size, b.size)) {
                val order = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
                if (order != 0) return order
            }
            return 0
        }
    }
}

/** Vendor-independent view of the actual loaded model, checked before engine creation. */
internal data class LiteRtLmModelSupport(
    val text: Boolean,
    val vision: Boolean,
    val cpuText: Boolean,
    val cpuVision: Boolean,
    val gpuVision: Boolean,
    val visionTokenBudget: Int,
    val minimumRuntime: String?,
) {
    fun supports(policy: LiteRtLmBackendPolicy): Boolean {
        // Fixed 0.18.1 ModelInfo documents -1 as undefined, rather than unsupported.
        val validBudget = visionTokenBudget == -1 || visionTokenBudget > 0
        val selectedVision = when (policy) {
            LiteRtLmBackendPolicy.CPU_ONLY -> cpuVision
            LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION -> gpuVision
        }
        return text && vision && cpuText && selectedVision && validBudget &&
            (minimumRuntime == null || LiteRtLmModelSpec.supportsRuntime(minimumRuntime))
    }
}

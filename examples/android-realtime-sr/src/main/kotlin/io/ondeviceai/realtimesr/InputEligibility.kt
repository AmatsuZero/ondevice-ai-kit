package io.ondeviceai.realtimesr

/**
 * Compatibility gate for the H.264 demo path. Missing transfer metadata is treated as the
 * ordinary SDR default only when BT.709 is explicit or inferred from the 1920x1080
 * fixture. Missing color standard is inferred as BT.709 only for that resolution.
 */
internal object InputEligibility {
    private const val COLOR_STANDARD_UNSPECIFIED = 0
    private const val COLOR_STANDARD_BT709 = 1
    private const val COLOR_TRANSFER_UNSPECIFIED = 0
    private const val COLOR_TRANSFER_SDR_VIDEO = 3
    private const val COLOR_TRANSFER_ST2084 = 6
    private const val COLOR_TRANSFER_HLG = 7

    internal data class Decision(val eligible: Boolean, val reason: String? = null)

    internal fun evaluate(
        width: Int,
        height: Int,
        frameRate: Int,
        colorStandard: Int,
        colorTransfer: Int
    ): Decision {
        if (width <= 0 || height <= 0) {
            return Decision(false, "Unknown video dimensions; CuNNy disabled")
        }
        if (frameRate != 30) {
            return Decision(false, "Only known 30 fps is supported; CuNNy disabled")
        }
        if (width > 1920 || height > 1080) {
            return Decision(false, "Video exceeds 1920x1080; CuNNy disabled")
        }
        if (colorTransfer == COLOR_TRANSFER_ST2084) {
            return Decision(false, "Unsupported HDR transfer ST2084; CuNNy disabled")
        }
        if (colorTransfer == COLOR_TRANSFER_HLG) {
            return Decision(false, "Unsupported HDR transfer HLG; CuNNy disabled")
        }
        if (colorTransfer != COLOR_TRANSFER_UNSPECIFIED && colorTransfer != COLOR_TRANSFER_SDR_VIDEO) {
            return Decision(false, "Unsupported explicit color transfer ($colorTransfer); CuNNy disabled")
        }
        if (colorStandard != COLOR_STANDARD_UNSPECIFIED && colorStandard != COLOR_STANDARD_BT709) {
            return Decision(false, "Unsupported explicit color standard ($colorStandard); CuNNy disabled")
        }

        val is1080p = width == 1920 && height == 1080
        if (colorStandard == COLOR_STANDARD_UNSPECIFIED && !is1080p) {
            return Decision(false, "Unspecified color standard is supported only for the 1920x1080 input; CuNNy disabled")
        }

        val inferences = mutableListOf<String>()
        if (colorStandard == COLOR_STANDARD_UNSPECIFIED) {
            inferences += "BT.709 inferred from 1920x1080 input"
        }
        if (colorTransfer == COLOR_TRANSFER_UNSPECIFIED) {
            inferences += "SDR inferred from unspecified transfer for H.264/BT.709"
        }
        return Decision(true, inferences.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }
}

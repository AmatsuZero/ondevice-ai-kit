package io.ondeviceai.litertlm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiteRtLmModelSupportTest {
    private val gemma = LiteRtLmModelSupport(text = true, vision = true, cpuText = true,
        cpuVision = false, gpuVision = true, visionTokenBudget = -1, minimumRuntime = null)

    @Test
    fun gpuVisionOnlyModelRequiresExplicitMixedSelection() {
        assertFalse(gemma.supports(LiteRtLmBackendPolicy.CPU_ONLY))
        assertTrue(gemma.supports(LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION))
        assertFalse(gemma.copy(gpuVision = false).supports(LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION))
        assertTrue(gemma.copy(cpuVision = true).supports(LiteRtLmBackendPolicy.CPU_ONLY))
    }

    @Test
    fun undefinedBudgetIsAcceptedButInvalidBudgetOrRuntimeStillRejects() {
        val policy = LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION
        assertTrue(gemma.copy(visionTokenBudget = 256).supports(policy))
        assertFalse(gemma.copy(visionTokenBudget = 0).supports(policy))
        assertFalse(gemma.copy(visionTokenBudget = -2).supports(policy))
        assertFalse(gemma.copy(cpuText = false).supports(policy))
        assertFalse(gemma.copy(text = false).supports(policy))
        assertFalse(gemma.copy(vision = false).supports(policy))
        assertTrue(gemma.copy(minimumRuntime = "0.18.1").supports(policy))
        assertFalse(gemma.copy(minimumRuntime = "0.19.0").supports(policy))
        assertFalse(gemma.copy(minimumRuntime = "unknown").supports(policy))
    }
}

package io.ondeviceai.demo

import kotlin.test.*

class UnifiedDemoScreenContractTest {
    @Test fun leavingRunningCapabilityWaitsForCleanupBeforeChangingRoute() {
        var idle = true
        val left = mutableListOf<DemoCapabilityId>()
        val model = UnifiedDemoModel(onLeave = { left += it }, isIdle = { idle })
        model.navigate(UnifiedDemoRoute.Capability(DemoCapabilityId.ASR))
        idle = false
        model.navigate(UnifiedDemoRoute.Models)
        assertEquals(listOf(DemoCapabilityId.ASR), left)
        assertEquals(UnifiedDemoRoute.Capability(DemoCapabilityId.ASR), model.route)
        assertTrue(model.leaving)
        model.finishNavigation()
        assertTrue(model.leaving)
        idle = true
        model.finishNavigation()
        assertFalse(model.leaving)
        assertEquals(UnifiedDemoRoute.Models, model.route)
    }

    @Test fun recordReopenReturnsToRecordsAndNeverRunsAnOperation() {
        val left = mutableListOf<DemoCapabilityId>()
        val model = UnifiedDemoModel(onLeave = { left += it })
        model.navigateTopLevel(DemoTab.RECORDS)
        model.openCapability(DemoCapabilityId.TTS)
        model.back()
        assertEquals(UnifiedDemoRoute.Records, model.route)
        assertEquals(listOf(DemoCapabilityId.TTS), left)
    }

    @Test fun frozenStatusCannotBePromotedToReady() {
        val model = UnifiedDemoModel()
        model.updateStatus(DemoCapabilityId.IDENTITY_LIVENESS, DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY))
        assertEquals(DemoCapabilityAvailabilityKind.FROZEN_PREVIEW, model.descriptor(DemoCapabilityId.IDENTITY_LIVENESS).status.kind)
    }

    @Test fun statusLabelsKeepHostBackendModelAndPermissionSeparate() {
        val kinds = listOf(DemoCapabilityAvailabilityKind.MISSING_HOST, DemoCapabilityAvailabilityKind.MISSING_BACKEND,
            DemoCapabilityAvailabilityKind.MISSING_MODEL, DemoCapabilityAvailabilityKind.PERMISSION_REQUIRED)
        assertEquals(4, kinds.map { DemoCapabilityStatus(it).label }.toSet().size)
        assertTrue(DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_MODEL, "ESRGAN.tflite").explanation.contains("ESRGAN.tflite"))
    }
}

package io.ondeviceai.demo

import kotlin.test.*

class UnifiedDemoBehaviorTest {
    @Test fun delayedOrFailedHostPreparationNeverReportsReady() {
        assertEquals(DemoCapabilityAvailabilityKind.PREPARING, demoHostAvailability(DemoHostState.PREPARING).kind)
        assertEquals(DemoCapabilityAvailabilityKind.FAILED, demoHostAvailability(DemoHostState.FAILED).kind)
        assertEquals(DemoCapabilityAvailabilityKind.READY, demoHostAvailability(DemoHostState.READY).kind)
    }

    @Test fun terminalRecordsRequireExplicitSaveIncludingCancellationAndCannotBeSavedTwice() {
        val model = UnifiedDemoModel()
        for (outcome in DemoRecordOutcome.entries) {
            model.recordTerminal(DemoCapabilityId.FACE_DETECTION, "test", 1, outcome, "人脸检测")
            val count = model.records.snapshot().size
            assertTrue(model.canSaveRecord(DemoCapabilityId.FACE_DETECTION))
            assertEquals(count, model.records.snapshot().size)
            model.saveRecord(DemoCapabilityId.FACE_DETECTION)
            model.saveRecord(DemoCapabilityId.FACE_DETECTION)
            assertEquals(count + 1, model.records.snapshot().size)
            assertEquals(outcome, model.records.snapshot().last().outcome)
        }
    }

    @Test fun availabilitySeparatesMissingHostModelPermissionAndBackend() {
        assertEquals(DemoCapabilityAvailabilityKind.MISSING_HOST, demoAvailability(hostPresent = false).kind)
        assertEquals(DemoCapabilityAvailabilityKind.MISSING_MODEL, demoAvailability(reason = "modelMissing").kind)
        assertEquals(DemoCapabilityAvailabilityKind.PERMISSION_REQUIRED, demoAvailability(reason = "microphonePermissionDenied").kind)
        assertEquals(DemoCapabilityAvailabilityKind.UNSUPPORTED, demoAvailability(reason = "unsupportedOS").kind)
        assertEquals(DemoCapabilityAvailabilityKind.MISSING_BACKEND, demoAvailability(queried = true).kind)
        assertEquals(DemoCapabilityAvailabilityKind.READY, demoAvailability(available = true).kind)
    }

    @Test fun searchAndCategoryPersistAcrossDetailNavigation() {
        val model = UnifiedDemoModel()
        model.search = "语音"
        model.category = "语音"
        assertEquals(listOf(DemoCapabilityId.ASR, DemoCapabilityId.TTS), model.filteredCatalog.map { it.id })
        model.openCapability(DemoCapabilityId.ASR)
        model.back()
        assertEquals("语音", model.search)
        assertEquals("语音", model.category)
    }

    @Test fun frozenRecordsCannotClaimExecutedSuccess() {
        val model = UnifiedDemoModel()
        model.recordTerminal(DemoCapabilityId.IDENTITY_LIVENESS, "test", 1, DemoRecordOutcome.COMPLETED, "invalid")
        assertTrue(model.records.snapshot().isEmpty())
        model.recordTerminal(DemoCapabilityId.TTS, "test", 2, DemoRecordOutcome.COMPLETED, "完成")
        assertTrue(model.records.snapshot().isEmpty())
        model.saveRecord(DemoCapabilityId.TTS)
        assertEquals(1, model.records.snapshot().size)
    }
}

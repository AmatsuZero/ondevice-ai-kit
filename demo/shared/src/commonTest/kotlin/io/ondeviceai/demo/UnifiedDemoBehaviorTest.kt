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

    @Test fun videoOptInOnlyEnablesVideoUnderstandingAndAllowsSavingCancellation() {
        val disabled = demoCatalogForVideo(enabled = false)
        val enabled = demoCatalogForVideo(enabled = true)
        assertEquals(defaultDemoCatalog, disabled)
        assertEquals(defaultDemoCatalog.map { it.id }, enabled.map { it.id })
        assertEquals(
            listOf(DemoCapabilityId.VIDEO_UNDERSTANDING),
            defaultDemoCatalog.zip(enabled).filter { (before, after) -> before != after }.map { it.first.id },
        )

        val video = enabled.first { it.id == DemoCapabilityId.VIDEO_UNDERSTANDING }
        assertFalse(video.frozen)
        assertFalse(video.previewOnly)
        assertEquals(DemoCapabilityAvailabilityKind.NOT_QUERIED, video.status.kind)
        val identity = enabled.first { it.id == DemoCapabilityId.IDENTITY_LIVENESS }
        assertEquals(defaultDemoCatalog.first { it.id == DemoCapabilityId.IDENTITY_LIVENESS }, identity)
        assertTrue(identity.frozen)

        val active = UnifiedDemoModel(enabled)
        active.updateStatus(DemoCapabilityId.VIDEO_UNDERSTANDING,
            DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY, "backend ready"))
        assertEquals(DemoCapabilityAvailabilityKind.READY,
            active.descriptor(DemoCapabilityId.VIDEO_UNDERSTANDING).status.kind)
        active.recordTerminal(DemoCapabilityId.VIDEO_UNDERSTANDING, "test", 1,
            DemoRecordOutcome.CANCELLED, "用户取消")
        assertTrue(active.canSaveRecord(DemoCapabilityId.VIDEO_UNDERSTANDING))
        assertTrue(active.records.snapshot().isEmpty())
        active.saveRecord(DemoCapabilityId.VIDEO_UNDERSTANDING)
        assertEquals(DemoRecordOutcome.CANCELLED, active.records.snapshot().single().outcome)

        val frozen = UnifiedDemoModel(disabled)
        frozen.updateStatus(DemoCapabilityId.VIDEO_UNDERSTANDING,
            DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY, "backend ready"))
        assertEquals(DemoCapabilityAvailabilityKind.FROZEN_PREVIEW,
            frozen.descriptor(DemoCapabilityId.VIDEO_UNDERSTANDING).status.kind)
        frozen.recordTerminal(DemoCapabilityId.VIDEO_UNDERSTANDING, "test", 2,
            DemoRecordOutcome.CANCELLED, "默认冻结")
        assertFalse(frozen.canSaveRecord(DemoCapabilityId.VIDEO_UNDERSTANDING))
        frozen.saveRecord(DemoCapabilityId.VIDEO_UNDERSTANDING)
        assertTrue(frozen.records.snapshot().isEmpty())
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

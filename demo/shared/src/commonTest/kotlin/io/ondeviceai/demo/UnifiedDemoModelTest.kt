package io.ondeviceai.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedDemoModelTest {
    @Test
    fun catalogContainsNineStableCapabilities() {
        val ids = defaultDemoCatalog.map { it.id }

        assertEquals(9, ids.size)
        assertEquals(9, ids.toSet().size)
        assertTrue(DemoCapabilityId.ASR in ids)
        assertTrue(DemoCapabilityId.TTS in ids)
        assertTrue(DemoCapabilityId.VOICE_EFFECTS in ids)
        assertTrue(DemoCapabilityId.FACE_DETECTION in ids)
        assertTrue(DemoCapabilityId.IMAGE_UNDERSTANDING in ids)
        assertTrue(DemoCapabilityId.FILE_SUPER_RESOLUTION in ids)
        assertTrue(DemoCapabilityId.PLAYBACK_ENHANCEMENT in ids)
        assertTrue(DemoCapabilityId.VIDEO_UNDERSTANDING in ids)
        assertTrue(DemoCapabilityId.IDENTITY_LIVENESS in ids)
    }

    @Test
    fun frozenCapabilitiesExposePreviewOnlyState() {
        val frozen = defaultDemoCatalog.filter { it.frozen }

        assertEquals(setOf(DemoCapabilityId.VIDEO_UNDERSTANDING, DemoCapabilityId.IDENTITY_LIVENESS), frozen.map { it.id }.toSet())
        assertTrue(frozen.all { it.status.kind == DemoCapabilityAvailabilityKind.FROZEN_PREVIEW })
        assertTrue(frozen.all { it.previewOnly })
        assertTrue(frozen.all { it.previewCopy.isNotBlank() })
    }

    @Test
    fun recordsKeepNewestThirtyEntriesAndClearWithoutLeases() {
        val records = DemoRecordsStore()

        repeat(31) { index ->
            records.append(
                DemoRecord(
                    capability = DemoCapabilityId.ASR,
                    platform = "Android",
                    timestampMillis = index.toLong(),
                    outcome = DemoRecordOutcome.COMPLETED,
                    summary = "run-$index",
                ),
            )
        }

        assertEquals(30, records.snapshot().size)
        assertEquals("run-1", records.snapshot().first().summary)
        assertEquals("run-30", records.snapshot().last().summary)
        val captured = records.snapshot()

        records.clear()

        assertTrue(records.snapshot().isEmpty())
        assertEquals(30, captured.size)
    }

    @Test
    fun capabilityRoutesReturnToTheirOwningTopLevelTab() {
        assertEquals(UnifiedDemoRoute.Explore, UnifiedDemoRoute.Explore.topLevel())
        assertEquals(UnifiedDemoRoute.Models, UnifiedDemoRoute.Models.topLevel())
        assertEquals(UnifiedDemoRoute.Records, UnifiedDemoRoute.Records.topLevel())
        assertEquals(UnifiedDemoRoute.Explore, UnifiedDemoRoute.Capability(DemoCapabilityId.ASR).topLevel())
        assertEquals(UnifiedDemoRoute.Models, UnifiedDemoRoute.ModelDetail(DemoCapabilityId.TTS).topLevel())
        assertEquals(UnifiedDemoRoute.Records, UnifiedDemoRoute.RecordDetail(0).topLevel())
    }
}

package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class GalleryTask : IdentityBridgeTask {
    var cancels = 0
    override fun cancel() { cancels++ }
    override fun close() { cancels++ }
}

private class GalleryBridge : IdentityBridge by UnavailableIdentityBridge() {
    val created = mutableListOf<IdentityCallback<IdentityGalleryBuilderReference>>()
    val added = mutableListOf<Pair<IdentityTemplateReference, IdentityCallback<Unit>>>()
    val published = mutableListOf<IdentityCallback<IdentityGalleryReference>>()
    val closed = mutableListOf<IdentityGalleryBuilderReference>()
    val tasks = mutableListOf<GalleryTask>()
    var immediate = false

    private fun task() = GalleryTask().also(tasks::add)
    override fun createGalleryBuilder(baseGallery: IdentityGalleryReference?, grant: IdentityAuthorizationGrant,
        options: IdentityGalleryOptions, backend: IdentityBackendSelection,
        callback: IdentityCallback<IdentityGalleryBuilderReference>): IdentityBridgeTask {
        check(baseGallery == null)
        created += callback
        if (immediate) callback.onSuccess(IdentityGalleryBuilderReference("builder"))
        return task()
    }
    override fun addTemplate(builder: IdentityGalleryBuilderReference, template: IdentityTemplateReference,
        grant: IdentityAuthorizationGrant, callback: IdentityCallback<Unit>): IdentityBridgeTask {
        added += template to callback
        if (immediate) callback.onSuccess(Unit)
        return task()
    }
    override fun publishGallery(builder: IdentityGalleryBuilderReference,
        callback: IdentityCallback<IdentityGalleryReference>): IdentityBridgeTask {
        published += callback
        if (immediate) callback.onSuccess(IdentityGalleryReference("gallery"))
        return task()
    }
    override fun closeGalleryBuilder(builder: IdentityGalleryBuilderReference, callback: IdentityCallback<Unit>): IdentityBridgeTask {
        closed += builder
        callback.onSuccess(Unit)
        return task()
    }
}

private class GalleryTerminal : IdentityCallback<IdentityGalleryReference> {
    val successes = mutableListOf<IdentityGalleryReference>()
    val failures = mutableListOf<IdentityBridgeFailure>()
    override fun onSuccess(value: IdentityGalleryReference) { successes += value }
    override fun onFailure(error: IdentityBridgeFailure) { failures += error }
}

class IdentityGalleryPublicationTest {
    private val grant = IdentityAuthorizationGrant("grant")
    private val options = IdentityGalleryOptions(IdentityThresholdPolicy("policy"), "subject-max-v1")
    private val first = IdentityTemplateReference("template-one")
    private val second = IdentityTemplateReference("template-two")

    @Test fun emptyMembersFailBeforeOpeningABuilder() {
        val bridge = GalleryBridge(); val terminal = GalleryTerminal()
        publishIdentityGallery(bridge, grant, options, IdentityBackendSelection.Default, emptyList(), terminal)
        assertEquals(listOf<IdentityBridgeFailure>(IdentityBridgeFailure.Rejected(IdentityErrorCode.INVALID_INPUT)), terminal.failures)
        assertTrue(bridge.created.isEmpty())
    }

    @Test fun asynchronousPublicationAddsDistinctTemplatesThenClosesBuilder() {
        val bridge = GalleryBridge(); val terminal = GalleryTerminal()
        publishIdentityGallery(bridge, grant, options, IdentityBackendSelection.Explicit("backend"), listOf(first, first, second), terminal)
        bridge.created.single().onSuccess(IdentityGalleryBuilderReference("builder"))
        assertEquals(listOf(first), bridge.added.map { it.first })
        bridge.added.single().second.onSuccess(Unit)
        assertEquals(listOf(first, second), bridge.added.map { it.first })
        bridge.added.last().second.onSuccess(Unit)
        bridge.published.single().onSuccess(IdentityGalleryReference("gallery"))
        assertEquals(listOf(IdentityGalleryReference("gallery")), terminal.successes)
        assertEquals(listOf(IdentityGalleryBuilderReference("builder")), bridge.closed)
    }

    @Test fun synchronousBridgeDrainsLargeListWithoutRecursiveCallbackGrowth() {
        val bridge = GalleryBridge().apply { immediate = true }; val terminal = GalleryTerminal()
        val members = (0 until 256).map { IdentityTemplateReference("template-$it") }
        publishIdentityGallery(bridge, grant, options, IdentityBackendSelection.Default, members, terminal)
        assertEquals(256, bridge.added.size)
        assertEquals(listOf(IdentityGalleryReference("gallery")), terminal.successes)
        assertEquals(1, bridge.closed.size)
    }

    @Test fun firstFailureClosesBuilderAndSuppressesLateTerminals() {
        val bridge = GalleryBridge(); val terminal = GalleryTerminal()
        publishIdentityGallery(bridge, grant, options, IdentityBackendSelection.Default, listOf(first), terminal)
        bridge.created.single().onSuccess(IdentityGalleryBuilderReference("builder"))
        bridge.added.single().second.onFailure(IdentityBridgeFailure.Rejected(IdentityErrorCode.TEMPLATE_REVOKED))
        bridge.added.single().second.onSuccess(Unit)
        assertEquals(listOf<IdentityBridgeFailure>(IdentityBridgeFailure.Rejected(IdentityErrorCode.TEMPLATE_REVOKED)), terminal.failures)
        assertTrue(terminal.successes.isEmpty())
        assertEquals(listOf(IdentityGalleryBuilderReference("builder")), bridge.closed)
    }

    @Test fun cancellationStopsCurrentCallAndClosesBuilderDeliveredAfterward() {
        val bridge = GalleryBridge(); val terminal = GalleryTerminal()
        val operation = publishIdentityGallery(bridge, grant, options, IdentityBackendSelection.Default, listOf(first), terminal)
        operation.cancel()
        assertEquals(1, bridge.tasks.single().cancels)
        bridge.created.single().onSuccess(IdentityGalleryBuilderReference("late-builder"))
        assertEquals(listOf<IdentityBridgeFailure>(IdentityBridgeFailure.Cancelled), terminal.failures)
        assertEquals(listOf(IdentityGalleryBuilderReference("late-builder")), bridge.closed)
        assertTrue(bridge.added.isEmpty())
    }

    @Test fun duplicateTerminalFromPublishIsSuppressed() {
        val bridge = GalleryBridge(); val terminal = GalleryTerminal()
        publishIdentityGallery(bridge, grant, options, IdentityBackendSelection.Default, listOf(first), terminal)
        bridge.created.single().onSuccess(IdentityGalleryBuilderReference("builder"))
        bridge.added.single().second.onSuccess(Unit)
        bridge.published.single().onSuccess(IdentityGalleryReference("gallery"))
        bridge.published.single().onFailure(IdentityBridgeFailure.Rejected(IdentityErrorCode.CLOSED))
        assertEquals(1, terminal.successes.size)
        assertFalse(terminal.failures.isNotEmpty())
    }
}

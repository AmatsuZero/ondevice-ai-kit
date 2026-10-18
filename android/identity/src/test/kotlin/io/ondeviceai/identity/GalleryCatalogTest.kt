package io.ondeviceai.identity

import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class GalleryCatalogTest {
    private val roots = mutableListOf<Path>()
    @AfterTest fun cleanup() {
        roots.forEach { root -> Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    private fun root() = Files.createTempDirectory("gallery-catalog").also(roots::add)
    @Test fun encryptedCatalogPersistsImmutableRevisionsAndCompareAndSwapHead() {
        val root = root()
        val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId)
        catalog.publish(first)
        val child = snapshot(catalog.storeId, first.ref)
        catalog.publish(child)
        assertEquals(child.ref, catalog.head(first.ref.id))
        assertEquals(first, catalog.snapshot(first.ref))
        assertFailsWith<IdentityError> { catalog.publish(child) }
        val storeId = catalog.storeId
        catalog.close()
        val reopened = GalleryCatalog(root, "identity-store", keys)
        assertEquals(storeId, reopened.storeId)
        assertEquals(child.ref, reopened.head(first.ref.id))
        reopened.close()
    }

    @Test fun catalogRejectsForeignStoreAndUnpublishedParent() {
        val catalog = GalleryCatalog(root(), "identity-store", GalleryCatalogKeys())
        assertFailsWith<IdentityError> { catalog.publish(snapshot("foreign")) }
        val parent = GalleryRef("lineage", "missing")
        assertFailsWith<IdentityError> { catalog.publish(snapshot(catalog.storeId, parent)) }
        assertNull(catalog.head("lineage"))
        catalog.close()
    }

    @Test fun capacityRejectionLeavesPriorHeadUsableInsteadOfRetiringTheCatalog() {
        val catalog = GalleryCatalog(root(), "identity-store", GalleryCatalogKeys())
        try {
            var parent: GalleryRef? = null
            var reached = false
            for (index in 0 until 32) {
                val record = snapshot(catalog.storeId, parent)
                try { catalog.publish(record); parent = record.ref }
                catch (error: IdentityError) { assertEquals(IdentityErrorCode.INVALID_INPUT, error.code); reached = true; break }
            }
            assertTrue(reached)
            assertEquals(parent, catalog.head("lineage"))
            assertEquals(parent, catalog.snapshot(requireNotNull(parent)).ref)
        } finally { catalog.close() }
    }

    @Test fun capacityBoundaryStillCommitsDeletionTombstonesForEveryPublishedLineage() {
        val catalog = GalleryCatalog(root(), "identity-store", GalleryCatalogKeys())
        try {
            fun records(lengths: List<Int>) = lengths.mapIndexed { index, length ->
                snapshot(catalog.storeId, aggregation = "a".repeat(length), lineage = "lineage-$index")
            }
            val lengths = MutableList(12) { 1 }
            val baseline = records(lengths)
            val encodedSize = IdentityContractJSON.encode(mapOf("version" to 2, "storeId" to catalog.storeId,
                "identityStoreId" to "identity-store", "snapshots" to baseline.associate { it.ref.revision to it.canonicalJSON },
                "heads" to baseline.associate { it.ref.id to it.ref.revision }, "tombstones" to emptyMap<String, String>())).encodeToByteArray().size
            // Pack the candidate state to six bytes below the real journal ceiling.
            // Padding valid policy IDs changes only length, not the membership facts.
            var padding = 65_530 - encodedSize
            check(padding in 1..(12 * 255))
            for (index in lengths.indices) {
                val extra = minOf(255, padding); lengths[index] += extra; padding -= extra
            }
            check(padding == 0)
            val published = mutableListOf<GalleryRef>()
            for (record in records(lengths)) {
                try { catalog.publish(record); published.add(record.ref) }
                catch (error: IdentityError) { assertEquals(IdentityErrorCode.INVALID_INPUT, error.code); break }
            }
            assertTrue(published.isNotEmpty())
            published.forEachIndexed { index, ref ->
                val receipt = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"
                catalog.tombstoneLineage(ref.id, receipt)
                assertEquals(receipt, catalog.tombstoneReceipt(ref.id))
            }
        } finally { catalog.close() }
    }

    @Test fun restoreRejectsWrongIdentityWithoutRewritingTheJournal() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        catalog.publish(snapshot(catalog.storeId)); catalog.close()
        val file = root.resolve("identity-journal-index.bin"); val before = Files.readAllBytes(file)
        assertFailsWith<IdentityError> { GalleryCatalog(root, "foreign", keys) }
        assertTrue(before.contentEquals(Files.readAllBytes(file)))
        GalleryCatalog(root, "identity-store", keys).close()
    }

    @Test fun strictRestoreRejectsDisconnectedParentHeadAndOwnershipFacts() {
        val root = root(); val keys = GalleryCatalogKeys()
        val first = snapshot("index-store")
        val child = snapshot("index-store", first.ref)
        val fork = snapshot("index-store", first.ref, "other-policy")
        val valid = mapOf<String, Any?>("version" to 1, "storeId" to "index-store", "identityStoreId" to "identity-store",
            "snapshots" to mapOf(first.ref.revision to first.canonicalJSON, child.ref.revision to child.canonicalJSON),
            "heads" to mapOf("lineage" to child.ref.revision))
        val mutations: List<(MutableMap<String, Any?>) -> Unit> = listOf(
            { it["heads"] = mapOf("lineage" to first.ref.revision) },
            { it["snapshots"] = mapOf(child.ref.revision to child.canonicalJSON) },
            { it["heads"] = mapOf("wrong" to child.ref.revision) },
            { it["heads"] = emptyMap<String, String>() },
            { it["snapshots"] = mapOf(first.ref.revision to first.canonicalJSON, child.ref.revision to child.canonicalJSON, fork.ref.revision to fork.canonicalJSON) },
            { it["storeId"] = "foreign" }, { it["version"] = true }, { it["unknown"] = "value" }
        )
        mutations.forEach { mutation ->
            val fields = valid.toMutableMap().also(mutation)
            IdentityJournal(root, "index", keys).use { it.commit(IdentityContractJSON.encode(fields).encodeToByteArray()) }
            val file = root.resolve("identity-journal-index.bin"); val before = Files.readAllBytes(file)
            assertFailsWith<IdentityError> { GalleryCatalog(root, "identity-store", keys) }
            assertTrue(before.contentEquals(Files.readAllBytes(file)))
        }
    }

    @Test fun journalFaultRetiresAdmissionAndReservesWriterUntilClose() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId)
        catalog.publish(first)
        try {
            keys.denied = true
            assertFailsWith<IdentityError> { catalog.publish(snapshot(catalog.storeId, first.ref)) }
            assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { catalog.snapshot(first.ref) }.code)
            keys.denied = false
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { GalleryCatalog(root, "identity-store", keys) }.code)
        } finally { keys.denied = false; catalog.close() }
        GalleryCatalog(root, "identity-store", keys).use { assertEquals(first.ref, it.head(first.ref.id)) }
    }

    @Test fun concurrentChildrenHaveExactlyOneCasWinner() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        val left = snapshot(catalog.storeId, first.ref)
        val right = snapshot(catalog.storeId, first.ref, "other-policy")
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1); val errors = CopyOnWriteArrayList<IdentityErrorCode>()
        val executor = Executors.newFixedThreadPool(2)
        keys.onNextKey = { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)) }
        try {
            val a = executor.submit { catalog.publish(left) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val b = executor.submit {
                secondStarted.countDown()
                try { catalog.publish(right) } catch (error: IdentityError) { errors.add(error.code) }
            }
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
            release.countDown()
            a.get(3, TimeUnit.SECONDS); b.get(3, TimeUnit.SECONDS)
            assertEquals(listOf(IdentityErrorCode.GALLERY_REVISION_CONFLICT), errors.toList())
            assertEquals(left.ref, catalog.head(first.ref.id))
        } finally { release.countDown(); executor.shutdownNow(); catalog.close() }
    }

    @Test fun providerReentryCannotCloseOrMutateAnActiveCommit() {
        val keys = GalleryCatalogKeys(); val catalog = GalleryCatalog(root(), "identity-store", keys)
        try {
            val first = snapshot(catalog.storeId)
            keys.onNextKey = {
                assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { catalog.close() }.code)
                assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { catalog.publish(first) }.code)
            }
            catalog.publish(first)
            assertEquals(first.ref, catalog.head(first.ref.id))
        } finally { catalog.close() }
    }

    @Test fun logicalDeletionSurvivesReopenAndPhysicalCleanupWithoutRevivingAStaleBuilder() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        val child = snapshot(catalog.storeId, first.ref); catalog.publish(child)
        val stale = snapshot(catalog.storeId, child.ref)
        catalog.tombstoneLineage(first.ref.id, "delete-receipt")
        assertEquals("delete-receipt", catalog.tombstoneReceipt(first.ref.id))
        assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { catalog.head(first.ref.id) }.code)
        assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { catalog.snapshot(first.ref) }.code)
        assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { catalog.publish(stale) }.code)
        catalog.close()
        val reopened = GalleryCatalog(root, "identity-store", keys)
        assertEquals("delete-receipt", reopened.tombstoneReceipt(first.ref.id))
        assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { reopened.snapshot(child.ref) }.code)
        reopened.removeLineage(first.ref.id)
        reopened.removeLineage(first.ref.id)
        reopened.close()
        IdentityJournal(root, "index", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString())
            assertEquals(emptyMap<String, String>(), fields["snapshots"])
            assertEquals(emptyMap<String, String>(), fields["heads"])
            assertEquals(mapOf(first.ref.id to "delete-receipt"), fields["tombstones"])
        }
        GalleryCatalog(root, "identity-store", keys).use { cleaned ->
            assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { cleaned.head(first.ref.id) }.code)
            assertEquals("delete-receipt", cleaned.tombstoneReceipt(first.ref.id))
            assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { cleaned.publish(first) }.code)
        }
    }

    @Test fun deletionOfOneLineagePreservesOtherLineagesAndStableReceiptIdentity() {
        val catalog = GalleryCatalog(root(), "identity-store", GalleryCatalogKeys())
        try {
            val first = snapshot(catalog.storeId); catalog.publish(first)
            val other = snapshot(catalog.storeId, lineage = "other"); catalog.publish(other)
            assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { catalog.removeLineage(first.ref.id) }.code)
            assertEquals(first.ref, catalog.head(first.ref.id))
            assertEquals(IdentityErrorCode.GALLERY_NOT_FOUND, assertFailsWith<IdentityError> { catalog.tombstoneLineage("missing", "receipt") }.code)
            catalog.tombstoneLineage(first.ref.id, "receipt")
            catalog.tombstoneLineage(first.ref.id, "receipt")
            assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { catalog.tombstoneLineage(first.ref.id, "different") }.code)
            assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { catalog.tombstoneLineage(other.ref.id, "receipt") }.code)
            catalog.removeLineage(first.ref.id)
            assertEquals(other.ref, catalog.head(other.ref.id))
            assertEquals(other, catalog.snapshot(other.ref))
        } finally { catalog.close() }
    }

    @Test fun tombstoneCommitFaultRetiresAdmissionAndReopenKeepsPriorDurableHead() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        try {
            keys.denied = true
            assertFailsWith<IdentityError> { catalog.tombstoneLineage(first.ref.id, "receipt") }
            assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { catalog.head(first.ref.id) }.code)
            keys.denied = false
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { GalleryCatalog(root, "identity-store", keys) }.code)
        } finally { keys.denied = false; catalog.close() }
        GalleryCatalog(root, "identity-store", keys).use {
            assertNull(it.tombstoneReceipt(first.ref.id)); assertEquals(first.ref, it.head(first.ref.id))
        }
    }

    @Test fun physicalCleanupFaultRetainsCommittedTombstoneAcrossRecovery() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        catalog.tombstoneLineage(first.ref.id, "receipt")
        try {
            keys.denied = true
            assertFailsWith<IdentityError> { catalog.removeLineage(first.ref.id) }
        } finally { keys.denied = false; catalog.close() }
        GalleryCatalog(root, "identity-store", keys).use {
            assertEquals("receipt", it.tombstoneReceipt(first.ref.id))
            assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { it.snapshot(first.ref) }.code)
            it.removeLineage(first.ref.id)
        }
        GalleryCatalog(root, "identity-store", keys).use { assertEquals("receipt", it.tombstoneReceipt(first.ref.id)) }
    }

    @Test fun purgeFailureMarkerPersistsUntilSuccessfulPhysicalCleanup() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        catalog.tombstoneLineage(first.ref.id, "receipt")
        assertEquals(DeletionStatus.PURGE_PENDING, catalog.deletionStatus(first.ref.id))
        catalog.markPurgeAttempt(first.ref.id)
        assertEquals(DeletionStatus.PURGE_FAILED, catalog.deletionStatus(first.ref.id))
        val other = snapshot(catalog.storeId, lineage = "other"); catalog.publish(other)
        catalog.tombstoneLineage(other.ref.id, "other-receipt")
        keys.denied = true
        assertFailsWith<IdentityError> { catalog.removeLineage(first.ref.id) }
        keys.denied = false
        catalog.close()

        GalleryCatalog(root, "identity-store", keys).use { reopened ->
            assertEquals(DeletionStatus.PURGE_FAILED, reopened.deletionStatus(first.ref.id))
            reopened.removeLineage(first.ref.id)
            assertEquals(DeletionStatus.PURGED, reopened.deletionStatus(first.ref.id))
            keys.denied = true
            reopened.removeLineage(first.ref.id)
            reopened.markPurgeAttempt(first.ref.id)
            assertEquals(DeletionStatus.PURGED, reopened.deletionStatus(first.ref.id))
            keys.denied = false
        }
    }

    @Test fun failedMarkerWriteDoesNotClaimADurableFailureAfterRecovery() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        catalog.tombstoneLineage(first.ref.id, "receipt")
        keys.denied = true
        assertFailsWith<IdentityError> { catalog.markPurgeAttempt(first.ref.id) }
        keys.denied = false; catalog.close()
        GalleryCatalog(root, "identity-store", keys).use { reopened ->
            assertEquals(DeletionStatus.PURGE_PENDING, reopened.deletionStatus(first.ref.id))
        }
    }

    @Test fun versionTwoPendingDeletionUpgradesAndRetainsPurgeAttempt() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        catalog.tombstoneLineage(first.ref.id, "receipt"); catalog.close()
        IdentityJournal(root, "index", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
            fields["version"] = 2L; fields.remove("purgeFailures")
            journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
        }
        GalleryCatalog(root, "identity-store", keys).use {
            assertEquals(DeletionStatus.PURGE_PENDING, it.deletionStatus(first.ref.id))
            it.markPurgeAttempt(first.ref.id)
        }
        GalleryCatalog(root, "identity-store", keys).use {
            assertEquals(DeletionStatus.PURGE_FAILED, it.deletionStatus(first.ref.id))
            it.removeLineage(first.ref.id)
            assertEquals(DeletionStatus.PURGED, it.deletionStatus(first.ref.id))
        }
    }

    @Test fun invalidPurgeMarkersAreRejectedWithoutRewriting() {
        val root = root(); val keys = GalleryCatalogKeys()
        val catalog = GalleryCatalog(root, "identity-store", keys)
        val first = snapshot(catalog.storeId); catalog.publish(first)
        catalog.tombstoneLineage(first.ref.id, "receipt"); catalog.markPurgeAttempt(first.ref.id); catalog.close()
        val valid = IdentityJournal(root, "index", keys).use { IdentityContractJSON.parse(requireNotNull(it.read()).decodeToString()) }
        val mutations: List<(MutableMap<String, Any?>) -> Unit> = listOf(
            { it["purgeFailures"] = listOf("unknown") },
            { it["purgeFailures"] = listOf(first.ref.id, first.ref.id) },
            { it["purgeFailures"] = "invalid" },
            { it["heads"] = emptyMap<String, String>(); it["snapshots"] = emptyMap<String, String>() }
        )
        mutations.forEach { mutation ->
            val bytes = IdentityContractJSON.encode(valid.toMutableMap().also(mutation)).encodeToByteArray()
            IdentityJournal(root, "index", keys).use { it.commit(bytes) }
            assertFailsWith<IdentityError> { GalleryCatalog(root, "identity-store", keys) }
            IdentityJournal(root, "index", keys).use { assertTrue(bytes.contentEquals(it.read())) }
        }
    }

    @Test fun restoreRejectsMalformedOrAliasedDeletionReceiptsWithoutRewriting() {
        val root = root(); val keys = GalleryCatalogKeys()
        val valid = mapOf<String, Any?>("version" to 2, "storeId" to "index-store", "identityStoreId" to "identity-store",
            "snapshots" to emptyMap<String, String>(), "heads" to emptyMap<String, String>(), "tombstones" to mapOf("deleted" to "receipt"))
        val mutations: List<(MutableMap<String, Any?>) -> Unit> = listOf(
            { it["tombstones"] = mapOf("deleted" to 1) },
            { it["tombstones"] = mapOf("deleted" to "receipt", "other" to "receipt") },
            { it["tombstones"] = mapOf("bad token" to "receipt") },
            { it["tombstones"] = mapOf("deleted" to "bad token") },
            { it.remove("tombstones") }, { it["unknown"] = true }
        )
        mutations.forEach { mutation ->
            IdentityJournal(root, "index", keys).use { it.commit(IdentityContractJSON.encode(valid.toMutableMap().also(mutation)).encodeToByteArray()) }
            val file = root.resolve("identity-journal-index.bin"); val before = Files.readAllBytes(file)
            assertFailsWith<IdentityError> { GalleryCatalog(root, "identity-store", keys) }
            assertTrue(before.contentEquals(Files.readAllBytes(file)))
        }
    }

    private fun snapshot(indexStoreId: String, parent: GalleryRef? = null, aggregation: String = "subject-max-v1", lineage: String? = null): GallerySnapshotRecord {
        val fixturePath = sequenceOf(Path.of("fixtures/contracts/gallery-snapshots.json"), Path.of("../../fixtures/contracts/gallery-snapshots.json"), Path.of("../../../fixtures/contracts/gallery-snapshots.json")).first(Files::exists)
        val root = IdentityContractJSON.parse(Files.readAllBytes(fixturePath).decodeToString())
        val entry = (root["records"] as List<*>).first() as Map<*, *>
        val original = GallerySnapshotRecord.restore(entry["snapshotJSON"] as String)
        return GallerySnapshotRecord.create(indexStoreId, original.identityStoreId, lineage ?: original.ref.id, parent,
            original.members, original.operationGrant, original.policy, aggregation)
    }
}

private class GalleryCatalogKeys : IdentityKeyProvider {
    private val values = mutableMapOf<String, SecretKey>()
    @Volatile var denied = false
    @Volatile var onNextKey: (() -> Unit)? = null
    @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey {
        val callback = onNextKey; onNextKey = null; callback?.invoke()
        if (denied) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        values[id]?.let { return it }
        if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        return SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES").also { values[id] = it }
    }
    @Synchronized override fun deleteKey(id: String) { values.remove(id) }
}

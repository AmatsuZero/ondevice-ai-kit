package io.ondeviceai.identity

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IndexStoreTest {
    private val roots = mutableListOf<Path>()
    @AfterTest fun cleanup() { roots.forEach { Files.walk(it).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } } }
    private fun root() = Files.createTempDirectory("identity-index").also(roots::add)

    @Test fun immutableRevisionsCasReadLeaseAndLineageDeletionPersist() {
        val f = fixture()
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant)
        val first = builder.publish()
        val lease = index.acquireIndexReadLease(first, f.grant)
        val left = index.createGalleryBuilder(first, f.grant, f.policy)
        val right = index.createGalleryBuilder(first, f.grant, f.policy)
        val child = left.publish()
        assertEquals(first.id, child.id); assertNotEquals(first.revision, child.revision)
        assertEquals(IdentityErrorCode.GALLERY_REVISION_CONFLICT, assertFailsWith<IdentityError> { right.publish() }.code)
        assertEquals(1, lease.snapshot().members.size)
        val receipt = index.deleteGallery(first, f.owner)
        assertEquals(DeletionStatus.PURGE_PENDING, index.awaitDeletion(receipt))
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { lease.snapshot() }.code)
        lease.close()
        assertEquals(DeletionStatus.PURGED, index.awaitDeletion(receipt))
        f.identity.acquireTemplateLease(f.template, f.grant, IdentityScope.VERIFICATION, f.template.embeddingSpaceId).close()
        index.close()
        IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.use { reopened ->
            assertEquals(receipt, reopened.deleteGallery(first, f.owner))
            assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { reopened.acquireIndexReadLease(first, f.grant) }.code)
        }
        f.identity.close()
    }

    @Test fun failedChildBuildKeepsPriorIndexQueryableAndDiscardsBuilder() {
        val f = fixture(); var calls = 0
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { embeddings, _ ->
            assertEquals(f.template.embeddingSpaceId, embeddings.single().embeddingSpace)
            if (++calls > 1) throw IdentityError(IdentityErrorCode.INDEX_UNAVAILABLE)
            byteArrayOf(1)
        }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val base = builder.publish(); val child = index.createGalleryBuilder(base, f.grant, f.policy)
        assertEquals(IdentityErrorCode.INDEX_UNAVAILABLE, assertFailsWith<IdentityError> { child.publish() }.code)
        assertEquals(IdentityErrorCode.BUILDER_CLOSED, assertFailsWith<IdentityError> { child.publish() }.code)
        index.acquireIndexReadLease(base, f.grant).close()
        index.close(); f.identity.close()
    }

    @Test fun ownershipDenialCannotTombstoneOrCreateADeletionReceipt() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish()
        f.authority.denyOwnership = true
        assertEquals(IdentityErrorCode.AUTHORIZATION_REQUIRED, assertFailsWith<IdentityError> { index.deleteGallery(ref, f.owner) }.code)
        f.authority.denyOwnership = false
        index.acquireIndexReadLease(ref, f.grant).close()
        index.close(); f.identity.close()
    }

    @Test fun revocationStopsTheWholeReadLeaseWithoutSilentlyFiltering() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish(); val lease = index.acquireIndexReadLease(ref, f.grant)
        f.identity.revokeGrant(f.grant.grantId, f.owner)
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { lease.snapshot() }.code)
        assertFailsWith<IdentityError> { index.acquireIndexReadLease(ref, f.grant) }
        lease.close(); index.close(); f.identity.close()
    }

    @Test fun logicalDeletionDoesNotWaitForTheChildBuildToReturn() {
        val f = fixture(); var calls = 0
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ ->
            if (++calls > 1) { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)) }
            byteArrayOf(1)
        }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val base = builder.publish(); val child = index.createGalleryBuilder(base, f.grant, f.policy)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publication = executor.submit<IdentityErrorCode?> { try { child.publish(); null } catch (error: IdentityError) { error.code } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val deletion = executor.submit<DeletionReceipt> { index.deleteGallery(base, f.owner) }
            val receipt = deletion.get(1, TimeUnit.SECONDS)
            assertEquals(DeletionStatus.PURGE_PENDING, index.awaitDeletion(receipt), "native child build still owns derived work")
            release.countDown()
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, publication.get(2, TimeUnit.SECONDS))
            assertEquals(DeletionStatus.PURGED, index.awaitDeletion(receipt))
        } finally { release.countDown(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun identityOriginatedStopCallbackCanReenterIndexClose() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val ref = builder.publish()
        val lease = index.acquireIndexReadLease(ref, f.grant)
        val entered = CountDownLatch(1); val returned = CountDownLatch(1)
        val registration = lease.registerStopHandler { entered.countDown(); index.close(); returned.countDown() }
        val executor = Executors.newSingleThreadExecutor()
        val revocation = executor.submit { f.identity.revokeGrant(f.grant.grantId, f.owner) }
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertTrue(returned.await(200, TimeUnit.MILLISECONDS), "close waited for the ticket owned by its active stop callback")
        } finally {
            lease.close(); revocation.get(2, TimeUnit.SECONDS); registration.close()
            executor.shutdownNow(); index.close(); f.identity.close()
        }
    }

    @Test fun preInterruptedPublicationDoesNotStartAnExpensiveBuild() {
        val f = fixture(); val built = AtomicBoolean()
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> built.set(true); byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val outcome = executor.submit<IdentityErrorCode?> {
                Thread.currentThread().interrupt()
                try { builder.publish(); null } catch (error: IdentityError) { error.code }
                finally { Thread.interrupted() }
            }
            assertEquals(IdentityErrorCode.CANCELLED, outcome.get(2, TimeUnit.SECONDS))
            assertTrue(!built.get(), "pre-cancelled publication entered the backend")
        } finally { executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun everyConcurrentCloseRetainsTheWriterUntilReadLeaseRelease() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val ref = builder.publish()
        val lease = index.acquireIndexReadLease(ref, f.grant); val stopped = CountDownLatch(1)
        val registration = lease.registerStopHandler { stopped.countDown() }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { index.close() }
            assertTrue(stopped.await(1, TimeUnit.SECONDS))
            val second = executor.submit { index.close() }
            assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { lease.snapshot() }.code)
            assertFailsWith<java.util.concurrent.TimeoutException> { second.get(50, TimeUnit.MILLISECONDS) }
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> {
                IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.close()
            }.code)
            lease.close(); first.get(2, TimeUnit.SECONDS); second.get(2, TimeUnit.SECONDS)
            IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.close()
        } finally { lease.close(); registration.close(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun indexOriginatedStopCallbackCanReenterClose() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val lease = index.acquireIndexReadLease(builder.publish(), f.grant)
        val returned = CountDownLatch(1)
        val registration = lease.registerStopHandler { index.close(); returned.countDown() }
        val executor = Executors.newSingleThreadExecutor()
        val closing = executor.submit { index.close() }
        try { assertTrue(returned.await(1, TimeUnit.SECONDS)) }
        finally { lease.close(); closing.get(2, TimeUnit.SECONDS); registration.close(); executor.shutdownNow(); f.identity.close() }
    }

    @Test fun builderRejectsSecondWriterWhileMemberAdmissionIsBlocked() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.authority.onEpoch = { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val addition = executor.submit { builder.addTemplate(f.template, f.grant) }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { builder.publish() }.code)
            release.countDown(); addition.get(2, TimeUnit.SECONDS); builder.publish()
        } finally { release.countDown(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun closeRetainsTheWriterUntilAnInFlightBuildReturns() {
        val f = fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publishing = executor.submit<IdentityErrorCode?> { try { builder.publish(); null } catch (error: IdentityError) { error.code } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val closing = executor.submit { index.close() }
            assertFailsWith<java.util.concurrent.TimeoutException> { closing.get(50, TimeUnit.MILLISECONDS) }
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.close() }.code)
            release.countDown(); assertEquals(IdentityErrorCode.CLOSED, publishing.get(2, TimeUnit.SECONDS)); closing.get(2, TimeUnit.SECONDS)
        } finally { release.countDown(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun releasingDeletedLineagePreservesAnotherLeasedLineage() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        fun publish(): GalleryRef {
            val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
            builder.addTemplate(f.template, f.grant); return builder.publish()
        }
        val first = publish(); val other = publish()
        val left = index.acquireIndexReadLease(first, f.grant); val right = index.acquireIndexReadLease(other, f.grant)
        val receipt = index.deleteGallery(first, f.owner); left.close()
        assertEquals(DeletionStatus.PURGED, index.awaitDeletion(receipt)); assertEquals(other, right.snapshot().ref)
        right.close(); index.close(); f.identity.close()
    }

    @Test fun reopenedDerivedIndexRequiresRebuildInsteadOfServingAnEmptyIndex() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val ref = builder.publish(); index.close()
        IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.use { reopened ->
            assertEquals(IdentityErrorCode.REBUILD_REQUIRED, assertFailsWith<IdentityError> { reopened.acquireIndexReadLease(ref, f.grant) }.code)
        }
        f.identity.close()
    }

    @Test fun failedTombstoneCommitNeverCachesAnUncommittedReceipt() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val ref = builder.publish()
        f.keys.denied = true
        assertEquals(IdentityErrorCode.RE_ENROLLMENT_REQUIRED, assertFailsWith<IdentityError> { index.deleteGallery(ref, f.owner) }.code)
        f.keys.denied = false
        assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { index.deleteGallery(ref, f.owner) }.code)
        index.close()
        IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.use { reopened ->
            val receipt = reopened.deleteGallery(ref, f.owner)
            assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
        }
        f.identity.close()
    }

    @Test fun stopCallbackRunsOutsideIndexMonitorAndRetainsWriterUntilReturn() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val lease = index.acquireIndexReadLease(builder.publish(), f.grant)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val registration = lease.registerStopHandler { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)) }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val closing = executor.submit { index.close() }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            executor.submit { lease.close() }.get(1, TimeUnit.SECONDS)
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.close() }.code)
            release.countDown(); closing.get(2, TimeUnit.SECONDS)
            IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.close()
        } finally { release.countDown(); lease.close(); registration.close(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun newBuilderCanExplicitlyRemoveMultipleRevokedMembers() {
        val f = fixture()
        val operation = AuthorizationGrant("operation", "operations", setOf(IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "audit")
        val secondGrant = AuthorizationGrant("second", "second-domain", f.grant.scope, "identity", 0, null, 0, "audit")
        val thirdGrant = AuthorizationGrant("third", "third-domain", f.grant.scope, "identity", 0, null, 0, "audit")
        fun enroll(grant: AuthorizationGrant): TemplateRef = requireNotNull(f.identity.enroll(
            IdentityProbe("fixture", listOf(1f, 2f)), f.identity.createCandidate(grant), grant,
            EnrollmentOptions(0.8, "quality", "1"), FixtureIdentityBackend.system()).templateRef)
        val second = enroll(secondGrant); val third = enroll(thirdGrant)
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        try {
            val builder = index.createGalleryBuilder(grant = operation, policy = f.policy)
            builder.addTemplate(f.template, f.grant); builder.addTemplate(second, secondGrant); builder.addTemplate(third, thirdGrant)
            val base = builder.publish()
            f.identity.revokeGrant(f.grant.grantId, f.owner); f.identity.revokeGrant(secondGrant.grantId, f.owner)
            assertFailsWith<IdentityError> { index.acquireIndexReadLease(base, operation) }
            val unsafe = index.createGalleryBuilder(base, operation, f.policy)
            assertFailsWith<IdentityError> { unsafe.publish() }
            assertEquals(IdentityErrorCode.BUILDER_CLOSED, assertFailsWith<IdentityError> { unsafe.publish() }.code)
            val cleanup = index.createGalleryBuilder(base, operation, f.policy)
            cleanup.removeTemplate(f.template, operation)
            cleanup.removeTemplate(second, operation)
            val revision = cleanup.publish()
            assertEquals(base.id, revision.id); assertNotEquals(base.revision, revision.revision)
            index.acquireIndexReadLease(revision, operation).use { assertEquals(listOf(third), it.snapshot().members.map { member -> member.ref }) }
            assertFailsWith<IdentityError> { index.acquireIndexReadLease(base, operation) }
        } finally { index.close(); f.identity.close() }
    }

    @Test fun newRevisionCanUpgradeVerifiedPolicyWithoutChangingThePinnedSpace() {
        val f = fixture(); val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        try {
            val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
            val base = builder.publish(); val upgraded = f.policy.copy(revision = "2", threshold = 0.6)
            val next = index.createGalleryBuilder(base, f.grant, upgraded).publish()
            index.acquireIndexReadLease(base, f.grant).use { assertEquals("1", it.snapshot().policy.revision) }
            index.acquireIndexReadLease(next, f.grant).use { assertEquals("2", it.snapshot().policy.revision); assertEquals(0.6, it.snapshot().policy.threshold) }
        } finally { index.close(); f.identity.close() }
    }

    @Test fun nativeBuildReceivesStopOnCloseAndTheWriterDrains() {
        val f = fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, signal ->
            val registration = signal.registerStopHandler { release.countDown() }
            try { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); signal.check(); byteArrayOf(1) }
            finally { registration.close() }
        }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val publishing = executor.submit<IdentityErrorCode?> { try { builder.publish(); null } catch (error: IdentityError) { error.code } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val closing = executor.submit { index.close() }
            assertEquals(IdentityErrorCode.CLOSED, publishing.get(1, TimeUnit.SECONDS)); closing.get(1, TimeUnit.SECONDS)
            IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }.close()
        } finally { release.countDown(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun readLeaseRetainsTheSnapshotOperationGrantAcrossQueryGrantChanges() {
        val f = fixture()
        val operation = AuthorizationGrant("operation-read", "operation-domain", setOf(IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "audit")
        val query = AuthorizationGrant("query-read", "query-domain", setOf(IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "audit")
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = operation, policy = f.policy)
        builder.addTemplate(f.template, f.grant); val ref = builder.publish()
        val lease = index.acquireIndexReadLease(ref, query)
        f.identity.revokeGrant(operation.grantId, f.owner)
        try {
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { lease.snapshot() }.code)
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { index.acquireIndexReadLease(ref, query) }.code)
        } finally { lease.close(); index.close(); f.identity.close() }
    }

    @Test fun reopenedRevisionCanBeExplicitlyRebuiltWithoutChangingItsReference() {
        val f = fixture()
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, signal -> signal.check(); byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish(); index.close()
        IndexStore(f.indexRoot, f.identity, f.keys) { _, signal -> signal.check(); byteArrayOf(9) }.use { reopened ->
            assertEquals(IdentityErrorCode.REBUILD_REQUIRED, assertFailsWith<IdentityError> { reopened.acquireIndexReadLease(ref, f.grant) }.code)
            assertEquals(ref, reopened.rebuildIndex(ref, f.grant))
            reopened.acquireIndexReadLease(ref, f.grant).use { assertEquals(ref, it.snapshot().ref) }
        }
        f.identity.close()
    }

    @Test fun readLeasePinsDerivedPayloadAcrossRebuildAndClearsOnRelease() {
        val f = fixture(); var builds = 0
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf((++builds).toByte()) }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish(); val old = index.acquireIndexReadLease(ref, f.grant)
        assertEquals(ref, index.rebuildIndex(ref, f.grant))
        val current = index.acquireIndexReadLease(ref, f.grant)
        assertEquals(listOf<Byte>(1), old.indexData().toList()); assertEquals(listOf<Byte>(2), current.indexData().toList())
        old.indexData()[0] = 99
        assertEquals(listOf<Byte>(1), old.indexData().toList())
        old.close(); assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { old.indexData() }.code)
        current.close(); index.close(); f.identity.close()
    }

    @Test fun failedRebuildPreservesThePreviouslyQueryableDerivedPayload() {
        val f = fixture(); var builds = 0
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ ->
            if (++builds > 1) throw IdentityError(IdentityErrorCode.INDEX_UNAVAILABLE)
            byteArrayOf(7)
        }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish()
        assertEquals(IdentityErrorCode.INDEX_UNAVAILABLE, assertFailsWith<IdentityError> { index.rebuildIndex(ref, f.grant) }.code)
        index.acquireIndexReadLease(ref, f.grant).use { assertEquals(listOf<Byte>(7), it.indexData().toList()) }
        index.close(); f.identity.close()
    }

    @Test fun deletionStopsRebuildAndConcurrentRebuildIsBusy() {
        val f = fixture(); var builds = 0
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, signal ->
            if (++builds > 1) {
                val registration = signal.registerStopHandler { release.countDown() }
                try { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); signal.check() }
                finally { registration.close() }
            }
            byteArrayOf(1)
        }
        val builder = index.createGalleryBuilder(grant = f.grant, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish(); val executor = Executors.newSingleThreadExecutor()
        try {
            val rebuilding = executor.submit<IdentityErrorCode?> { try { index.rebuildIndex(ref, f.grant); null } catch (error: IdentityError) { error.code } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> { index.rebuildIndex(ref, f.grant) }.code)
            index.deleteGallery(ref, f.owner)
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, rebuilding.get(1, TimeUnit.SECONDS))
            assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> { index.acquireIndexReadLease(ref, f.grant) }.code)
        } finally { release.countDown(); executor.shutdownNow(); index.close(); f.identity.close() }
    }

    @Test fun rebuildChecksRevokedSnapshotOriginBeforeEnteringBackend() {
        val f = fixture()
        val operation = AuthorizationGrant("operation-rebuild", "operation-rebuild-domain", setOf(IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "audit")
        val index = IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> byteArrayOf(1) }
        val builder = index.createGalleryBuilder(grant = operation, policy = f.policy); builder.addTemplate(f.template, f.grant)
        val ref = builder.publish(); index.close(); f.identity.revokeGrant(operation.grantId, f.owner)
        var built = false
        IndexStore(f.indexRoot, f.identity, f.keys) { _, _ -> built = true; byteArrayOf(1) }.use { reopened ->
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { reopened.rebuildIndex(ref, f.grant) }.code)
            assertTrue(!built)
        }
        f.identity.close()
    }

    private fun fixture(): IndexFixture {
        val keys = IndexKeys(); val authority = IndexAuthority()
        val identity = IdentityStore(root().toFile(), "identity", authority, keys,
            object : IdentityWallClock { override val nowMillis = 100L }, object : IdentityMonotonicClock { override val nowNanos = 1L })
        val grant = AuthorizationGrant("gallery", "domain", setOf(IdentityScope.ENROLLMENT, IdentityScope.IDENTIFICATION, IdentityScope.VERIFICATION), "identity", 0, null, 0, "audit")
        val candidate = identity.createCandidate(grant); val backend = FixtureIdentityBackend.system()
        val template = requireNotNull(identity.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant,
            EnrollmentOptions(0.8, "quality", "1"), backend).templateRef)
        val policy = ThresholdPolicy("identify", "1", template.embeddingSpaceId, "cosine", ScoreDirection.HIGHER_IS_BETTER,
            CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, 0.5, 0.8, "fixture", "fixture", 1..32, "one", 0)
        return IndexFixture(identity, keys, authority, grant, template, policy, OwnershipAuthorization(identity.storeId, "owner"), root())
    }
}
private data class IndexFixture(val identity: IdentityStore, val keys: IndexKeys, val authority: IndexAuthority,
    val grant: AuthorizationGrant, val template: TemplateRef, val policy: ThresholdPolicy, val owner: OwnershipAuthorization, val indexRoot: Path)
private class IndexAuthority : IdentityAuthorizationProvider {
    @Volatile var denyOwnership = false
    @Volatile var onEpoch: (() -> Unit)? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        val callback = onEpoch; onEpoch = null; callback?.invoke()
        return grant.authorizationEpoch
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {
        if (denyOwnership) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
    }
}
private class IndexKeys : IdentityKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()
    @Volatile var denied = false
    @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey {
        if (denied) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        keys[id]?.let { return it }
        if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        return SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES").also { keys[id] = it }
    }
    @Synchronized override fun deleteKey(id: String) { keys.remove(id) }
}

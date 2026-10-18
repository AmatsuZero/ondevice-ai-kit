package io.ondeviceai.identity

import android.app.Instrumentation
import android.os.Bundle
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

/** Device-only acceptance with synthetic fixtures and an isolated private root. */
internal class AndroidIdentityStoreProbeInstrumentation(
    private val instrumentation: Instrumentation,
    private val arguments: Bundle
) {
    fun run() {
        val test = Bundle().apply {
            putInt("numtests", 1); putInt("current", 1)
            putString("id", "AndroidIdentityStoreProbe")
            putString("class", AndroidKeystoreProbeInstrumentation::class.java.name)
            putString("test", "identityStoreLifecycle")
        }
        instrumentation.sendStatus(1, test)
        val result = Bundle()
        val context = instrumentation.targetContext
        val namespace = "store-probe-${UUID.randomUUID()}"
        val storeName = "probe-${UUID.randomUUID()}"
        val root = File(context.noBackupFilesDir, "ondevice-identity-$storeName")
        val authority = StoreProbeAuthority()
        val createdIds = linkedSetOf<String>()
        fun keys() = StoreProbeKeys(AndroidKeystoreIdentityKeyProvider(namespace), createdIds)
        var store: IdentityStore? = null
        var lease: IdentityTemplateLease? = null
        var code = -2
        try {
            fun open(): IdentityStore = IdentityStore.open(context, storeName, "probe", authority, keys()).also { store = it }
            fun grant(id: String, epoch: Long) = AuthorizationGrant(id, "synthetic-domain", setOf(
                IdentityScope.ENROLLMENT, IdentityScope.VERIFICATION, IdentityScope.IDENTIFICATION
            ), "probe", 0, null, epoch, "synthetic-evidence")
            val backend = FixtureIdentityBackend.system()
            fun enroll(active: IdentityStore, candidate: CandidateRef, grant: AuthorizationGrant) = requireNotNull(active.enroll(
                IdentityProbe("synthetic-fixture", listOf(1f, 2f)), candidate, grant,
                EnrollmentOptions(0.8, "synthetic-quality", "fixture-1"), backend
            ).templateRef)

            val first = open()
            check(root.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile) { "root escaped noBackupFilesDir" }
            result.putBoolean("noBackupRoot", true)
            val originalGrant = grant("initial", 0)
            val candidate = first.createCandidate(originalGrant)
            val template = enroll(first, candidate, originalGrant)
            val payload = File(root, "identity-template-${template.id}.bin")
            check(File(root, "identity-journal-store.bin").isFile && payload.isFile) { "encrypted files missing" }
            check(arguments.getString("forceFailure") != "true") { "forced Store probe failure after persistence" }
            val id = first.storeId
            first.close(); store = null

            // Reconstruct both provider and Store; this is same-process recovery.
            val recovered = open()
            check(recovered.storeId == id && recovered.recordedTemplateCandidate(template) == candidate) { "membership did not persist" }
            lease = recovered.acquireTemplateLease(template, originalGrant, IdentityScope.VERIFICATION, template.embeddingSpaceId)
            check(lease.embedding.values == listOf(1f, 2f)) { "recovered fixture mismatch" }
            val owner = OwnershipAuthorization(id, "synthetic-owner")
            val receipt = recovered.deleteTemplate(template, owner)
            check(recovered.awaitDeletion(receipt) == DeletionStatus.PURGE_PENDING && payload.isFile) { "lease did not pin payload" }
            lease.close(); lease = null
            check(recovered.awaitDeletion(receipt) == DeletionStatus.PURGED && !payload.exists()) { "release did not purge" }
            check(recovered.deleteTemplate(template, owner) == receipt) { "repeat deletion changed receipt" }
            val templateKey = "template.$id.${template.id}"
            expectCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { keys().key(templateKey, false) }
            recovered.close(); store = null
            val tombstones = open()
            check(tombstones.awaitDeletion(receipt) == DeletionStatus.PURGED) { "receipt did not persist" }
            expectCode(IdentityErrorCode.TEMPLATE_REVOKED) { tombstones.recordedTemplateCandidate(template) }
            result.putBoolean("templateLifecycle", true)

            val domainTemplate = enroll(tombstones, candidate, originalGrant)
            lease = tombstones.acquireTemplateLease(domainTemplate, originalGrant, IdentityScope.VERIFICATION, domainTemplate.embeddingSpaceId)
            val revocation = tombstones.revokeGrant(originalGrant.grantId, owner)
            check(tombstones.awaitDeletion(revocation) == DeletionStatus.PURGE_PENDING) { "domain lease not pending" }
            lease.close(); lease = null
            check(tombstones.awaitDeletion(revocation) == DeletionStatus.PURGED) { "domain not purged" }
            tombstones.close(); store = null
            val revoked = open()
            check(revoked.awaitDeletion(revocation) == DeletionStatus.PURGED) { "domain receipt did not persist" }
            expectCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { revoked.createCandidate(grant("stale-new-id", 0)) }
            expectCode(IdentityErrorCode.CANDIDATE_REVOKED) { revoked.recordedCandidateGrant(candidate) }
            authority.epoch = 1
            val fresh = revoked.createCandidate(grant("fresh", 1))
            check(fresh.id != candidate.id) { "reauthorization reused candidate" }
            expectCode(IdentityErrorCode.TEMPLATE_REVOKED) { revoked.recordedTemplateCandidate(domainTemplate) }
            revoked.close(); store = null
            result.putBoolean("persistedRevocation", true)
            result.putInt("managedKeyCount", createdIds.size)
            result.putString("probeStatus", "pass")
            code = -1
        } catch (error: Throwable) {
            result.putString("probeStatus", "fail")
            result.putString("probeError", error.toString())
        } finally {
            // Attempt every exact cleanup even if an earlier close/delete fails.
            val errors = mutableListOf<String>()
            fun cleanup(action: () -> Unit) { try { action() } catch (error: Throwable) { errors += error.toString() } }
            cleanup { lease?.close() }
            cleanup { store?.close() }
            for (id in createdIds) cleanup {
                keys().deleteKey(id)
                expectCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { keys().key(id, false) }
            }
            cleanup { root.walkBottomUp().forEach { if (it.exists()) check(it.delete()) { "private root cleanup failed" } } }
            cleanup { check(!root.exists()) { "private root survived cleanup" } }
            if (errors.isNotEmpty()) {
                result.putString("probeStatus", "fail")
                result.putString("cleanupError", errors.joinToString("; "))
                code = -2
            } else result.putString("cleanup", "pass; exact private root and tracked key IDs only")
            instrumentation.sendStatus(if (code == -1) 0 else -2, Bundle(test).apply { putString("stream", result.getString("probeStatus")) })
            instrumentation.finish(code, result)
        }
    }
}

private class StoreProbeAuthority : IdentityAuthorizationProvider {
    var epoch = 0L
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long) = epoch
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) = Unit
}

private class StoreProbeKeys(private val delegate: IdentityKeyProvider, private val createdIds: MutableSet<String>) : IdentityKeyProvider {
    override fun key(id: String, createIfMissing: Boolean): SecretKey {
        if (createIfMissing) createdIds += id
        return delegate.key(id, createIfMissing)
    }
    override fun deleteKey(id: String) = delegate.deleteKey(id)
}

private fun expectCode(code: IdentityErrorCode, action: () -> Any?) {
    val error = runCatching(action).exceptionOrNull()
    check((error as? IdentityError)?.code == code) { "expected $code, got ${error?.javaClass?.simpleName ?: "success"}" }
}

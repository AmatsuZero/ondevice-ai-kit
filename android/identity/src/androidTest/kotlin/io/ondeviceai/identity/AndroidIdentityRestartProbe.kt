package io.ondeviceai.identity

import android.app.Instrumentation
import android.os.Bundle
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.crypto.SecretKey

/** Device-only process-death acceptance probe. The controller, never this probe, kills ready stages. */
internal class AndroidIdentityRestartProbe(
    private val instrumentation: Instrumentation,
    private val arguments: Bundle
) {
    fun run() {
        // Keep the instrumentation main thread available while a ready checkpoint is held open.
        Thread({ runStage() }, "identity-restart-probe").start()
    }

    private fun runStage() {
        val runID = arguments.getString("runID") ?: arguments.getString("run-id") ?: ""
        val stage = arguments.getString("restartStage") ?: arguments.getString("restart-stage") ?: ""
        val launchNonce = arguments.getString("launchNonce") ?: arguments.getString("launch-nonce") ?: ""
        if (!UUID_PATTERN.matches(runID) || !UUID_PATTERN.matches(launchNonce) || stage !in STAGES) {
            writeReport(runID, stage, launchNonce, "fail", emptyMap(), "invalid runID, launch nonce or stage")
            instrumentation.finish(-2, Bundle().apply { putString("probeStatus", "fail") })
            return
        }
        val context = instrumentation.targetContext
        val storeName = "restart-$runID"
        val root = File(context.noBackupFilesDir, "ondevice-identity-$storeName")
        val state = File(root, "restart-state.json")
        val ledger = File(root, "restart-keys.json")
        var store: IdentityStore? = null
        var lease: IdentityTemplateLease? = null
        try {
            writeReport(runID, stage, launchNonce, "running", emptyMap())
            if (stage == "create") {
                check(!root.exists()) { "restart root already exists" }
                check(root.mkdir()) { "cannot create restart root" }
            }
            val keys = RestartKeys("restart-$runID", ledger)
            if (stage == "cleanup") {
                val errors = mutableListOf<Throwable>()
                fun attempt(action: () -> Unit) {
                    try { action() } catch (error: Throwable) { errors += error }
                }
                val ids = try { keys.ids() } catch (error: Throwable) { errors += error; emptyList() }
                ids.forEach { id -> attempt {
                    keys.deleteKey(id)
                    expectCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { keys.key(id, false) }
                } }
                root.walkBottomUp().forEach { file -> attempt {
                    if (file.exists()) check(file.delete()) { "restart root cleanup failed" }
                } }
                attempt { check(!root.exists()) { "restart root survived cleanup" } }
                check(errors.isEmpty()) { "restart cleanup failed (${errors.size} exact operations)" }
                writeReport(runID, stage, launchNonce, "pass", mapOf("cleanup" to true))
                instrumentation.finish(-1, Bundle().apply { putString("probeStatus", "pass") })
                return
            }
            val authority = RestartAuthority()
            val opened = IdentityStore.open(context, storeName, "probe", authority, keys)
            store = opened
            val grant = AuthorizationGrant("restart", "restart.domain", setOf(IdentityScope.ENROLLMENT, IdentityScope.VERIFICATION),
                "probe", 0, null, 0, "synthetic")
            if (stage == "create") {
                val candidate = opened.createCandidate(grant)
                val template = requireNotNull(opened.enroll(IdentityProbe("synthetic-fixture", listOf(1f, 2f)), candidate, grant,
                    EnrollmentOptions(0.8, "synthetic-quality", "fixture-1"), FixtureIdentityBackend.system()).templateRef)
                writeState(state, opened.storeId, candidate.canonicalJSON(), template.canonicalJSON(), null)
                writeReport(runID, stage, launchNonce, "ready", mapOf("enrollmentCommitted" to true, "writerStillOpen" to true))
            } else {
                val checkpoint = readState(state)
                check(opened.storeId == checkpoint.storeId) { "store changed after restart" }
                val candidate = CandidateRef.decodeJSON(checkpoint.candidate)
                val template = TemplateRef.decodeJSON(checkpoint.template)
                val payload = File(root, "identity-template-${template.id}.bin")
                if (stage == "delete") {
                    check(opened.recordedTemplateCandidate(template) == candidate) { "membership did not recover" }
                    val held = opened.acquireTemplateLease(template, grant, IdentityScope.VERIFICATION, template.embeddingSpaceId)
                    lease = held
                    check(held.embedding.values == listOf(1f, 2f)) { "fixture decrypt mismatch" }
                    val receipt = opened.deleteTemplate(template, OwnershipAuthorization(opened.storeId, "owner"))
                    check(opened.awaitDeletion(receipt) == DeletionStatus.PURGE_PENDING && payload.isFile) { "delete was not pending" }
                    writeState(state, checkpoint.storeId, checkpoint.candidate, checkpoint.template, receipt.canonicalJSON())
                    writeReport(runID, stage, launchNonce, "ready", mapOf("membershipRecovered" to true, "decryptRecovered" to true,
                        "deletionCommitted" to true, "leaseHeld" to true, "payloadPending" to true))
                } else {
                    val receipt = DeletionReceipt.decodeJSON(requireNotNull(checkpoint.receipt) { "missing receipt" })
                    check(opened.awaitDeletion(receipt) == DeletionStatus.PURGED && !payload.exists()) { "purge did not recover" }
                    expectCode(IdentityErrorCode.TEMPLATE_REVOKED) { opened.recordedTemplateCandidate(template) }
                    expectCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { keys.key("template.${opened.storeId}.${template.id}", false) }
                    check(opened.deleteTemplate(template, OwnershipAuthorization(opened.storeId, "owner")) == receipt) { "receipt changed" }
                    opened.close(); store = null
                    writeReport(runID, stage, launchNonce, "pass", mapOf("tombstoneRecovered" to true, "receiptStable" to true,
                        "payloadPurged" to true, "templateKeyPurged" to true, "closed" to true))
                    instrumentation.finish(-1, Bundle().apply { putString("probeStatus", "pass") })
                    return
                }
            }
            // A successful ready checkpoint deliberately does not close or finish. The external
            // controller must verify this PID and SIGKILL it; use references after every wait.
            while (true) {
                Thread.sleep(1_000)
                check(store != null)
                lease?.embedding
            }
        } catch (error: Exception) {
            try { lease?.close() } catch (_: Exception) { }
            try { store?.close() } catch (_: Exception) { }
            writeReport(runID, stage, launchNonce, "fail", emptyMap(), error.toString())
            instrumentation.finish(-2, Bundle().apply { putString("probeStatus", "fail"); putString("probeError", error.toString()) })
        }
    }

    private fun writeReport(runID: String, stage: String, launchNonce: String, status: String,
                            flags: Map<String, Boolean>, error: String? = null) {
        val report = JSONObject().put("probe", "identityRestart").put("platform", "android")
            .put("runID", runID).put("stage", stage).put("launchNonce", launchNonce)
            .put("pid", Process.myPid()).put("status", status).put("flags", JSONObject(flags))
        error?.let { report.put("error", it) }
        atomicWriteRestartFile(File(instrumentation.targetContext.filesDir, REPORT), report.toString())
    }

    private fun writeState(file: File, storeId: String, candidate: String, template: String, receipt: String?) {
        atomicWriteRestartFile(file, JSONObject().put("storeId", storeId).put("candidate", candidate)
            .put("template", template).put("receipt", receipt).toString())
    }
    private fun readState(file: File): RestartState {
        val json = JSONObject(file.readText())
        val receipt = if (json.isNull("receipt")) null else json.getString("receipt")
        return RestartState(json.getString("storeId"), json.getString("candidate"), json.getString("template"), receipt)
    }

    private data class RestartState(val storeId: String, val candidate: String, val template: String, val receipt: String?)
    private class RestartAuthority : IdentityAuthorizationProvider {
        override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long) = 0L
        override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) = Unit
    }
    /** Persists only exact owned key IDs before creation, never key material or embeddings. */
    private class RestartKeys(namespace: String, private val ledger: File) : IdentityKeyProvider {
        private val delegate = AndroidKeystoreIdentityKeyProvider(namespace)
        @Synchronized fun ids(): List<String> = if (ledger.isFile) JSONArray(ledger.readText()).let { array -> List(array.length()) { array.getString(it) } } else emptyList()
        @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey {
            if (createIfMissing && id !in ids()) atomicWriteRestartFile(ledger, JSONArray((ids() + id).sorted()).toString())
            return delegate.key(id, createIfMissing)
        }
        override fun deleteKey(id: String) = delegate.deleteKey(id)
    }
    companion object {
        private const val REPORT = "identity-restart-probe.json"
        private val STAGES = setOf("create", "delete", "recover", "cleanup")
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

private fun atomicWriteRestartFile(file: File, text: String) {
    file.parentFile?.mkdirs()
    val temporary = File.createTempFile(".${file.name}.", ".tmp", file.parentFile)
    try {
        FileOutputStream(temporary).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}

private fun expectCode(code: IdentityErrorCode, action: () -> Any?) {
    val error = runCatching(action).exceptionOrNull()
    check((error as? IdentityError)?.code == code) {
        "expected $code, got ${error?.javaClass?.simpleName ?: "success"}"
    }
}

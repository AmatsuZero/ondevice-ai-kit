package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import io.ondeviceai.identity.CandidateRef
import io.ondeviceai.identity.DeletionReceipt
import io.ondeviceai.identity.DeletionStatus
import io.ondeviceai.identity.EnrollmentOptions
import io.ondeviceai.identity.EnrollmentOutcome
import io.ondeviceai.identity.FaceVerification
import io.ondeviceai.identity.IdentityDeadline
import io.ondeviceai.identity.IdentityError
import io.ondeviceai.identity.IdentityErrorCode
import io.ondeviceai.identity.IdentityKeyProvider
import io.ondeviceai.identity.IdentityPolicyKind
import io.ondeviceai.identity.IdentityProbe
import io.ondeviceai.identity.IdentityStore
import io.ondeviceai.identity.OwnershipAuthorization
import io.ondeviceai.identity.SystemIdentityMonotonicClock
import io.ondeviceai.identity.TemplateRef
import io.ondeviceai.identity.VerificationOptions
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.opencvidentity.OpenCvIdentityBackend
import io.ondeviceai.opencvidentity.OpenCvIdentityModel
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android Identity generic SDK functional smoke probe for caller-approved upright PNG inputs.
 * Validates functional pipeline without biometric evaluation, accuracy scoring, or threshold decisions.
 */
class IdentityFunctionalInstrumentation : Instrumentation() {
    private lateinit var runId: String
    private lateinit var expectedSdkCommit: String
    private lateinit var manifestSHA256: String
    private lateinit var reportFile: AtomicFile
    private lateinit var report: JSONObject
    private val rows = JSONArray()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val rId = requireNotNull(arguments?.getString("runId"))
        require(rId.matches(Regex("[A-Za-z0-9-]{1,40}")))
        val expCommit = requireNotNull(arguments?.getString("expectedSdkCommit"))
        require(expCommit.matches(Regex("[0-9a-f]{40}")))
        val mSha = requireNotNull(arguments?.getString("manifestSHA256"))
        require(mSha.matches(Regex("[0-9a-f]{64}")))
        require(BuildConfig.ODAI_SDK_COMMIT == expCommit)

        runId = rId
        expectedSdkCommit = expCommit
        manifestSHA256 = mSha

        val directory = File(targetContext.filesDir, "odai-functional-reports")
        if (!exists(directory)) check(directory.mkdir())
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))

        val file = File(directory, "$runId.json")
        for (suffix in listOf("", ".new", ".bak")) {
            check(!exists(File(file.path + suffix)))
        }
        check(!exists(workDirectory()))

        Files.createFile(file.toPath())
        reportFile = AtomicFile(file)

        report = JSONObject()
            .put("reportVersion", 1)
            .put("kind", "android-identity-functional")
            .put("runId", runId)
            .put("sdkCommit", BuildConfig.ODAI_SDK_COMMIT)
            .put("modelRevision", OpenCvIdentityModel.ref.revision)
            .put("backendId", OpenCvIdentityModel.BACKEND_ID)
            .put("preprocessRevision", OpenCvIdentityModel.PREPROCESS)
            .put("manifestSHA256", manifestSHA256)
            .put("purpose", "local-functional-test-only")
            .put("calibrationAllowed", false)
            .put("thresholdPolicyStatus", "UNVERIFIED")
            .put("cameraStarts", 0)
            .put("audioStarts", 0)
            .put("rawEmbeddingsRecorded", false)
            .put("rows", rows)
            .put("terminal", false)
            .put("status", "running")
            .put("inputCount", 0)
            .put("processedCount", 0)
            .put("usableCount", 0)
            .put("enrolledCount", 0)
            .put("deletedCount", 0)
            .put("unverifiedRefusalCount", 0)
            .put("backendClosed", false)
            .put("modelLeaseReleased", false)
            .put("workDirectoryRemoved", false)
            .put("keysCleared", false)
            .put("keepScreenOnRestored", false)

        persist()
        start()
    }

    override fun onStart() {
        val work = workDirectory()
        var ownsWork = false
        var activity: MainActivity? = null
        var originalKeepScreenOn = false
        var originalKeepScreenOnCaptured = false
        var modelStore: ModelStore? = null
        var backend: OpenCvIdentityBackend? = null
        var keyProvider: InMemoryIdentityKeyProvider? = null
        var consent: PrototypeIdentityConsent? = null
        var store: IdentityStore? = null
        var verification: FaceVerification? = null
        var ownership: OwnershipAuthorization? = null
        var grantAdmitted = false

        var processedCount = 0
        var usableCount = 0
        var enrolledCount = 0
        var deletedCount = 0
        var unverifiedRefusalCount = 0

        try {
            check(!exists(work) && work.mkdir())
            ownsWork = true

            val inputsParent = File(targetContext.filesDir, "odai-functional-inputs")
            check(inputsParent.isDirectory && !Files.isSymbolicLink(inputsParent.toPath()))

            val inputRoot = File(inputsParent, runId)
            check(inputRoot.isDirectory && !Files.isSymbolicLink(inputRoot.toPath()))

            val manifestFile = File(inputRoot, "manifest.json")
            check(Files.isRegularFile(manifestFile.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(!Files.isSymbolicLink(manifestFile.toPath()))

            val manifestLength = manifestFile.length()
            check(manifestLength in 1L..1048576L)

            val manifestBytes = manifestFile.readBytes()
            check(manifestBytes.size.toLong() == manifestLength)
            check(digest(manifestBytes) == manifestSHA256)

            val manifestJson = JSONObject(String(manifestBytes, Charsets.UTF_8))
            check(manifestJson.keys().asSequence().toSet() == setOf("schemaVersion", "purpose", "entries"))

            val schemaVersionVal = manifestJson.get("schemaVersion")
            check(schemaVersionVal is Int && schemaVersionVal == 1)

            val purposeVal = manifestJson.get("purpose")
            check(purposeVal is String && purposeVal == "local-functional-test-only")

            val entriesVal = manifestJson.get("entries")
            check(entriesVal is JSONArray)
            val entryCount = entriesVal.length()
            check(entryCount in 1..500)
            report.put("inputCount", entryCount)
            persist()

            data class EntrySpec(
                val id: Int,
                val file: File,
                val sha256: String,
                val bytes: Int,
                val width: Int,
                val height: Int
            )

            val entrySpecs = ArrayList<EntrySpec>(entryCount)
            val allowedEntryKeys = setOf("id", "filename", "sha256", "bytes", "width", "height")
            val sha256Regex = Regex("[0-9a-f]{64}")

            for (i in 0 until entryCount) {
                val entryRaw = entriesVal.get(i)
                check(entryRaw is JSONObject)
                check(entryRaw.keys().asSequence().toSet() == allowedEntryKeys)

                val idVal = entryRaw.get("id")
                check(idVal is Int && idVal == i)

                val expectedFilename = String.format(Locale.ROOT, "input-%04d.png", i)
                val filenameVal = entryRaw.get("filename")
                check(filenameVal is String && filenameVal == expectedFilename)

                val shaVal = entryRaw.get("sha256")
                check(shaVal is String && shaVal.matches(sha256Regex))

                val bytesVal = entryRaw.get("bytes")
                check(bytesVal is Int && bytesVal in 1..10485760)

                val widthVal = entryRaw.get("width")
                check(widthVal is Int && widthVal in 16..2048)

                val heightVal = entryRaw.get("height")
                check(heightVal is Int && heightVal in 16..2048)
                check(widthVal.toLong() * heightVal.toLong() <= 4194304L)

                val inputFile = File(inputRoot, expectedFilename)
                check(inputFile.parentFile?.canonicalFile == inputRoot.canonicalFile)
                check(Files.isRegularFile(inputFile.toPath(), LinkOption.NOFOLLOW_LINKS))
                check(!Files.isSymbolicLink(inputFile.toPath()))
                check(inputFile.length() == bytesVal.toLong())

                val precheckBytes = inputFile.readBytes()
                try {
                    check(digest(precheckBytes) == shaVal)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(precheckBytes, 0, precheckBytes.size, bounds)
                    check(bounds.outWidth == widthVal && bounds.outHeight == heightVal)
                } finally {
                    precheckBytes.fill(0)
                }

                entrySpecs.add(EntrySpec(i, inputFile, shaVal, bytesVal, widthVal, heightVal))
            }

            val sourceModels = File(targetContext.filesDir, "identity-validation/models")
            verifyModels(sourceModels)

            val act = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = act
            waitForIdleSync()

            val keepScreenOnError = AtomicReference<Throwable?>(null)
            runOnMainSync {
                try {
                    val flags = act.window.attributes.flags
                    originalKeepScreenOn = (flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
                    originalKeepScreenOnCaptured = true
                    if (!originalKeepScreenOn) {
                        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } catch (t: Throwable) {
                    keepScreenOnError.set(t)
                }
            }
            keepScreenOnError.get()?.let { throw it }
            checkForeground(act)

            val modelsWorkDir = File(work, "models")
            check(!exists(modelsWorkDir) && modelsWorkDir.mkdir())
            val models = ModelStore(
                modelsWorkDir,
                ModelCompatibility(
                    OpenCvIdentityModel.BACKEND_ID,
                    OpenCvIdentityModel.VERSION,
                    Build.VERSION.SDK_INT.toString(),
                    Build.SUPPORTED_ABIS.first()
                )
            )
            modelStore = models

            runBlocking {
                models.install(OpenCvIdentityModel.manifest(), sourceModels)
                check(models.status(OpenCvIdentityModel.ref) == ModelStatus.INSTALLED)
            }

            val bEnd = runBlocking { OpenCvIdentityBackend.open(models) }
            backend = bEnd

            val identityBase = File(work, "identity-base")
            check(!exists(identityBase) && identityBase.mkdir())

            val storeContext = object : ContextWrapper(targetContext) {
                override fun getNoBackupFilesDir(): File = identityBase
            }

            val keys = InMemoryIdentityKeyProvider()
            keyProvider = keys

            val protoConsent = PrototypeIdentityConsent(System.currentTimeMillis())
            consent = protoConsent

            val idStore = IdentityStore.open(
                context = storeContext,
                storeName = "functional-$runId",
                purpose = "local-identity-prototype",
                authorizationProvider = protoConsent,
                keyProvider = keys
            )
            store = idStore
            protoConsent.bindStore(idStore.storeId)
            val ownerAuth = protoConsent.ownership()
            ownership = ownerAuth

            val verifier = FaceVerification(
                identityStore = idStore,
                defaultBackendId = bEnd.backendId,
                backends = listOf(bEnd)
            )
            verification = verifier

            for (entry in entrySpecs) {
                checkForeground(act)

                val inputBytes = entry.file.readBytes()
                try {
                    check(digest(inputBytes) == entry.sha256)

                    val probe = IdentityProbe(inputBytes, "functional-upright-png-v1")
                    val quality = bEnd.evaluateQuality(probe)
                    val faceCount = quality.faceCount
                    val geometricallyUsable = (faceCount == 1 && quality.score == 1.0)

                    if (geometricallyUsable) {
                        usableCount++

                        val embedding = bEnd.embed(probe)
                        check(embedding.values.size == 128)
                        check(embedding.values.all { it.isFinite() })
                        val norm = embedding.values.sumOf { it.toDouble() * it.toDouble() }
                        check(kotlin.math.abs(norm - 1.0) <= 1e-4)

                        val candidate = idStore.createCandidate(protoConsent.grant)
                        grantAdmitted = true

                        val enrollOptions = EnrollmentOptions(1.0, "geometric-only-uncalibrated", "v1")
                        val enrollResult = idStore.enroll(probe, candidate, protoConsent.grant, enrollOptions, bEnd)
                        check(enrollResult.outcome == EnrollmentOutcome.ENROLLED)
                        val templateRef = requireNotNull(enrollResult.templateRef)
                        enrolledCount++

                        var unverifiedRefused = false
                        var unverifiedErrorCode: String? = null
                        try {
                            val task = verifier.verify(
                                probe = probe,
                                templateRef = templateRef,
                                grant = protoConsent.grant,
                                options = VerificationOptions(OpenCvIdentityModel.unverifiedPolicy(IdentityPolicyKind.VERIFICATION), "v1")
                            )
                            try {
                                task.result()
                            } catch (error: IdentityError) {
                                if (error.code == IdentityErrorCode.THRESHOLD_UNVERIFIED) {
                                    unverifiedRefused = true
                                    unverifiedErrorCode = error.code.name
                                } else {
                                    throw error
                                }
                            } finally {
                                task.close()
                            }
                        } catch (error: IdentityError) {
                            if (error.code == IdentityErrorCode.THRESHOLD_UNVERIFIED) {
                                unverifiedRefused = true
                                unverifiedErrorCode = error.code.name
                            } else {
                                throw error
                            }
                        }
                        check(unverifiedRefused)
                        check(unverifiedErrorCode == IdentityErrorCode.THRESHOLD_UNVERIFIED.name)
                        unverifiedRefusalCount++

                        val deleteReceipt = idStore.deleteCandidate(candidate, ownerAuth)
                        val deletionStatus = idStore.awaitDeletion(deleteReceipt, IdentityDeadline(SystemIdentityMonotonicClock, 30_000_000_000L))
                        check(deletionStatus == DeletionStatus.PURGED)
                        deletedCount++

                        val row = JSONObject()
                            .put("index", entry.id)
                            .put("faceCount", faceCount)
                            .put("geometricallyUsable", true)
                            .put("embeddingDimension", embedding.values.size)
                            .put("embeddingNormalized", true)
                            .put("enrolled", true)
                            .put("unverifiedRefusal", true)
                            .put("verificationErrorCode", requireNotNull(unverifiedErrorCode))
                            .put("purged", true)
                            .put("outcome", "eligible-processed")
                        rows.put(row)
                    } else {
                        val row = JSONObject()
                            .put("index", entry.id)
                            .put("faceCount", faceCount)
                            .put("geometricallyUsable", false)
                            .put("outcome", "not-eligible")
                        rows.put(row)
                    }

                    check(digest(inputBytes) == entry.sha256)
                    check(digest(entry.file) == entry.sha256)
                } finally {
                    inputBytes.fill(0)
                }

                checkForeground(act)

                processedCount++
                report.put("processedCount", processedCount)
                report.put("usableCount", usableCount)
                report.put("enrolledCount", enrolledCount)
                report.put("deletedCount", deletedCount)
                report.put("unverifiedRefusalCount", unverifiedRefusalCount)
                persist()
            }

            check(digest(manifestFile) == manifestSHA256)
            check(processedCount == entryCount)
            check(usableCount == enrolledCount)
            check(usableCount == deletedCount)
            check(usableCount == unverifiedRefusalCount)
            report.put("status", "completed")
        } catch (error: Throwable) {
            recordFailure("execution", error)
        } finally {
            var cleanupFailed = false

            try {
                verification?.close()
            } catch (error: Throwable) {
                cleanupFailed = true
                recordFailure("cleanup-verification", error)
            }

            if (grantAdmitted && store != null && consent != null && ownership != null) {
                try {
                    val receipt = store.revokeGrant(consent.grant.grantId, ownership)
                    val purgeStatus = store.awaitDeletion(receipt, IdentityDeadline(SystemIdentityMonotonicClock, 30_000_000_000L))
                    check(purgeStatus == DeletionStatus.PURGED)
                } catch (error: Throwable) {
                    cleanupFailed = true
                    recordFailure("cleanup-revoke-grant", error)
                }
            }

            try {
                store?.close()
            } catch (error: Throwable) {
                cleanupFailed = true
                recordFailure("cleanup-store", error)
            }

            try {
                consent?.close()
            } catch (error: Throwable) {
                cleanupFailed = true
                recordFailure("cleanup-consent", error)
            }

            try {
                runBlocking { backend?.closeAndWait() }
                if (backend != null) {
                    report.put("backendClosed", true)
                }
            } catch (error: Throwable) {
                cleanupFailed = true
                recordFailure("cleanup-backend", error)
            }

            try {
                modelStore?.let { models ->
                    runBlocking {
                        models.remove(OpenCvIdentityModel.ref)
                        check(models.status(OpenCvIdentityModel.ref) == ModelStatus.ABSENT)
                    }
                    report.put("modelLeaseReleased", true)
                }
            } catch (error: Throwable) {
                cleanupFailed = true
                recordFailure("cleanup-model-lease", error)
            }

            if (ownsWork && exists(work)) {
                try {
                    val workPath = work.toPath().toRealPath()
                    Files.walk(workPath).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { path ->
                            if (!Files.isSymbolicLink(path) && path.toRealPath().startsWith(workPath)) {
                                Files.delete(path)
                            }
                        }
                    }
                    check(!exists(work))
                    report.put("workDirectoryRemoved", true)
                } catch (error: Throwable) {
                    cleanupFailed = true
                    recordFailure("cleanup-work-directory", error)
                }
            }

            try {
                keyProvider?.clear()
                report.put("keysCleared", true)
            } catch (error: Throwable) {
                cleanupFailed = true
                recordFailure("cleanup-keys", error)
            }

            activity?.let { act ->
                val cleanupError = AtomicReference<Throwable?>(null)
                try {
                    runOnMainSync {
                        try {
                            check(!act.isFinishing && !act.isDestroyed)
                            if (!originalKeepScreenOnCaptured) {
                                throw IllegalStateException("keep-screen-on original state not captured; restore skipped")
                            }
                            if (!originalKeepScreenOn) {
                                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                            val currentKeepScreenOn = (act.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
                            check(currentKeepScreenOn == originalKeepScreenOn)
                        } catch (error: Throwable) {
                            cleanupError.set(error)
                        }
                    }
                    cleanupError.get()?.let { throw it }
                    report.put("keepScreenOnRestored", true)
                } catch (error: Throwable) {
                    cleanupFailed = true
                    recordFailure("cleanup-keep-screen-on", error)
                }
            }

            if (cleanupFailed) {
                report.put("status", "failed")
            }
        }

        var finalStatus: String
        var reportPersisted = false
        try {
            report.put("terminal", true)
            persist()
            reportPersisted = true
            finalStatus = report.getString("status")
        } catch (_: Throwable) {
            finalStatus = "failed"
        }
        val success = finalStatus == "completed" && reportPersisted
        finish(
            if (success) -1 else 1,
            Bundle().apply {
                putString("status", finalStatus)
                putBoolean("reportPersisted", reportPersisted)
            }
        )
    }

    private fun checkForeground(activity: MainActivity) {
        val resumed = AtomicBoolean(false)
        val errorRef = AtomicReference<Throwable?>(null)
        runOnMainSync {
            try {
                resumed.set(
                    !activity.isFinishing &&
                    !activity.isDestroyed &&
                    activity.lifecycle.currentState == Lifecycle.State.RESUMED
                )
            } catch (t: Throwable) {
                errorRef.set(t)
            }
        }
        errorRef.get()?.let { throw it }
        check(resumed.get())
    }

    private fun recordFailure(stage: String, error: Throwable) {
        report.put("status", "failed")
        report.put("failureStage", stage)
        if (error is IdentityError) {
            report.put("failureErrorCode", error.code.wire)
        } else {
            report.put("failureErrorClass", error.javaClass.name)
        }
    }

    private fun workDirectory() = File(targetContext.cacheDir, "odai-functional-work-$runId")

    private fun exists(file: File) = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun verifyModels(directory: File) {
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        val manifest = OpenCvIdentityModel.manifest()
        check(directory.list()?.toSet() == manifest.files.map { it.path }.toSet())
        manifest.files.forEach { model ->
            val file = File(directory, model.path)
            check(file.length() == model.bytes && digest(file) == model.sha256)
        }
    }

    private fun digest(file: File): String {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun digest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun persist() {
        val stream = reportFile.startWrite()
        try {
            stream.write((report.toString() + "\n").toByteArray(Charsets.UTF_8))
            reportFile.finishWrite(stream)
        } catch (error: Throwable) {
            reportFile.failWrite(stream)
            throw error
        }
    }

    private class InMemoryIdentityKeyProvider : IdentityKeyProvider {
        private val keys = mutableMapOf<String, ByteArray>()
        private val random = SecureRandom()

        @Synchronized
        override fun key(id: String, createIfMissing: Boolean): SecretKey {
            if (!Regex("[A-Za-z0-9._:-]{1,256}").matches(id)) {
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
            var raw = keys[id]
            if (raw == null) {
                if (!createIfMissing) {
                    throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
                }
                val generated = ByteArray(32)
                random.nextBytes(generated)
                keys[id] = generated
                raw = generated
            }
            return SecretKeySpec(raw, "AES")
        }

        @Synchronized
        override fun deleteKey(id: String) {
            if (!Regex("[A-Za-z0-9._:-]{1,256}").matches(id)) {
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            }
            val removed = keys.remove(id)
            removed?.fill(0)
        }

        @Synchronized
        fun clear() {
            for (keyBytes in keys.values) {
                keyBytes.fill(0)
            }
            keys.clear()
        }
    }
}

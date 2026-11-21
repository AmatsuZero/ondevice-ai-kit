// IdentityParityInstrumentation.kt
// CANDIDATE / REVIEW-ONLY. Authored here; NEVER built, installed, or executed on any
// device/emulator by this author. Self-starting Instrumentation for the EXISTING isolated
// alias app. Reuses production OpenCvIdentityBackend.open + IdentityProbe/
// evaluateQuality/embed/close. No production backend/model/threshold/executor is modified.
// No real person's identity/image, no feature values, no paths, no exception text are logged.
package io.ondeviceai.demo

import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import io.ondeviceai.identity.IdentityError
import io.ondeviceai.identity.IdentityProbe
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelStatus
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.opencvidentity.OpenCvIdentityBackend
import io.ondeviceai.opencvidentity.OpenCvIdentityModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

class IdentityParityInstrumentation : Instrumentation() {
    private class ParityFailure(val token: String) : Exception()
    companion object {
        private const val PUBLIC_FIXTURE_SHA = "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"
        private const val DETECTOR_SHA = "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"
        private const val RECOGNIZER_SHA = "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"
        private const val PROBE_REVISION = "parity-upright-public-fixture-v1"
        private const val INPUT = "input.png"
        private const val MODELS = "models"
    }
    private lateinit var runId: String; private lateinit var imageSHA: String; private lateinit var sourceFingerprint: String
    private var publicFixtureOnly = true; private var started = 0L
    private lateinit var taskRoot: File; private lateinit var work: File
    private lateinit var featureFile: File; private lateinit var reportFile: File; private lateinit var failedFile: File
    private lateinit var report: JSONObject

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        try {
            runId = arg(arguments, "runId", "[A-Za-z0-9]{1,40}")
            imageSHA = arg(arguments, "imageSHA", "[0-9a-f]{64}")
            sourceFingerprint = arg(arguments, "sourceFingerprint", "[A-Za-z0-9._:-]{1,256}")
            publicFixtureOnly = (arguments?.getString("publicFixtureOnly") ?: "true") == "true"
        } catch (_: Throwable) {
            finish(1, Bundle().apply { putString("status", "failed") })
            return
        }
        started = System.nanoTime()
        taskRoot = File(targetContext.filesDir, "odai-parity/$runId"); work = File(targetContext.cacheDir, "odai-parity-work-$runId")
        featureFile = File(taskRoot, "parity-feature-$runId.json"); reportFile = File(taskRoot, "parity-report-$runId.json")
        failedFile = File(taskRoot, "parity-failed-$runId.json"); report = commonReport(); start()
    }

    override fun onStart() {
        var backend: OpenCvIdentityBackend? = null; var store: ModelStore? = null
        var ownsWork = false; var values: List<Float>? = null
        val cleanup = JSONObject().put("backendClosed", false).put("nativeDrained", false).put("modelLeaseReleased", false)
            .put("modelStoreRemoved", false).put("workDirectoryRemoved", false).put("cacheCleared", false)
            .put("sourceInputPreserved", true).put("sourceModelsPreserved", true).put("outcome", "pending")
        var stage = "preflight"
        try {
            ensure(dir(taskRoot) && !symlink(taskRoot), "preflight")
            val modelsDir = File(taskRoot, MODELS)
            ensure(dir(modelsDir) && !symlink(modelsDir), "preflight")
            val inputFile = File(taskRoot, INPUT)
            ensure(regular(inputFile) && !symlink(inputFile) && inputFile.canonicalFile.parentFile == taskRoot.canonicalFile, "preflight")
            val detectorFile = File(modelsDir, OpenCvIdentityModel.DETECTOR)
            val recognizerFile = File(modelsDir, OpenCvIdentityModel.RECOGNIZER)
            for (f in listOf(detectorFile, recognizerFile)) ensure(regular(f) && !symlink(f) &&
                f.canonicalFile.parentFile == modelsDir.canonicalFile, "preflight")
            val manifest = OpenCvIdentityModel.manifest()
            ensure(modelsDir.list()?.toSet() == manifest.files.map { it.path }.toSet(), "model-manifest")
            for (f in listOf(work, featureFile, reportFile, failedFile)) ensure(!exists(f) && !symlink(f), "preflight")
            // Hash validation before ANY feature work.
            stage = "hash-validation"
            val measuredInput = digest(inputFile)
            report.put("inputSHA256", measuredInput)
            ensure(measuredInput == imageSHA, "input-hash")
            if (publicFixtureOnly) ensure(imageSHA == PUBLIC_FIXTURE_SHA, "fixture-gate")
            val dSha = digest(detectorFile); val rSha = digest(recognizerFile)
            report.put("modelWeightSHA256", JSONObject().put("detector", dSha).put("recognizer", rSha))
            ensure(dSha == DETECTOR_SHA && rSha == RECOGNIZER_SHA, "model-hash")
            stage = "work-create"
            ensure(!exists(work) && work.mkdir(), "work-create")
            ownsWork = true
            val modelsWorkDir = File(work, "models")
            ensure(modelsWorkDir.mkdir(), "work-create")
            stage = "model-store"
            val models = ModelStore(modelsWorkDir, ModelCompatibility(OpenCvIdentityModel.BACKEND_ID,
                OpenCvIdentityModel.VERSION, Build.VERSION.SDK_INT.toString(), Build.SUPPORTED_ABIS.first()))
            store = models
            stage = "install"
            runBlocking {
                models.install(OpenCvIdentityModel.manifest(), modelsDir)
                ensure(models.status(OpenCvIdentityModel.ref) == ModelStatus.INSTALLED, "install")
            }
            stage = "open"
            val b = runBlocking { OpenCvIdentityBackend.open(models) }
            backend = b
            // Quality + feature via production primitives only.
            stage = "quality"
            val inputBytes = inputFile.readBytes()
            try {
                ensure(digest(inputBytes) == imageSHA, "input-hash")
                val probe = IdentityProbe(inputBytes, PROBE_REVISION)
                val quality = b.evaluateQuality(probe)
                report.put("faceCount", quality.faceCount).put("geometricQuality", quality.score ?: JSONObject.NULL)
                stage = "feature"
                val raw = b.embed(probe).values
                ensure(raw.size == 128 && raw.all { it.isFinite() }, "feature")
                val norm = kotlin.math.sqrt(raw.sumOf { it.toDouble() * it.toDouble() })
                ensure(norm.isFinite() && kotlin.math.abs(norm - 1.0) < 1e-4, "feature")
                values = raw
                report.put("feature", JSONObject().put("dimension", 128).put("finite", true).put("l2Normalized", true)
                    .put("valuesInReport", false).put("valuesLogged", false).put("localOnlyArtifact", true))
                stage = "done"
            } finally { inputBytes.fill(0) }
        } catch (error: Throwable) {
            recordFailure(stage, error)
        } finally {
            // Cleanup: close/drain -> release lease/store -> own work only. Fail-closed.
            var degraded = false
            var backendClosed = backend == null
            var storeReleased = store == null
            try {
                if (backend != null) {
                    val b = backend
                    runBlocking { b.closeAndWait() }
                    backendClosed = true
                    cleanup.put("backendClosed", true).put("nativeDrained", true)
                }
            } catch (_: Throwable) { degraded = true }
            // Do not attempt store removal after a failed backend close/drain.
            if (backendClosed) {
                try {
                    store?.let { m ->
                        runBlocking {
                            m.remove(OpenCvIdentityModel.ref)
                            ensure(m.status(OpenCvIdentityModel.ref) == ModelStatus.ABSENT, "cleanup")
                        }
                        storeReleased = true
                        cleanup.put("modelLeaseReleased", true).put("modelStoreRemoved", true)
                    }
                } catch (_: Throwable) { degraded = true }
            }
            // Owned work may be deleted only after verified backend close/drain and store
            // release; if either prior obligation failed, retain the work tree for investigation.
            if (ownsWork && exists(work) && backendClosed && storeReleased) {
                try {
                    val wp = work.toPath().toRealPath()
                    Files.walk(wp).use { ps -> ps.sorted(Comparator.reverseOrder()).forEach { p ->
                        if (!Files.isSymbolicLink(p) && p.toRealPath().startsWith(wp)) Files.delete(p) } }
                    ensure(!exists(work), "cleanup")
                    cleanup.put("workDirectoryRemoved", true).put("cacheCleared", true)
                } catch (_: Throwable) { degraded = true }
            }
            cleanup.put("outcome", if (degraded) "degraded" else "clean")
            report.put("cleanup", cleanup)
            if (degraded && !report.has("failureStage")) report.put("failureStage", "cleanup").put("failureCategory", "probe:cleanup")
        }
        var status = if (!report.has("failureStage")) "completed" else "failed"
        report.put("durationMillis", (System.nanoTime() - started) / 1_000_000L)
        // LOCAL-ONLY feature artifact; report emitted only after the close final state.
        if (status == "completed" && values != null) {
            try {
                ensure(!exists(featureFile), "artifact")
                val payload = JSONObject().put("runId", runId).put("dimension", 128).put("normalization", "l2")
                    .put("values", JSONArray().apply { values!!.forEach { put(it.toDouble()) } })
                writeAtomic(featureFile, payload.toString())
            } catch (_: Throwable) {
                try { if (exists(featureFile)) Files.delete(featureFile.toPath()) } catch (_: Throwable) {}
                status = "failed"
                report.put("status", "failed").put("failureStage", "feature-artifact").put("failureCategory", "probe:artifact")
            }
        }
        report.put("status", status).put("terminal", true)
        val target = if (status == "completed") reportFile else failedFile
        var persisted = try { writeAtomic(target, report.toString()); true } catch (_: Throwable) { false }
        if (!persisted) {
            report.put("status", "failed").put("failureStage", "report-write").put("failureCategory", "probe:artifact")
            try { writeAtomic(failedFile, report.toString()) } catch (_: Throwable) {}
            status = "failed"
        }
        finish(if (status == "completed" && persisted) -1 else 1,
            Bundle().apply { putString("status", status); putBoolean("reportPersisted", persisted) })
    }

    private fun commonReport(): JSONObject {
        val m = OpenCvIdentityModel
        val weights = JSONObject().put("detector", DETECTOR_SHA).put("recognizer", RECOGNIZER_SHA)
        val basis = JSONObject().put("dimension", 128).put("dtype", "float32").put("normalization", "l2")
            .put("scoreMetric", "cosine").put("scoreDirection", "higherIsBetter").put("preprocessRevision", m.PREPROCESS)
            .put("modelWeightSHA256", weights).put("comparator", "local-later-explicit-mapping").put("comparedHere", false)
        val stages = JSONObject().put("decodedPixels", "unobservable-via-public-api").put("resizedPixels", "unobservable-via-public-api")
            .put("detectedFaces", "count-only-via-evaluateQuality").put("faceCrop", "unobservable-via-public-api")
            .put("landmarks", "unobservable-via-public-api").put("alignedCrop", "unobservable-via-public-api")
            .put("preprocessedPixels", "not-measured")
        return JSONObject().put("reportVersion", 1).put("kind", "paired-feature-parity-probe")
            .put("runId", runId).put("platform", "android").put("sdkInt", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown").put("sourceFingerprint", sourceFingerprint)
            .put("sourceFingerprintAuthority", "caller-supplied-provenance-claim-only; not authority, not proof of compiled source or build")
            .put("expectedInputSHA256", imageSHA).put("publicFixtureOnly", publicFixtureOnly)
            .put("modelRevision", m.ref.revision).put("preprocessRevision", m.PREPROCESS)
            .put("backendId", m.BACKEND_ID).put("backendVersion", m.VERSION)
            .put("embeddingSpaceCanonicalJSON", OpenCvIdentityModel.embeddingSpace.canonicalJSON())
            .put("embeddingSpaceCanonicalJSONSource", "native-OpenCvIdentityModel.embeddingSpace.canonicalJSON")
            .put("crossPlatformNote", "backendId intentionally differs across OS; embeddingSpaceCanonicalJSON is NOT forced identical")
            .put("parityBasis", basis).put("intermediateStages", stages).put("pixelParityMeasured", false)
            .put("policyStatus", "UNVERIFIED").put("productionReady", false).put("pad", "absent")
            .put("cameraStarts", 0).put("micStarts", 0).put("enrollments", 0).put("thresholdDecisions", 0)
            .put("verificationDecisions", 0).put("featureValuesInReport", false).put("featureValuesLogged", false)
            .put("execution", "candidate-code-not-executed-by-author")
    }

    private fun arg(b: Bundle?, key: String, pattern: String): String {
        val v = b?.getString(key) ?: throw ParityFailure("arguments")
        ensure(Regex("^" + pattern + "$").matches(v), "arguments")
        return v
    }
    private fun ensure(c: Boolean, t: String) { if (!c) throw ParityFailure(t) }
    private fun exists(f: File) = Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun symlink(f: File) = Files.isSymbolicLink(f.toPath())
    private fun dir(f: File) = f.isDirectory
    private fun regular(f: File) = Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun digest(f: File): String {
        ensure(regular(f) && !symlink(f), "hash-validation")
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(1048576)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return hex(md.digest())
    }
    private fun digest(b: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(b))
    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    private fun recordFailure(stage: String, error: Throwable) {
        report.put("failureStage", stage).put("failureCategory", when (error) {
            is IdentityError -> "identity:" + error.code.wire
            is ParityFailure -> "probe:" + error.token
            else -> "io-or-runtime"
        })
    }
    private fun writeAtomic(target: File, text: String) {
        ensure(!exists(target) && !exists(File(target.path + ".new")) && !exists(File(target.path + ".bak")), "artifact")
        val atomic = AtomicFile(target); val stream = atomic.startWrite()
        try { stream.write((text + "\n").toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Throwable) { atomic.failWrite(stream); throw e }
    }
}

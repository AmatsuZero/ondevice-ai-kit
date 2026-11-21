package io.ondeviceai.modelstore
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.*
class InstallCoordinatorTest {
    @Test fun cleanupFailureAfterCommitReportsOwnedDirectory()=runBlocking<Unit> {
        val root=Files.createTempDirectory("odai-cleanup-commit").toFile()
        try {
            val data="fixture".toByteArray()
            val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
            val manifest=fixture(ModelFile("weights.bin","https://models.example/file",7,hash))
            val transport=object:ModelDownloadTransport {
                override suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit) {destination.writeBytes(data)}
            }
            val store=ModelStore(File(root,"store"),ModelCompatibility("fixture","1.0.0","26","arm64"))
            val downloads=File(root,"downloads");val cause=java.io.IOException("permission denied")
            val coordinator=InstallCoordinator(store,downloads,transport,cleanup={throw cause})
            val error=assertFailsWith<ModelCleanupException>{coordinator.install(manifest)}
            assertEquals(downloads,error.directory.parentFile);assertTrue(error.directory.isDirectory)
            assertSame(cause,error.cause);assertEquals(ModelStatus.INSTALLED,coordinator.status(manifest.ref))
        } finally {root.deleteRecursively()}
    }
    @Test fun explicitCancellationReportsCleanupFailure()=runBlocking<Unit> {checkCancelledCleanupFailure(false)}
    @Test fun callerCancellationReportsCleanupFailure()=runBlocking<Unit> {checkCancelledCleanupFailure(true)}
    private suspend fun checkCancelledCleanupFailure(cancelCaller:Boolean)=coroutineScope {
        val root=Files.createTempDirectory("odai-cleanup-cancel").toFile()
        try {
            val manifest=fixture(ModelFile("weights.bin","https://models.example/file",7,"0".repeat(64)))
            val started=CompletableDeferred<Unit>()
            val transport=object:ModelDownloadTransport {
                override suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit) {started.complete(Unit);awaitCancellation()}
            }
            val store=ModelStore(File(root,"store"),ModelCompatibility("fixture","1.0.0","26","arm64"))
            val downloads=File(root,"downloads");val cause=java.io.IOException("permission denied")
            val coordinator=InstallCoordinator(store,downloads,transport,cleanup={throw cause})
            val observed=CompletableDeferred<Throwable>()
            val task=launch {try {coordinator.install(manifest,"only");observed.complete(AssertionError("expected cleanup failure"))}catch(error:Throwable){observed.complete(error)}}
            started.await()
            if(cancelCaller)task.cancel() else coordinator.cancel("only")
            val error=withTimeout(5000){observed.await()}
            task.join();assertTrue(error is ModelCleanupException,"cleanup failure must survive cancellation: $error")
            assertEquals(downloads,error.directory.parentFile);assertTrue(error.directory.isDirectory)
            assertSame(cause,error.cause);assertEquals(ModelStatus.ABSENT,coordinator.status(manifest.ref))
        } finally {root.deleteRecursively()}
    }
    @Test fun sharedInstallKeepsSecondObserverAfterFirstCancels() = runBlocking<Unit> {
        val root=Files.createTempDirectory("odai-download-test").toFile()
        try {
            val data="fixture".toByteArray();val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
            val manifest=fixture(ModelFile("weights.bin","https://models.example/v1/weights.bin",data.size.toLong(),hash))
            val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val attached=CompletableDeferred<Unit>();var count=0
            val transport=object:ModelDownloadTransport { override suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit) { count++;started.complete(Unit);release.await();destination.writeBytes(data);progress(data.size.toLong()) } }
            val store=ModelStore(File(root,"store"),ModelCompatibility("fixture","1.0.0","26","arm64"))
            val coordinator=InstallCoordinator(store,File(root,"downloads"),transport)
            val first=async { coordinator.install(manifest,"first") };started.await()
            val second=async { coordinator.install(manifest,"second"){attached.complete(Unit)} };attached.await()
            coordinator.cancel("first");assertFailsWith<CancellationException>{first.await()}
            release.complete(Unit);assertEquals(manifest.ref,second.await().ref);assertEquals(1,count)
        } finally {root.deleteRecursively()}
    }
    @Test fun lastCancellationStopsSuspendingTransport() = runBlocking<Unit> {
        val root=Files.createTempDirectory("odai-last-cancel").toFile()
        try {
            val manifest=fixture(ModelFile("weights.bin","https://models.example/file",7,"0".repeat(64)))
            val started=CompletableDeferred<Unit>();val stopped=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val transport=object:ModelDownloadTransport { override suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit) {
                started.complete(Unit);try {release.await()} finally {stopped.complete(Unit)}
            } }
            val store=ModelStore(File(root,"store"),ModelCompatibility("fixture","1.0.0","26","arm64"))
            val coordinator=InstallCoordinator(store,File(root,"downloads"),transport)
            val task=async {coordinator.install(manifest,"only")};started.await();coordinator.cancel("only")
            val prompt=withTimeoutOrNull(1000) {stopped.await();true}?:false
            release.complete(Unit)
            assertFailsWith<CancellationException>{task.await()}
            assertEquals(ModelStatus.ABSENT,store.status(manifest.ref));assertTrue(prompt,"transport must be cancelled")
        } finally {root.deleteRecursively()}
    }
    @Test fun policyRejectsPlaintextAndUnknownHosts() {
        val policy=HTTPSDownloadPolicy(setOf("models.example"))
        assertFails {policy.validate("http://models.example/file")};assertFails {policy.validate("https://foreign.example/file")}
        policy.validate("https://models.example/file")
    }
    @Test fun freezesCallerFileListBeforeSuspendingDownload()=runBlocking<Unit> {
        val root=Files.createTempDirectory("odai-snapshot-test").toFile()
        try {
            val data="fixture".toByteArray()
            val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
            val files=mutableListOf(ModelFile("one.bin","https://models.example/one",7,hash),ModelFile("two.bin","https://models.example/two",7,hash))
            val manifest=fixture(files[0]).copy(files=files,resources=ModelResources(14,14,14))
            val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val transport=object:ModelDownloadTransport {
                override suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit) {
                    if(file.path=="one.bin") {started.complete(Unit);release.await()}
                    // An unexpected destination is rejected before any file is written.
                    require(file.path in listOf("one.bin","two.bin")){"caller mutation reached transport"}
                    destination.writeBytes(data)
                }
            }
            val store=ModelStore(File(root,"store"),ModelCompatibility("fixture","1.0.0","26","arm64"))
            val coordinator=InstallCoordinator(store,File(root,"downloads"),transport)
            val outcome=async {runCatching {coordinator.install(manifest)}}
            started.await();files[1]=files[1].copy(path="../changed.bin");release.complete(Unit)
            assertTrue(outcome.await().isSuccess,"in-flight manifest must be an owned snapshot")
            assertEquals(ModelStatus.INSTALLED,store.status(manifest.ref))
            assertFalse(File(root,"downloads/changed.bin").exists())
        } finally {root.deleteRecursively()}
    }
}

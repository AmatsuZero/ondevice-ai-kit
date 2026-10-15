package io.ondeviceai.modelstore
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.*
class InstallCoordinatorTest {
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
}

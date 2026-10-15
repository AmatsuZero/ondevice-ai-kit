package io.ondeviceai.modelstore
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.runBlocking
class ModelStoreTest {
    @Test fun verifiedInstallPinsRevisionUntilLeaseClosed() = runBlocking<Unit> {
        val root = Files.createTempDirectory("odai-model-test").toFile()
        try {
            val source = File(root,"source").apply { mkdirs() }; File(source,"weights.bin").writeText("small model fixture")
            val bytes = File(source,"weights.bin").readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val manifest = fixture(ModelFile("weights.bin", "https://models.example/fixture/v1/weights.bin", bytes.size.toLong(), sha))
            val store = ModelStore(File(root,"store"), ModelCompatibility("fixture","1.0.0","26","arm64"))
            store.install(manifest, source); assertEquals(ModelStatus.INSTALLED, store.status(manifest.ref))
            val lease = store.acquire(manifest.ref)
            assertFailsWith<ModelInUseException> { store.remove(manifest.ref) }
            val broken = manifest.copy(revision="v2", files=listOf(manifest.files.single().copy(sha256="0".repeat(64))))
            assertFails { store.install(broken, source) }
            assertEquals(ModelStatus.INSTALLED, store.status(manifest.ref)); assertEquals(ModelStatus.ABSENT, store.status(broken.ref))
            lease.close(); lease.close(); store.remove(manifest.ref); assertEquals(ModelStatus.ABSENT,store.status(manifest.ref))
        } finally { root.deleteRecursively() }
    }
    @Test fun unsafeManifestAndSymlinkAreRejected() = runBlocking<Unit> {
        val root = Files.createTempDirectory("odai-model-test").toFile()
        try {
            val source = File(root,"source").apply { mkdirs() }; File(source,"weights.bin").writeText("fixture")
            val file = ModelFile("weights.bin","https://models.example/v1/weights.bin",7,"0".repeat(64))
            val store = ModelStore(File(root,"store"),ModelCompatibility("fixture","1.0.0","26","arm64"))
            for (path in listOf("../escape","/absolute","a//b",".odai-manifest.json")) {
                assertFails { store.install(fixture(file.copy(path=path)),source) }
            }
            assertFails { store.install(fixture(file).copy(schemaVersion=2),source) }
            assertFails { store.install(fixture(file).copy(minimumOS="99"),source) }
            File(source,"weights.bin").delete(); Files.createSymbolicLink(File(source,"weights.bin").toPath(), File(root,"outside").toPath())
            assertFails { store.install(fixture(file),source) }
        } finally { root.deleteRecursively() }
    }
}
fun fixture(file: ModelFile) = ModelManifest(modelId="fixture",revision="v1",upstreamURL="https://models.example/fixture",upstreamRevision="pinned-test-commit",capability="test-only-data",backendId="fixture",backendVersion=VersionRange("1.0.0","2.0.0"),minimumOS="26",architectures=listOf("arm64"),formatVersion="raw-v1",files=listOf(file),processing=ModelProcessing("bytes","bytes","none-v1",null,null,1024),resources=ModelResources(file.bytes,file.bytes,file.bytes),licenseIdentifier="CC0-1.0",licenseURL="https://creativecommons.org/publicdomain/zero/1.0/",attribution="SDK synthetic fixture",redistribution="permitted")

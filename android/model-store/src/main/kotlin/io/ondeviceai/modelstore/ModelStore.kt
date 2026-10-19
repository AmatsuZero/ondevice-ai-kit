package io.ondeviceai.modelstore
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One store owns one app-private root. Leases must outlive all backend file reads. */
class ModelStore(private val root:File,private val compatibility:ModelCompatibility) {
    private val mutex=Mutex()
    private val leases=mutableMapOf<ModelRef,MutableSet<String>>()
    init {
        File(root,"installed").mkdirs();val staging=File(root,"staging");staging.mkdirs()
        require(!Files.isSymbolicLink(staging.toPath()))
        staging.listFiles()?.forEach { it.deleteRecursively() }
    }
    private fun directory(ref:ModelRef)=File(File(File(root,"installed"),ref.modelId),ref.revision)
    private fun validateRef(ref:ModelRef) { require(listOf(ref.modelId,ref.revision).all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it!="." && it!=".." }) }
    private fun version(value:String):List<Int> = value.split('.').map { require(it.isNotEmpty() && it.all { c->c in '0'..'9' });it.toInt() }
    private fun compare(a:String,b:String):Int {
        val x=version(a);val y=version(b)
        for(i in 0 until maxOf(x.size,y.size)) { val c=(x.getOrElse(i){0}).compareTo(y.getOrElse(i){0});if(c!=0)return c };return 0
    }
    fun validate(m:ModelManifest) {
        validateRef(m.ref)
        require(m.schemaVersion==1 && m.files.isNotEmpty() && m.upstreamRevision.isNotBlank() && m.capability.isNotBlank() && m.formatVersion.isNotBlank())
        require(m.licenseIdentifier.isNotBlank() && m.attribution.isNotBlank() && m.redistribution.isNotBlank())
        require(m.processing.preprocessRevision.isNotBlank() && m.processing.inputSignature.isNotBlank() && m.processing.outputSignature.isNotBlank() && m.processing.maxInputElements>0)
        for(value in listOf(m.upstreamURL,m.licenseURL)) {
            val url=URI(value);require(url.scheme=="https" && url.host!=null && url.userInfo==null)
        }
        m.resources.measuredMemoryBytes?.let { require(it>=0 && !m.resources.memoryMeasurementContext.isNullOrBlank()) }
        require(m.backendId==compatibility.backendId && compatibility.architecture in m.architectures && compare(compatibility.osVersion,m.minimumOS)>=0 && compare(compatibility.backendVersion,m.backendVersion.minimum)>=0 && compare(compatibility.backendVersion,m.backendVersion.maximumExclusive)<0)
        val paths=mutableSetOf<String>();var total=0L
        for(f in m.files) {
            require(!f.path.startsWith("/") && !f.path.startsWith(".odai-") && !f.path.contains('\\') && !f.path.contains('\u0000') && f.path.split('/').all { it.isNotEmpty() && it!="." && it!=".." } && paths.add(f.path))
            require(f.bytes>=0 && f.sha256.matches(Regex("[0-9a-f]{64}")))
            val url=URI(f.url);require(url.scheme=="https" && url.host!=null && url.userInfo==null)
            total=Math.addExact(total,f.bytes)
        }
        require(m.resources.downloadBytes==total && m.resources.installedBytesLimit>=total && m.resources.temporaryBytes>=total)
        require((m.resources.measuredMemoryBytes==null)==(m.resources.memoryMeasurementContext==null))
    }
    private fun verify(m:ModelManifest,source:File,includesManifest:Boolean=false) {
        require(source.isDirectory && !Files.isSymbolicLink(source.toPath()))
        val seen=mutableSetOf<String>()
        Files.walk(source.toPath()).use { paths->paths.forEach { path->
            require(!Files.isSymbolicLink(path))
            if(!Files.isDirectory(path)) {
                require(Files.isRegularFile(path))
                val relative=source.toPath().relativize(path).toString()
                if(!(includesManifest && relative==".odai-manifest.json")) seen.add(relative)
            }
        } }
        require(seen==m.files.map { it.path }.toSet())
        for(f in m.files) {
            val file=File(source,f.path);if(file.length()!=f.bytes) throw ModelIntegrityException()
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input->val buffer=ByteArray(1024*1024);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
            if(digest.digest().joinToString(""){"%02x".format(it)}!=f.sha256) throw ModelIntegrityException()
        }
    }
    private fun statusLocked(ref:ModelRef):ModelStatus {
        return try {
            validateRef(ref);val dir=directory(ref)
            if(!dir.exists()) ModelStatus.ABSENT else {
                val m=ModelManifest.parse(File(dir,".odai-manifest.json").readText());require(m.ref==ref);validate(m);verify(m,dir,true);ModelStatus.INSTALLED
            }
        } catch(_:Exception){ModelStatus.INVALID}
    }
    suspend fun status(ref:ModelRef)=withContext(Dispatchers.IO){mutex.withLock { statusLocked(ref) }}
    suspend fun install(manifest:ModelManifest,source:File) {
        val snapshot=manifest.snapshot()
        withContext(Dispatchers.IO) { val manifest=snapshot;mutex.withLock {
        validate(manifest);verify(manifest,source);ensureActive()
        val target=directory(manifest.ref)
        if(target.exists()) {
            val existing=ModelManifest.parse(File(target,".odai-manifest.json").readText())
            if(existing!=manifest || statusLocked(manifest.ref)!=ModelStatus.INSTALLED)throw ModelIntegrityException()
        } else {
            if(root.usableSpace<manifest.resources.temporaryBytes)throw ModelDiskSpaceException()
            val stage=File(File(root,"staging"),UUID.randomUUID().toString()).apply { mkdirs() }
            try {
                for(file in manifest.files) { ensureActive();val output=File(stage,file.path);output.parentFile!!.mkdirs();Files.copy(File(source,file.path).toPath(),output.toPath()) }
                verify(manifest,stage);File(stage,".odai-manifest.json").writeText(manifest.toJson());ensureActive()
                target.parentFile!!.mkdirs();Files.move(stage.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
            } finally { stage.deleteRecursively() }
        }
    } } }
    suspend fun acquire(ref:ModelRef):ModelLease=withContext(Dispatchers.IO) { mutex.withLock {
        check(statusLocked(ref)==ModelStatus.INSTALLED){"absent"}
        val manifest=ModelManifest.parse(File(directory(ref),".odai-manifest.json").readText())
        val token=UUID.randomUUID().toString();leases.getOrPut(ref){mutableSetOf()}.add(token)
        ModelLease(directory(ref),manifest){mutex.withLock{leases[ref]?.remove(token);Unit}}
    } }
    suspend fun remove(ref:ModelRef)=withContext(Dispatchers.IO) {mutex.withLock {
        validateRef(ref);if(!leases[ref].isNullOrEmpty())throw ModelInUseException()
        val dir=directory(ref);if(dir.exists() && !dir.deleteRecursively())throw java.io.IOException("remove failed")
    }}
}
class ModelLease internal constructor(val directory:File,private val verifiedManifest:ModelManifest,private val release:suspend ()->Unit) {
    /** Read-only snapshot of the verified asset manifest. Collections are copied on access. */
    val manifest:ModelManifest get()=verifiedManifest.snapshot()
    suspend fun close() { release() }
}

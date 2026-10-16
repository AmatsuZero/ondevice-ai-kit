package io.ondeviceai.modelstore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

data class InstalledModel(val ref:ModelRef)
/** Cleanup failed after transport stopped. Query model status before retrying installation. */
class ModelCleanupException(val directory:File,cause:Throwable):java.io.IOException("download cleanup failed: $directory",cause)
class InstallCoordinator internal constructor(private val store:ModelStore,private val downloadRoot:File,private val transport:ModelDownloadTransport,private val freeBytes:(File)->Long={it.usableSpace},private val cleanup:(File)->Unit) {
    constructor(store:ModelStore,downloadRoot:File,transport:ModelDownloadTransport,freeBytes:(File)->Long={it.usableSpace}):this(store,downloadRoot,transport,freeBytes,{directory->
        if(!directory.deleteRecursively())throw java.io.IOException("remove failed")
    })
    private class Waiter(val result:CompletableDeferred<InstalledModel>,val progress:(Long)->Unit)
    private class InstallJob(val manifest:ModelManifest) {
        val waiters=mutableMapOf<String,Waiter>();val cancelled=mutableListOf<Waiter>()
        var worker:Job?=null;var cancelling=false;var bytes=0L
    }
    private val lock=Any()
    private val jobs=mutableMapOf<ModelRef,InstallJob>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    suspend fun status(ref:ModelRef):ModelStatus {
        if(synchronized(lock){jobs.containsKey(ref)})return ModelStatus.INSTALLING
        return store.status(ref)
    }
    suspend fun install(manifest:ModelManifest,observerId:String=UUID.randomUUID().toString(),progress:(Long)->Unit={}):InstalledModel {
        val snapshot=manifest.snapshot()
        store.validate(snapshot);currentCoroutineContext().ensureActive()
        val waiter=Waiter(CompletableDeferred(),progress)
        val job=synchronized(lock) {
            require(jobs.values.none { observerId in it.waiters }){"duplicate observer"}
            val job=jobs.getOrPut(snapshot.ref){InstallJob(snapshot)}
            require(job.manifest==snapshot && !job.cancelling){"installation revision already active with different assets or cancelling"}
            job.waiters[observerId]=waiter
            if(job.worker==null) {
                // Always enter cleanup even if cancelled before dispatcher starts the worker.
                job.worker=scope.launch(start=CoroutineStart.UNDISPATCHED) { perform(job) }
            }
            job
        }
        progress(synchronized(lock){job.bytes})
        try {return waiter.result.await()}
        catch(error:CancellationException) {
            cancel(observerId)
            val terminal = withContext(NonCancellable) { runCatching { waiter.result.await() } }
            terminal.exceptionOrNull()?.let { if (it is ModelCleanupException) throw it }
            throw error
        }
    }
    fun cancel(observerId:String) = synchronized(lock) {
        val job=jobs.values.firstOrNull {observerId in it.waiters}?:return@synchronized
        val waiter=job.waiters.remove(observerId)?:return@synchronized
        if(job.waiters.isEmpty()) {job.cancelling=true;job.cancelled.add(waiter);job.worker?.cancel()}
        else waiter.result.completeExceptionally(CancellationException("observer cancelled"))
    }
    private fun checkCancelled(job:InstallJob) {if(synchronized(lock){job.cancelling})throw CancellationException("last observer cancelled")}
    private suspend fun perform(job:InstallJob) {
        val manifest=job.manifest;val directory=File(downloadRoot,UUID.randomUUID().toString())
        val result=runCatching {
            check(downloadRoot.exists() || downloadRoot.mkdirs())
            val required=maxOf(Math.multiplyExact(manifest.resources.downloadBytes,2),manifest.resources.temporaryBytes)
            if(freeBytes(downloadRoot)<required)throw ModelDiskSpaceException()
            check(directory.mkdirs());var base=0L
            for(file in manifest.files) {
                checkCancelled(job);val destination=File(directory,file.path);destination.parentFile!!.mkdirs();val offset=base
                transport.download(file,destination) { bytes ->
                    checkCancelled(job)
                    val callbacks=synchronized(lock){job.bytes=maxOf(job.bytes,offset+minOf(bytes,file.bytes));job.waiters.values.map{it.progress}}
                    callbacks.forEach{it(job.bytes)}
                }
                base+=file.bytes
            }
            checkCancelled(job);store.install(manifest,directory);InstalledModel(manifest.ref)
        }
        val cleanupError=runCatching {cleanup(directory)}.exceptionOrNull()
        val finalResult=cleanupError?.let { Result.failure<InstalledModel>(ModelCleanupException(directory,it)) } ?: result
        val final=synchronized(lock){jobs.remove(manifest.ref);job.waiters.values.toList() to job.cancelled.toList()}
        final.first.forEach{it.result.completeWith(finalResult)}
        final.second.forEach{it.result.completeExceptionally(cleanupError?.let { error -> ModelCleanupException(directory,error) } ?: CancellationException("observer cancelled"))}
    }
}

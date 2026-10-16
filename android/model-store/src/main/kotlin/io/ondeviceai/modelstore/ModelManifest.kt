package io.ondeviceai.modelstore
import org.json.JSONArray
import org.json.JSONObject

data class ModelRef(val modelId:String,val revision:String)
data class ModelFile(val path:String,val url:String,val bytes:Long,val sha256:String)
data class VersionRange(val minimum:String,val maximumExclusive:String)
data class ModelProcessing(val inputSignature:String,val outputSignature:String,val preprocessRevision:String,val tokenizerRevision:String?,val promptRevision:String?,val maxInputElements:Int)
data class ModelResources(val downloadBytes:Long,val temporaryBytes:Long,val installedBytesLimit:Long,val measuredMemoryBytes:Long?=null,val memoryMeasurementContext:String?=null)
data class ModelCompatibility(val backendId:String,val backendVersion:String,val osVersion:String,val architecture:String)
data class ModelManifest(val schemaVersion:Int=1,val modelId:String,val revision:String,val upstreamURL:String,val upstreamRevision:String,val capability:String,val backendId:String,val backendVersion:VersionRange,val minimumOS:String,val architectures:List<String>,val formatVersion:String,val files:List<ModelFile>,val processing:ModelProcessing,val resources:ModelResources,val licenseIdentifier:String,val licenseURL:String,val attribution:String,val redistribution:String) {
    val ref:ModelRef get()=ModelRef(modelId,revision)
    internal fun snapshot():ModelManifest=copy(files=files.toList(),architectures=architectures.toList())
    fun toJson():String = JSONObject().apply {
        put("schemaVersion",schemaVersion);put("modelId",modelId);put("revision",revision);put("upstreamURL",upstreamURL);put("upstreamRevision",upstreamRevision)
        put("capability",capability);put("backendId",backendId);put("backendVersion",JSONObject().put("minimum",backendVersion.minimum).put("maximumExclusive",backendVersion.maximumExclusive))
        put("minimumOS",minimumOS);put("architectures",JSONArray(architectures));put("formatVersion",formatVersion)
        put("files",JSONArray(files.map { JSONObject().put("path",it.path).put("url",it.url).put("bytes",it.bytes).put("sha256",it.sha256) }))
        put("processing",JSONObject().put("inputSignature",processing.inputSignature).put("outputSignature",processing.outputSignature).put("preprocessRevision",processing.preprocessRevision).put("tokenizerRevision",processing.tokenizerRevision?:JSONObject.NULL).put("promptRevision",processing.promptRevision?:JSONObject.NULL).put("maxInputElements",processing.maxInputElements))
        put("resources",JSONObject().put("downloadBytes",resources.downloadBytes).put("temporaryBytes",resources.temporaryBytes).put("installedBytesLimit",resources.installedBytesLimit).put("measuredMemoryBytes",resources.measuredMemoryBytes?:JSONObject.NULL).put("memoryMeasurementContext",resources.memoryMeasurementContext?:JSONObject.NULL))
        put("licenseIdentifier",licenseIdentifier);put("licenseURL",licenseURL);put("attribution",attribution);put("redistribution",redistribution)
    }.toString()
    companion object {
        fun parse(json:String):ModelManifest {
            val j=JSONObject(json);val b=j.getJSONObject("backendVersion");val p=j.getJSONObject("processing");val r=j.getJSONObject("resources")
            val files=j.getJSONArray("files");val architectures=j.getJSONArray("architectures")
            return ModelManifest(j.getInt("schemaVersion"),j.getString("modelId"),j.getString("revision"),j.getString("upstreamURL"),j.getString("upstreamRevision"),j.getString("capability"),j.getString("backendId"),VersionRange(b.getString("minimum"),b.getString("maximumExclusive")),j.getString("minimumOS"),(0 until architectures.length()).map{architectures.getString(it)},j.getString("formatVersion"),(0 until files.length()).map { i-> val f=files.getJSONObject(i);ModelFile(f.getString("path"),f.getString("url"),f.getLong("bytes"),f.getString("sha256")) },ModelProcessing(p.getString("inputSignature"),p.getString("outputSignature"),p.getString("preprocessRevision"),if(p.isNull("tokenizerRevision"))null else p.getString("tokenizerRevision"),if(p.isNull("promptRevision"))null else p.getString("promptRevision"),p.getInt("maxInputElements")),ModelResources(r.getLong("downloadBytes"),r.getLong("temporaryBytes"),r.getLong("installedBytesLimit"),if(r.isNull("measuredMemoryBytes"))null else r.getLong("measuredMemoryBytes"),if(r.isNull("memoryMeasurementContext"))null else r.getString("memoryMeasurementContext")),j.getString("licenseIdentifier"),j.getString("licenseURL"),j.getString("attribution"),j.getString("redistribution"))
        }
    }
}
enum class ModelStatus { ABSENT, INSTALLING, INSTALLED, INVALID }
class ModelInUseException:IllegalStateException("inUse")
class ModelIntegrityException:IllegalStateException("integrity")
class ModelDiskSpaceException:IllegalStateException("diskSpace")

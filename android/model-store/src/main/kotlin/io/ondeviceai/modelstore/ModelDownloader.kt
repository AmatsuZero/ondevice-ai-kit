package io.ondeviceai.modelstore
import java.io.File
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
interface ModelDownloadTransport {
    suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit)
}
class HTTPSDownloadPolicy(allowedHosts:Set<String>) {
    private val hosts=allowedHosts.map { it.lowercase() }.toSet()
    fun validate(value:String) {
        val url=URI(value)
        require(url.scheme=="https" && url.host?.lowercase() in hosts && url.userInfo==null && (url.port==-1 || url.port==443)){"untrusted URL"}
    }
}
class HTTPSModelDownloader(allowedHosts:Set<String>):ModelDownloadTransport {
    private val policy=HTTPSDownloadPolicy(allowedHosts)
    override suspend fun download(file:ModelFile,destination:File,progress:(Long)->Unit)=withContext(Dispatchers.IO) {
        var current=file.url;var redirects=0
        while(true) {
            ensureActive();policy.validate(current)
            val connection=URL(current).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=15000
            try {
                val status=connection.responseCode
                if(status in listOf(301,302,303,307,308)) {
                    require(redirects++<5){"too many redirects"}
                    current=URL(URL(current),connection.getHeaderField("Location")?:throw java.io.IOException("missing redirect target")).toString()
                    policy.validate(current);continue
                }
                require(status==200){"HTTP $status"}
                require(connection.contentLengthLong<=file.bytes){"oversized response"}
                connection.inputStream.use { input -> destination.outputStream().use { output ->
                    val buffer=ByteArray(64*1024);var total=0L
                    while(true) { ensureActive();val n=input.read(buffer);if(n<0)break;total=Math.addExact(total,n.toLong());if(total>file.bytes)throw ModelIntegrityException();output.write(buffer,0,n);progress(total) }
                    if(total!=file.bytes)throw ModelIntegrityException()
                } }
                break
            } finally {connection.disconnect()}
        }
    }
}

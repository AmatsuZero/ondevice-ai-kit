    case("selected one-row scope and ordered byte heap events") {
        reset(); try {
            start(); val j=json()
            expect(j.getString("selectedClipId")=="fleurs-en_us-1776")
            expect(j.getInt("selectedOriginalIndex")==25 && j.getInt("verifiedInputCount")==65)
            expect(j.getJSONArray("rows").length()==1)
            expect(j.getJSONArray("rows").getJSONObject(0).getString("id")=="fleurs-en_us-1776")
            expect(j.isNull("heapTelemetryErrorClass"))
            expect(j.getBoolean("nativePolicyEffectVerified")==false)
            expect(j.getJSONObject("config").getString("provider")==Runtime.capturedProvider)
            val events=j.getJSONArray("heapEvents");expect(events.length() in 8..128)
            var previous=-1L
            for(i in 0 until events.length()) {
                val e=events.getJSONObject(i)
                expect(e.getInt("seq")==i)
                expect(e.getString("label").isNotEmpty())
                val time=e.getLong("elapsedRealtimeNanos");expect(time>=previous);previous=time
                expect(e.getLong("nativeHeapSizeBytes")==1000L)
                expect(e.getLong("nativeHeapAllocatedBytes")==600L)
                expect(e.getLong("nativeHeapFreeBytes")==400L)
            }
        } finally {Runtime.root.deleteRecursively()}
    }
    case("first heap API failure blocks constructor and preserves real error") {
        reset(); try {
            Runtime.heapFailWhen={true};start();expect(Runtime.creates==0 && Runtime.streams==0)
            expect(Runtime.code==0)
            failed();val j=json()
            expect(j.getString("heapTelemetryErrorClass")=="java.lang.IllegalStateException")
            val e=j.getJSONArray("heapEvents").getJSONObject(0)
            expect(e.getString("errorClass")=="java.lang.IllegalStateException")
            for(k in listOf("nativeHeapSizeBytes","nativeHeapAllocatedBytes","nativeHeapFreeBytes")) expect(e.isNull(k))
        } finally {Runtime.root.deleteRecursively()}
    }
    case("negative heap values fail closed before constructor") {
        reset(); try {
            Runtime.heapNegative=true;start();expect(Runtime.creates==0 && Runtime.streams==0 && Runtime.code==0)
            failed();expect(!json().isNull("heapTelemetryErrorClass"))
        } finally {Runtime.root.deleteRecursively()}
    }
    case("heap failure after real constructor blocks streams and still releases") {
        reset(); try {
            Runtime.heapFailWhen={Runtime.creates>0};start();failed()
            expect(Runtime.creates==1 && Runtime.releases==1 && Runtime.streams==0)
            expect(json().getBoolean("recognizerReleaseSucceeded"))
            expect(!json().isNull("heapTelemetryErrorClass"))
        } finally {Runtime.root.deleteRecursively()}
    }
    case("heap failure after create still releases acquired stream and recognizer") {
        reset(); try {
            Runtime.heapFailWhen={Runtime.streams>0};start();failed()
            expect(Runtime.streams==1 && Runtime.streamReleases==1 && Runtime.decodes==0 && Runtime.releases==1)
            expect(!json().isNull("heapTelemetryErrorClass"));allZero()
        } finally {Runtime.root.deleteRecursively()}
    }
    case("heap failure after decode preserves dispatch and cleanup truth") {
        reset(); try {
            Runtime.heapFailWhen={Runtime.decodes>0};start();failed()
            expect(Runtime.streams==1 && Runtime.streamReleases==1 && Runtime.decodes==1 && Runtime.releases==1)
            expect(json().getInt("nativeDecodeCalls")==1)
            expect(!json().isNull("heapTelemetryErrorClass"));allZero()
        } finally {Runtime.root.deleteRecursively()}
    }
    case("heap failure after actual recognizer release keeps successful release and teardown") {
        reset(); try {
            Runtime.heapFailWhen={Runtime.releases>0};start();failed()
            val j=json();expect(Runtime.releases==1 && j.getBoolean("recognizerReleaseSucceeded"))
            expect(j.getJSONObject("foreground").getBoolean("teardownAck"))
            expect(!j.isNull("heapTelemetryErrorClass"));allZero()
        } finally {Runtime.root.deleteRecursively()}
    }
    case("decode primary is not overwritten by cleanup heap failure") {
        reset(); try {
            Runtime.decodeFail=true;Runtime.heapFailWhen={Runtime.releases>0};start();failed()
            val j=json();expect(j.getString("errorClass")=="java.lang.IllegalStateException")
            expect(j.getString("errorMessage")=="injected-decode")
            expect(!j.isNull("heapTelemetryErrorClass"))
            expect(Runtime.streamReleases==1 && Runtime.releases==1);allZero()
        } finally {Runtime.root.deleteRecursively()}
    }
    case("decode primary survives a heap fault during final core persistence") {
        reset(); try {
            Runtime.decodeFail=true;Runtime.heapFailWhen={Runtime.decodes>0};start();failed()
            val j=json()
            expect(j.getString("errorClass")=="java.lang.IllegalStateException")
            expect(j.getString("errorMessage")=="injected-decode", "heap error replaced actual decode primary")
            expect(!j.isNull("heapTelemetryErrorClass"))
            val cs=j.getJSONArray("rows").getJSONObject(0).getJSONObject("caseState")
            expect(cs.getString("errorMessage")=="injected-decode" && !cs.isNull("persistErrorClass"))
            expect(Runtime.decodes==1 && Runtime.streams==1 && Runtime.streamReleases==1 && Runtime.releases==1)
            expect(j.getBoolean("recognizerReleaseSucceeded") && j.getJSONObject("foreground").getBoolean("teardownAck"))
            allZero()
        } finally {Runtime.root.deleteRecursively()}
    }

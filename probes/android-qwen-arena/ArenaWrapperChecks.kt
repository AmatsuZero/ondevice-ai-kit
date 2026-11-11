    case("arena policy receipt matches actual constructor and fixed exact config") {
        reset(); try {
            start(); val j=json(); val p=j.getJSONObject("allocatorPolicy")
            val f=File(p.getString("configPath"))
            expect(j.getString("probeVariant")=="cpu-arena-off-v1")
            expect(!j.getBoolean("nativePolicyEffectVerified"))
            expect(f.readText()=="EnableCpuMemArena=0\n")
            expect(p.getLong("bytes")==20L)
            expect(j.getJSONObject("config").getString("provider")==Runtime.capturedProvider)
            expect(p.getString("provider")==Runtime.capturedProvider)
            expect(!j.getJSONObject("config").getBoolean("requestedEnableCpuMemArena"))
            expect(p.getString("sha256")==java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString(""){"%02x".format(it)})
        } finally { Runtime.root.deleteRecursively() }
    }
    case("existing policy child blocks native and preserves existing content") {
        reset(); try {
            val f=File(Runtime.root,"no_backup/cpu-arena-off/cpu.config"); f.parentFile.mkdirs(); f.writeText("preserve")
            start(); failed(); expect(Runtime.creates==0 && Runtime.streams==0 && Runtime.decodes==0)
            expect(f.readText()=="preserve")
        } finally { Runtime.root.deleteRecursively() }
    }
    case("policy snapshot persistence failure blocks constructor") {
        reset(); try {
            Runtime.failWhen={j->j.optString("phase")=="allocator-policy"}
            start(); failed(); expect(Runtime.creates==0 && Runtime.streams==0 && Runtime.decodes==0)
            expect(!json().isNull("wrapperPersistErrorClass"))
        } finally { Runtime.root.deleteRecursively() }
    }
    case("config changed after policy snapshot is rejected before constructor") {
        reset(); try {
            Runtime.failWhen={j->if(j.optString("phase")=="allocator-policy") {
                File(j.getJSONObject("allocatorPolicy").getString("configPath")).writeText("EnableCpuMemArena=1\n")
            };false}
            start(); failed(); expect(Runtime.creates==0 && Runtime.streams==0 && Runtime.decodes==0)
            expect(!json().isNull("errorClass"))
        } finally { Runtime.root.deleteRecursively() }
    }

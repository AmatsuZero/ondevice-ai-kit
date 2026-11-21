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
    case("fresh lock after policy snapshot blocks every native call") {
        reset(); try {
            Runtime.failWhen={j->if(j.optString("phase")=="allocator-policy") Runtime.locked=true;false}
            start(); failed(); expect(Runtime.creates==0 && Runtime.streams==0 && Runtime.decodes==0)
            expect(File(json().getJSONObject("allocatorPolicy").getString("configPath")).readText()=="EnableCpuMemArena=0\n")
        } finally { Runtime.root.deleteRecursively() }
    }
    case("initial running failed and terminal snapshot attempts retain truthful policy metadata") {
        for (decodeFailure in listOf(false,true)) {
            reset(); try {
                Runtime.decodeFail=decodeFailure; start()
                expect(Runtime.snapshotAttempts.size>=4)
                val first=Runtime.snapshotAttempts.first()
                expect(first.isNull("allocatorPolicy") && first.getJSONObject("config").isNull("provider"))
                var policySeen=false
                for (j in Runtime.snapshotAttempts) {
                    expect(j.getString("probeVariant")=="cpu-arena-off-v1" && !j.getBoolean("nativePolicyEffectVerified"))
                    val c=j.getJSONObject("config")
                    expect(!c.getBoolean("requestedEnableCpuMemArena"))
                    if(j.isNull("allocatorPolicy")) {
                        expect(!policySeen && c.isNull("provider"))
                    } else {
                        policySeen=true
                        val p=j.getJSONObject("allocatorPolicy")
                        expect(p.keySet()==setOf("provider","configPath","bytes","sha256"))
                        expect(p.getString("provider")==c.getString("provider"))
                    }
                }
                expect(policySeen && Runtime.snapshotAttempts.last().getBoolean("terminal"))
                expect(Runtime.snapshotAttempts.last().getString("status")==if(decodeFailure)"failed" else "completed")
            } finally { Runtime.root.deleteRecursively() }
        }
    }

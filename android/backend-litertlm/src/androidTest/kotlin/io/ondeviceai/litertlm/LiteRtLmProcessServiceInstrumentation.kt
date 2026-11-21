package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LiteRtLmProcessServiceInstrumentation {
    @Test
    fun processServiceTransportAndLifecycleWithoutNativeAdmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(
            context.packageName,
            "io.ondeviceai.litertlm.LiteRtLmProcessService",
        )
        val expectedProcessName = "${context.packageName}:odai_litertlm"
        val receipt = Receipt(context.filesDir, component, expectedProcessName)
        val cycles = mutableListOf<Cycle>()
        val cacheRoots = mutableListOf<File>()
        var stage = "setup"
        var failure: Throwable? = null
        try {
            @Suppress("DEPRECATION")
            val serviceInfo = context.packageManager.getServiceInfo(component, 0)
            assertFalse("service must not be exported", serviceInfo.exported)
            assertEquals(expectedProcessName, serviceInfo.processName)

            val first = newCycle(component, receipt, "first")
            cycles += first
            stage = "first-bind"
            bindAndRegisterDeath(context, first)

            val firstNonce = uuid()
            stage = "first-hello"
            val firstHello = hello(first, firstNonce)
            assertHello(firstHello, firstNonce, expectedProcessName)
            first.ownedNonce = firstNonce

            stage = "wrong-nonce-status"
            val wrongStatus = status(first, uuid())
            assertEquals(Protocol.REJECT, wrongStatus.result)
            assertTrue("wrong-nonce STATUS must return an empty object",
                JSONObject(wrongStatus.rawJson).length() == 0)

            stage = "initial-owner-status"
            assertReservedStatus(status(first, firstNonce), firstNonce)

            stage = "competing-hello"
            val competingHello = hello(first, uuid())
            assertEquals(Protocol.REJECT, competingHello.result)
            assertReservedStatus(status(first, firstNonce), firstNonce)

            stage = "invalid-starts"
            invalidStarts(context, first, firstNonce, cacheRoots)
            assertReservedStatus(status(first, firstNonce), firstNonce)

            stage = "unknown-transaction"
            assertUnknownTransactionUnimplemented(first)

            stage = "cancel"
            assertEquals(Protocol.OK, simpleOwnerTransaction(first, Protocol.CANCEL, firstNonce))
            assertCanceledStatus(status(first, firstNonce), firstNonce)

            stage = "hello-after-cancel"
            assertEquals(Protocol.REJECT, hello(first, uuid()).result)

            stage = "first-retire"
            retireAndRequireDeath(first)
            unbind(context, first)

            val second = newCycle(component, receipt, "second")
            cycles += second
            stage = "second-bind"
            bindAndRegisterDeath(context, second)

            val secondNonce = uuid()
            stage = "second-hello"
            val secondHello = hello(second, secondNonce)
            assertHello(secondHello, secondNonce, expectedProcessName)
            second.ownedNonce = secondNonce
            assertTrue("fresh service incarnation must use a different PID",
                firstHello.pid != secondHello.pid)

            stage = "second-initial-status"
            assertReservedStatus(status(second, secondNonce), secondNonce)

            stage = "second-retire"
            retireAndRequireDeath(second)
            unbind(context, second)
        } catch (t: Throwable) {
            failure = t
            receipt.addError(stage, t)
        } finally {
            cycles.forEach { cycle ->
                cleanupCycle(context, cycle).forEach { (cleanupStage, error) ->
                    receipt.addError(cleanupStage, error)
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            cacheRoots.forEach { root ->
                try {
                    if (root.exists()) {
                        assertTrue("test-owned cache root must be empty and removable", root.delete())
                    }
                } catch (t: Throwable) {
                    receipt.addError("cache-root-cleanup", t)
                    if (failure == null) failure = t else failure!!.addSuppressed(t)
                }
            }
            receipt.passed = failure == null
            try {
                receipt.writeAtomically()
            } catch (t: Throwable) {
                receipt.passed = false
                receipt.addError("receipt-write", t)
                if (failure == null) failure = t else failure!!.addSuppressed(t)
            }
        }
        failure?.let { throw it }
    }

    private fun newCycle(
        component: ComponentName,
        receipt: Receipt,
        label: String,
    ): Cycle = Cycle(component, label).also(receipt::addCycle)

    private fun bindAndRegisterDeath(context: Context, cycle: Cycle) {
        val bound = context.bindService(
            Intent().setComponent(cycle.component),
            cycle.connection,
            Context.BIND_AUTO_CREATE,
        )
        cycle.bindReturned = bound
        assertTrue("explicit service bind must succeed", bound)
        assertTrue("service connection must arrive within 10 seconds",
            cycle.connected.await(10, TimeUnit.SECONDS))
        val binder = cycle.binder.get()
        assertNotNull("ServiceConnection did not provide a binder", binder)
        cycle.retainedBinder = binder!!
        cycle.deathRegistrationAttempted = true
        val identity = System.identityHashCode(binder)
        val recipient = object : IBinder.DeathRecipient {
            override fun binderDied() {
                cycle.deathCallbacks.incrementAndGet()
                if (cycle.deathObserved.compareAndSet(false, true)) {
                    val evidence = JSONObject()
                        .put("event", "binderDied")
                        .put("registeredBinderIdentity", identity)
                        .put("exactRegisteredBinder", true)
                    cycle.deathEvidence.set(evidence)
                    cycle.recordEvent(evidence)
                    cycle.death.countDown()
                }
            }
        }
        cycle.deathRecipient = recipient
        binder.linkToDeath(recipient, 0)
        cycle.deathRegistrationSucceeded = true
        cycle.recordEvent(JSONObject()
            .put("event", "deathRecipientRegistered")
            .put("binderIdentity", identity))
    }

    private fun hello(cycle: Cycle, nonce: String): Hello {
        return withParcels { data, reply ->
            data.writeInterfaceToken(Protocol.DESCRIPTOR)
            data.writeString(nonce)
            val handled = cycle.retainedBinder!!.transact(Protocol.HELLO, data, reply, 0)
            assertTrue("HELLO transaction must be handled", handled)
            val result = reply.readInt()
            if (result == Protocol.OK) {
                if (cycle.ownedNonce == null) cycle.ownedNonce = nonce
                val echo = reply.readString()
                val pid = reply.readInt()
                val processName = reply.readString()
                val binderIdentity = System.identityHashCode(cycle.retainedBinder!!)
                if (cycle.identity == null) {
                    cycle.identity = JSONObject()
                        .put("ownerNonce", nonce)
                        .put("echoNonce", echo ?: JSONObject.NULL)
                        .put("pid", pid)
                        .put("processName", processName ?: JSONObject.NULL)
                        .put("binderIdentity", binderIdentity)
                }
                cycle.recordEvent(JSONObject()
                    .put("event", "hello")
                    .put("requestNonce", nonce)
                    .put("result", result)
                    .put("echoNonce", echo ?: JSONObject.NULL)
                    .put("pid", pid)
                    .put("processName", processName ?: JSONObject.NULL)
                    .put("binderIdentity", binderIdentity))
                assertEquals("successful HELLO must have no trailing reply", 0, reply.dataAvail())
                Hello(result, echo, pid, processName)
            } else {
                cycle.recordEvent(JSONObject()
                    .put("event", "hello")
                    .put("requestNonce", nonce)
                    .put("result", result))
                Hello(result, null, null, null)
            }
        }
    }

    private fun assertHello(
        hello: Hello,
        nonce: String,
        expectedProcessName: String,
    ) {
        assertEquals(Protocol.OK, hello.result)
        assertEquals(nonce, hello.echoNonce)
        assertNotNull(hello.pid)
        assertTrue("service PID must differ from the instrumentation process",
            hello.pid != Process.myPid())
        assertEquals(expectedProcessName, hello.processName)
    }

    private fun status(cycle: Cycle, nonce: String): StatusReply =
        withParcels { data, reply ->
            data.writeInterfaceToken(Protocol.DESCRIPTOR)
            data.writeString(nonce)
            val handled = cycle.retainedBinder!!.transact(Protocol.STATUS, data, reply, 0)
            assertTrue("STATUS transaction must be handled", handled)
            val result = reply.readInt()
            val rawJson = reply.readString()
            cycle.recordEvent(JSONObject()
                .put("event", "status")
                .put("requestNonce", nonce)
                .put("result", result)
                .put("rawJson", rawJson ?: JSONObject.NULL))
            assertNotNull("STATUS JSON is required", rawJson)
            val exactRaw = rawJson!!
            assertEquals("STATUS reply must have no trailing data", 0, reply.dataAvail())
            StatusReply(result, exactRaw)
        }

    private fun assertReservedStatus(reply: StatusReply, nonce: String) {
        assertEquals(Protocol.OK, reply.result)
        val json = JSONObject(reply.rawJson)
        assertExactStatusKeys(json)
        assertEquals(nonce, json.strictString("nonce"))
        assertEquals("RESERVED", json.strictString("phase"))
        assertEquals("NONE", json.strictString("generation"))
        assertEquals(0, json.getJSONArray("snapshots").length())
        assertFalse(json.strictBoolean("nativeAttempted"))
        assertFalse(json.strictBoolean("nativeInitialized"))
        assertTrue(json.get("errorCode") === JSONObject.NULL)
        assertEquals("UNPROVEN", json.strictString("callbackMode"))
    }

    private fun assertCanceledStatus(reply: StatusReply, nonce: String) {
        assertEquals(Protocol.OK, reply.result)
        val json = JSONObject(reply.rawJson)
        assertExactStatusKeys(json)
        assertEquals(nonce, json.strictString("nonce"))
        assertEquals("TERMINAL", json.strictString("phase"))
        assertEquals("CANCELED", json.strictString("generation"))
        assertEquals(0, json.getJSONArray("snapshots").length())
        assertFalse(json.strictBoolean("nativeAttempted"))
        assertFalse(json.strictBoolean("nativeInitialized"))
        assertTrue(json.get("errorCode") === JSONObject.NULL)
        assertEquals("UNPROVEN", json.strictString("callbackMode"))
    }

    private fun assertExactStatusKeys(json: JSONObject) {
        val actual = mutableSetOf<String>()
        val keys = json.keys()
        while (keys.hasNext()) actual += keys.next()
        assertEquals(
            setOf(
                "nonce", "phase", "generation", "snapshots", "nativeAttempted",
                "nativeInitialized", "errorCode", "callbackMode",
            ),
            actual,
        )
    }

    private fun invalidStarts(
        context: Context,
        cycle: Cycle,
        ownerNonce: String,
        cacheRoots: MutableList<File>,
    ) {
        fun send(
            label: String,
            modelPath: String = File(context.filesDir, "absent-model-${uuid()}").absolutePath,
            prompt: String = "test-only",
            maxTokens: Int = 1,
            policy: String = "CPU_ONLY",
            trailing: Boolean = false,
            truncated: Boolean = false,
        ) {
            val cache = File(context.filesDir, "litertlm-test-cache-${uuid()}")
            assertFalse("unique cache root unexpectedly exists", cache.exists())
            assertTrue("test-owned cache root must be created", cache.mkdir())
            cacheRoots += cache
            val imagePath = File(context.filesDir, "absent-image-${uuid()}").absolutePath
            cycle.recordEvent(JSONObject()
                .put("event", "invalidStartAttempt")
                .put("case", label))
            val result = withParcels { data, reply ->
                data.writeInterfaceToken(Protocol.DESCRIPTOR)
                data.writeString(ownerNonce)
                data.writeString(modelPath)
                if (!truncated) {
                    data.writeString(imagePath)
                    data.writeString(cache.absolutePath)
                    data.writeString(prompt)
                    data.writeInt(maxTokens)
                    if (!truncated) data.writeString(policy)
                    if (trailing) data.writeString("trailing-test-data")
                } else {
                    data.writeString(imagePath)
                    data.writeString(cache.absolutePath)
                    data.writeString(prompt)
                    data.writeInt(0)
                }
                val handled = cycle.retainedBinder!!.transact(Protocol.START, data, reply, 0)
                assertTrue("$label START transaction must be handled", handled)
                val code = reply.readInt()
                assertEquals("$label must explicitly return RESULT_REJECT", Protocol.REJECT, code)
                assertEquals("$label reply must contain only the result", 0, reply.dataAvail())
                code
            }
            cycle.recordEvent(JSONObject()
                .put("event", "invalidStart")
                .put("case", label)
                .put("result", result))
        }

        send("zero-max-output-tokens", maxTokens = 0)
        send("missing-model-path", modelPath = "")
        send("nonabsolute-model-path", modelPath = "relative-model")
        send("empty-prompt", prompt = "")
        send("bad-policy", policy = "NOT_A_POLICY")
        send("trailing-data", maxTokens = 0, trailing = true)
        send("truncated-start", truncated = true)
    }

    private fun simpleOwnerTransaction(cycle: Cycle, code: Int, nonce: String): Int =
        withParcels { data, reply ->
            data.writeInterfaceToken(Protocol.DESCRIPTOR)
            data.writeString(nonce)
            val handled = cycle.retainedBinder!!.transact(code, data, reply, 0)
            assertTrue("owner transaction must be handled", handled)
            val result = reply.readInt()
            assertEquals("owner transaction reply must contain only its result", 0, reply.dataAvail())
            result
        }

    private fun assertUnknownTransactionUnimplemented(cycle: Cycle) {
        val handled = withParcels { data, reply ->
            data.writeInterfaceToken(Protocol.DESCRIPTOR)
            data.writeString(uuid())
            cycle.retainedBinder!!.transact(Protocol.UNKNOWN, data, reply, 0)
        }
        cycle.recordEvent(JSONObject()
            .put("event", "unknownTransaction")
            .put("transactionHandled", handled))
        assertFalse("unknown transaction must return false, not RESULT_REJECT", handled)
    }

    private fun retireAndRequireDeath(cycle: Cycle) {
        val binder = cycle.retainedBinder
        val nonce = cycle.ownedNonce
        assertNotNull("RETIRE requires the exact retained binder", binder)
        assertNotNull("RETIRE requires a reserved owner nonce", nonce)
        cycle.retireAttempted = true

        var handled: Boolean? = null
        var result: Int? = null
        var error: Exception? = null
        try {
            withParcels { data, reply ->
                data.writeInterfaceToken(Protocol.DESCRIPTOR)
                data.writeString(nonce)
                handled = binder!!.transact(Protocol.RETIRE, data, reply, 0)
                if (handled == true && reply.dataAvail() >= Int.SIZE_BYTES) {
                    result = reply.readInt()
                    cycle.retireReplyTrailingBytes = reply.dataAvail()
                }
            }
        } catch (e: Exception) {
            error = e
        }
        cycle.recordEvent(JSONObject()
            .put("event", "retire")
            .put("transactionHandled", handled)
            .put("result", result)
            .put("errorClass", error?.javaClass?.name ?: JSONObject.NULL))

        var awaitError: InterruptedException? = null
        val died = try {
            cycle.death.await(20, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            awaitError = e
            false
        }
        cycle.deathAwaited = true
        cycle.recordEvent(JSONObject()
            .put("event", "deathAwait")
            .put("observed", died)
            .put("callbackCount", cycle.deathCallbacks.get())
            .put("registeredBinderIdentity", System.identityHashCode(binder))
            .put("exactRegisteredBinder", cycle.deathRegistrationSucceeded)
            .put("awaitErrorClass", awaitError?.javaClass?.name ?: JSONObject.NULL))

        assertTrue("exact registered Binder death must arrive within 20 seconds", died)
        assertTrue("death must come from the registered Binder recipient",
            cycle.deathRegistrationSucceeded && cycle.deathObserved.get())
        if (handled == true && result != null) {
            assertEquals("normal RETIRE reply must be RESULT_OK", Protocol.OK, result)
            assertEquals("RETIRE reply must contain only its result", 0,
                cycle.retireReplyTrailingBytes)
        }
    }

    private fun unbind(context: Context, cycle: Cycle) {
        if (!cycle.bindReturned || cycle.unbindAttempted) return
        cycle.unbindAttempted = true
        context.unbindService(cycle.connection)
        cycle.unbound = true
        cycle.recordEvent(JSONObject().put("event", "unbind"))
    }

    private fun cleanupCycle(context: Context, cycle: Cycle): List<Pair<String, Throwable>> {
        val errors = mutableListOf<Pair<String, Throwable>>()
        if (cycle.ownedNonce != null && cycle.retainedBinder != null && !cycle.deathObserved.get()
            && !cycle.retireAttempted
        ) {
            try {
                retireAndRequireDeath(cycle)
            } catch (t: Throwable) {
                errors += "cleanup-retire" to t
            }
        }
        if (cycle.bindReturned && !cycle.unbindAttempted) {
            try {
                unbind(context, cycle)
            } catch (t: Throwable) {
                errors += "cleanup-unbind" to t
            }
        }
        return errors
    }

    private inline fun <T> withParcels(block: (Parcel, Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            return block(data, reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private data class Hello(
        val result: Int,
        val echoNonce: String?,
        val pid: Int?,
        val processName: String?,
    )

    private data class StatusReply(val result: Int, val rawJson: String)

    private class Cycle(
        val component: ComponentName,
        val label: String,
    ) {
        val connected = CountDownLatch(1)
        val death = CountDownLatch(1)
        val binder = AtomicReference<IBinder?>()
        val deathObserved = AtomicBoolean(false)
        val deathCallbacks = AtomicInteger(0)
        val deathEvidence = AtomicReference<JSONObject?>()
        private val events = mutableListOf<JSONObject>()
        private val eventLock = Any()
        val connection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder.set(service)
                recordEvent(JSONObject().put("event", "serviceConnected"))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                recordEvent(JSONObject().put("event", "serviceDisconnected"))
            }

            override fun onBindingDied(name: ComponentName) {
                recordEvent(JSONObject().put("event", "bindingDied"))
            }

            override fun onNullBinding(name: ComponentName) {
                recordEvent(JSONObject().put("event", "nullBinding"))
            }
        }

        @Volatile var retainedBinder: IBinder? = null
        @Volatile var deathRecipient: IBinder.DeathRecipient? = null
        @Volatile var deathRegistrationAttempted = false
        @Volatile var deathRegistrationSucceeded = false
        @Volatile var bindReturned = false
        @Volatile var unbindAttempted = false
        @Volatile var unbound = false
        @Volatile var retireAttempted = false
        @Volatile var deathAwaited = false
        @Volatile var retireReplyTrailingBytes = -1
        @Volatile var ownedNonce: String? = null
        @Volatile var identity: JSONObject? = null

        fun recordEvent(event: JSONObject) {
            synchronized(eventLock) { events += event }
        }

        fun toJson(): JSONObject {
            val eventSnapshot = synchronized(eventLock) { events.toList() }
            val eventArray = JSONArray()
            eventSnapshot.forEach { eventArray.put(it) }
            return JSONObject()
                .put("label", label)
                .put("ownerNonce", ownedNonce)
                .put("helloIdentity", identity)
                .put("deathRegistrationAttempted", deathRegistrationAttempted)
                .put("deathRegistrationSucceeded", deathRegistrationSucceeded)
                .put("deathObserved", deathObserved.get())
                .put("deathEvidence", deathEvidence.get())
                .put("deathAwaited", deathAwaited)
                .put("events", eventArray)
        }
    }

    private class Receipt(
        private val filesDir: File,
        private val component: ComponentName,
        private val processName: String,
    ) {
        private val id = uuid()
        private val errors = JSONArray()
        @Volatile var passed = false
        private val root = JSONObject()
            .put("passed", false)
            .put("nativeInferenceAttempted", false)
            .put("receiptId", id)
            .put("serviceComponent", component.flattenToString())
            .put("processName", processName)
            .put("errors", errors)

        @Synchronized
        fun addError(stage: String, error: Throwable) {
            errors.put(JSONObject()
                .put("stage", stage)
                .put("errorClass", error.javaClass.name))
        }

        fun writeAtomically() {
            val cycles = JSONArray()
            cycleRecords.forEach { cycles.put(it.toJson()) }
            root.put("cycles", cycles)
            root.put("passed", passed)
            val destination = File(filesDir, "litertlm-production-service-contract-$id.json")
            val temporary = File(filesDir, ".litertlm-production-service-contract-$id.tmp")
            check(!destination.exists() && !temporary.exists()) {
                "unique receipt destination already exists"
            }
            var createdTemporary = false
            var failure: Throwable? = null
            try {
                createdTemporary = true
                FileOutputStream(temporary, false).use { output ->
                    output.write(root.toString().toByteArray(StandardCharsets.UTF_8))
                    output.fd.sync()
                }
                check(temporary.renameTo(destination)) { "atomic receipt rename failed" }
                createdTemporary = false
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                if (createdTemporary && temporary.exists()) {
                    try {
                        check(temporary.delete()) { "receipt temporary file cleanup failed" }
                    } catch (cleanup: Throwable) {
                        if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
                    }
                }
            }
        }

        private val cycleRecords = mutableListOf<Cycle>()

        @Synchronized
        fun addCycle(cycle: Cycle) {
            cycleRecords += cycle
        }
    }

    private object Protocol {
        const val DESCRIPTOR = "io.ondeviceai.litertlm.process.v1"
        const val HELLO = 1
        const val START = 2
        const val STATUS = 3
        const val CANCEL = 4
        const val RETIRE = 5
        const val OK = 0
        const val REJECT = 1
        const val UNKNOWN = 0x00F00D
    }
}

private fun uuid(): String = UUID.randomUUID().toString()

private fun JSONObject.strictString(name: String): String {
    val value = get(name)
    assertTrue("$name must be a JSON string", value is String)
    return value as String
}

private fun JSONObject.strictBoolean(name: String): Boolean {
    val value = get(name)
    assertTrue("$name must be a JSON boolean", value is Boolean)
    return value as Boolean
}

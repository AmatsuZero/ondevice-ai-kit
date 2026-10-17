package io.ondeviceai.systemasr

import io.ondeviceai.speech.SpeechRecognitionRequest
import io.ondeviceai.core.ClosedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeEngine(private val listener: RecognitionEngineListener) : RecognitionEngine {
    var starts = 0
    var cancels = 0
    var destroys = 0
    var startFailure: Throwable? = null
    var destroyFailure: Throwable? = null
    override fun start(locale: String) { starts++; startFailure?.let { throw it } }
    override fun cancel() { cancels++ }
    override fun destroy() { destroys++; destroyFailure?.let { throw it } }
    fun result(text: String, confidence: Float? = null) = listener.onResult(text, confidence)
    fun error(code: Int = 7) = listener.onError(code)
}

private class FakeFactory : RecognitionEngineFactory {
    val engines = mutableListOf<FakeEngine>()
    var nextStartFailure: Throwable? = null
    override fun create(listener: RecognitionEngineListener): RecognitionEngine = FakeEngine(listener).also {
        it.startFailure = nextStartFailure
        nextStartFailure = null
        engines += it
    }
}

class RecognitionSessionTest {
    @Test
    fun cancellationReleasesEngineAndLateResultCannotCompleteNextRequest() { runBlocking { supervisorScope {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, timeoutMillis = 5000, dispatcher = Dispatchers.Unconfined)
        val first = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.isEmpty()) delay(1)
        val old = factory.engines.single()
        first.cancel()
        assertFailsWith<CancellationException> { first.await() }
        assertEquals(1, old.cancels)
        assertEquals(1, old.destroys)

        val second = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.size < 2) delay(1)
        val current = factory.engines[1]
        old.result("late")
        current.result("current", .8f)
        assertEquals("current", second.await().transcript)
    } } }

    @Test
    fun closeIsIdempotentCancelsActiveAndRejectsNewWork() { runBlocking { supervisorScope {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, timeoutMillis = 5000, dispatcher = Dispatchers.Unconfined)
        val request = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.isEmpty()) delay(1)
        session.close()
        session.close()
        assertFailsWith<ClosedException> { request.await() }
        assertEquals(1, factory.engines.single().cancels)
        assertEquals(1, factory.engines.single().destroys)
        assertFailsWith<ClosedException> { session.recognize(SpeechRecognitionRequest("en-US")) }
    } } }

    @Test
    fun startErrorNoResultAndRecognitionErrorAlwaysReleaseEngine() { runBlocking { supervisorScope {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, timeoutMillis = 5000, dispatcher = Dispatchers.Unconfined)
        factory.nextStartFailure = IllegalStateException("start")
        assertFailsWith<IllegalStateException> { session.recognize(SpeechRecognitionRequest("en-US")) }
        assertEquals(1, factory.engines.single().destroys)

        val noResult = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.size < 2) delay(1)
        factory.engines[1].result("")
        assertFailsWith<AsrNoResultException> { noResult.await() }

        val error = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.size < 3) delay(1)
        factory.engines[2].error(13)
        assertFailsWith<AsrRecognitionException> { error.await() }
        assertTrue(factory.engines.all { it.destroys == 1 })
    } } }

    @Test
    fun timeoutDestroysEngineAndBusyRequestIsRejected() { runBlocking { supervisorScope {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, timeoutMillis = 20, dispatcher = Dispatchers.Unconfined)
        val first = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.isEmpty()) delay(1)
        assertFailsWith<AsrTimeoutException> { withTimeout(1000) { first.await() } }
        assertEquals(1, factory.engines.single().destroys)
        val next = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.size < 2) delay(1)
        assertFalse(next.isCompleted)
        factory.engines[1].result("ok")
        assertEquals("ok", next.await().transcript)
    } } }

    @Test
    fun callerTimeoutRemainsCallerCancellation() { runBlocking {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, timeoutMillis = 5000, dispatcher = Dispatchers.Unconfined)
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(20) { session.recognize(SpeechRecognitionRequest("en-US")) }
        }
        assertEquals(1, factory.engines.single().destroys)
    } }

    @Test
    fun cleanupFailureCannotBeReportedAsSuccessfulRecognition() { runBlocking { supervisorScope {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, dispatcher = Dispatchers.Unconfined)
        val task = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.isEmpty()) delay(1)
        val failure = IllegalStateException("destroy failed")
        factory.engines.single().destroyFailure = failure
        factory.engines.single().result("hello")
        val error = assertFailsWith<IllegalStateException> { task.await() }
        assertEquals(failure.message, error.message)
        assertEquals(1, factory.engines.single().destroys)
    } } }

    @Test
    fun busyRequestDoesNotRetireActiveEngine() { runBlocking { supervisorScope {
        val factory = FakeFactory()
        val session = RecognitionSession(factory, dispatcher = Dispatchers.Unconfined)
        val task = async { session.recognize(SpeechRecognitionRequest("en-US")) }
        while (factory.engines.isEmpty()) delay(1)
        assertFailsWith<AsrBusyException> { session.recognize(SpeechRecognitionRequest("en-US")) }
        assertEquals(0, factory.engines.single().destroys)
        factory.engines.single().result("hello")
        assertEquals("hello", task.await().transcript)
    } } }
}

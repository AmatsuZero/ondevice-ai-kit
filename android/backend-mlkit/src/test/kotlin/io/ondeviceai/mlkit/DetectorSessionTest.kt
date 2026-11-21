package io.ondeviceai.mlkit
import kotlin.test.*
import kotlinx.coroutines.*
import io.ondeviceai.core.*
class DetectorSessionTest {
 @Test fun closeWaitsForBackendAndReleasesResourcesExactlyOnce()=runBlocking<Unit> {
  var releases=0
  val session=DetectorSession { releases++ }
  val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
  val request=async { session.execute { entered.complete(Unit);finish.await();"result" } }
  entered.await()
  val closing=async(start=CoroutineStart.UNDISPATCHED){session.close()}
  assertFalse(closing.isCompleted);assertEquals(0,releases)
  assertFailsWith<ClosedException>{session.execute { "later" }}
  finish.complete(Unit)
  assertFailsWith<CancellationException>{request.await()}
  closing.await();session.close();assertEquals(1,releases)
 }
 @Test fun cancellationWaitsForNonCancellableBackendBeforeReadmission()=runBlocking<Unit> {
  val session=DetectorSession{}
  val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
  val request=async {session.execute {entered.complete(Unit);finish.await();"result"}}
  entered.await();request.cancel();yield()
  assertFalse(request.isCompleted)
  assertFailsWith<BusyException>{session.execute {"later"}}
  finish.complete(Unit);request.join()
  assertEquals("next",session.execute {"next"})
  session.close()
 }
}

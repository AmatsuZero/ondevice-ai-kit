package io.ondeviceai.demo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.ondeviceai.binding.RealtimeSRMode
import io.ondeviceai.realtimesr.RealtimePlaybackSession
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises real decoder open/reopen and direct fallback without a production file-path hook. */
class PlaybackSessionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        var activity: Activity? = null
        var session: RealtimePlaybackSession? = null
        var ownsRecovery = false
        val recovery = File(targetContext.cacheDir, "sr-session-recovery.mp4")
        try {
            val input = File(targetContext.cacheDir, "sr-session-test-input.mp4")
            check(input.isFile) { "Supply the synthetic test fixture in target cache first" }
            check(!recovery.exists()) { "Recovery fixture already exists" }
            ownsRecovery = true
            val ended = CountDownLatch(1)
            var direct = false
            var failureMessage: String? = null
            val current = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { activity = it }
            val ready = CountDownLatch(1)
            lateinit var player: RealtimePlaybackSession
            runOnMainSync {
                player = RealtimePlaybackSession(current, useDirectOutput = { direct },
                    onRunEnded = { ended.countDown() }, onRunError = { failureMessage = it })
                session = player
                val surface = SurfaceView(current)
                surface.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        player.surfaceCreated(holder.surface); ready.countDown()
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = player.surfaceChanged(holder.surface)
                    override fun surfaceDestroyed(holder: SurfaceHolder) = player.surfaceDestroyed()
                })
                current.setContentView(surface)
                player.start(); player.resume()
                check(player.selectRealtimeMode(RealtimeSRMode.CUNNY))
                direct = true
            }
            check(ready.await(10, TimeUnit.SECONDS)) { "Surface not created" }
            runOnMainSync { player.selectSource(Uri.fromFile(recovery)) }
            await("missing source failure") {
                !player.isPlaying && failureMessage != null
            }
            val failure = onMain { player.statusText }
            input.copyTo(recovery)
            // Same URI and requested mode; foreground recreation must clear the old failure.
            runOnMainSync { player.stop(); player.start(); player.resume() }
            await("current successful direct reopen") {
                player.srStatus.fellBackToOriginal && player.statusText != failure &&
                    !player.statusText.contains(failureMessage!!)
            }
            onMain {
                check(player.selectedMode == RealtimeSRMode.CUNNY)
                check(player.supportedModes == listOf(RealtimeSRMode.ORIGINAL))
                check(player.srStatus.presentedBackend == null)
            }
            result.putString("directFallback", "requested=CUNNY; available=ORIGINAL; fallback=true; presented=null")
            result.putString("sameSourceRecovery", "old error cleared after codec configuration")
            runOnMainSync { player.togglePlayback() }
            await("EOS") { ended.count == 0L && !player.isPlaying }
            runOnMainSync { player.togglePlayback() }
            await("replay") { player.isPlaying && player.mediaPositionUs in 1..1_500_000L }
            result.putString("eosReplay", "ended, then replayed from start")
            runOnMainSync { player.leave() }
            await("leave cleanup") { !player.hasVideo && !player.isPlaying && !player.cleanupPending }
            check(onMain { player.srStatus.presentedBackend == null && !player.srStatus.fellBackToOriginal })
            result.putString("leave", "empty, paused, no pending cleanup or presentation")
            result.putString("probeStatus", "pass")
        } catch (error: Throwable) {
            result.putString("probeStatus", "fail")
            result.putString("failure", error.stackTraceToString())
        } finally {
            runOnMainSync { session?.close(); activity?.finish() }
            if (ownsRecovery) recovery.delete()
            finish(if (result.getString("probeStatus") == "pass") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < end) {
            if (onMain(condition)) return
            Thread.sleep(50)
        }
        error("Timed out: $label")
    }

    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        var failure: Throwable? = null
        runOnMainSync { try { value = block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }
}

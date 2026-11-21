package io.ondeviceai.demo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.util.concurrent.CountDownLatch

/**
 * Opt-in, temporary, visible probe Activity for the isolated ASR acceptance harness.
 *
 * SCOPE (bounded, source-only):
 *  - Plain [android.app.Activity]. No AndroidX, no external dependencies, no layout XML.
 *  - Renders a single STATIC public-ASR testing status string via a TextView.
 *  - Performs NO inference, NO model load, NO media capture/playback, NO permission use,
 *    NO Service, NO logging, NO wake-lock, NO keep-screen-on, NO global flag, NO settings
 *    mutation, and reads NO user data.
 *  - It is declared non-exported in the harness (target main APK) manifest and is launched
 *    only by the harness via NEW_TASK for an explicitly opted-in run, so a physical observer
 *    can confirm a visible foreground window existed during a bounded resource observation.
 *  - It MUST NOT start inference itself.
 *
 * Lifecycle acknowledgement (test-owned, public SDK only):
 *  - Small per-instance fields and a resume latch are written inside the public lifecycle
 *    callbacks onResume/onPause/onDestroy. They only RECORD that a callback actually ran;
 *    the Activity performs NO inference about visibility, focus, or freezer/battery state.
 *  - The instrumentation worker bounded-waits on [resumeArrived] off the main thread and
 *    reads/finishes the exact instance through runOnMainSync.
 *  - No hidden/system getter (e.g. Activity.isResumed) is used, and NO static/global
 *    Activity reference is kept. The harness clears its reference after teardown.
 */
class ForegroundProbeActivity : Activity() {

    /** Counted down in onResume; the worker bounded-waits on this (never on Main). */
    val resumeArrived = CountDownLatch(1)

    @Volatile var resumeAcked = false
    @Volatile var pauseAcked = false
    @Volatile var destroyAcked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text =
                "On-device ASR acceptance: public natural-speech probe active (no inference, no capture)."
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(status)
    }

    override fun onResume() {
        super.onResume()
        resumeAcked = true
        resumeArrived.countDown()
    }

    override fun onPause() {
        pauseAcked = true
        super.onPause()
    }

    override fun onDestroy() {
        destroyAcked = true
        super.onDestroy()
    }
}

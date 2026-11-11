package io.ondeviceai.acceptance.qwenfile

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.util.concurrent.CountDownLatch

/**
 * Opt-in, temporary, visible foreground Activity for the isolated file-recognition harness.
 *
 * Bounded, source-only scope:
 *  - Plain [android.app.Activity], no AndroidX, no layout XML, no external deps.
 *  - Renders ONE static public status string via a TextView; it only SHOWS status.
 *  - It NEVER loads, decodes, or captures: NO inference, NO model/native load,
 *    NO file decode, NO media capture/playback, NO permissions, NO Service,
 *    NO logging, NO wake-lock, NO keep-screen-on, NO global/static reference,
 *    NO settings mutation, NO user data.
 *  - Declared android:exported="false" in the parent-owned harness manifest and
 *    launched only via explicit Instrumentation. The Activity requests no work and
 *    MUST NOT load or decode itself.
 *
 * Lifecycle acknowledgement (public SDK only): small per-instance flags and two
 * latches are written inside onResume/onPause/onDestroy; they only RECORD that a
 * callback ran. The worker bounded-waits off the main thread and reads/finishes the
 * exact instance through runOnMainSync. No hidden/system getter, no static reference.
 */
class QwenFileActivity : Activity() {

    val resumeArrived = CountDownLatch(1)
    val destroyArrived = CountDownLatch(1)

    @Volatile var resumeAcked = false
    @Volatile var pauseAcked = false
    @Volatile var destroyAcked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "On-device ASR acceptance: public file recognition probe active (no capture)."
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
        destroyArrived.countDown()
        super.onDestroy()
    }
}

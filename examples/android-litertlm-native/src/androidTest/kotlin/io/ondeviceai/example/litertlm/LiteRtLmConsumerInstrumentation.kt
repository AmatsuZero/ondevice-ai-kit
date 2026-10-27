package io.ondeviceai.example.litertlm

import android.content.Intent
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the real Activity and app-private empty store, without loading model weights. */
class LiteRtLmConsumerInstrumentation {
    @Test
    fun emptyStoreDisplaysMissingAssets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val expected = "Backend: litertlm.android\nStatus: REQUIRES_ASSETS\n" +
                "Reason: MISSING_ASSETS\nMinimum Android API: 26"
            var displayed = ""
            val deadline = SystemClock.elapsedRealtime() + 5_000
            do {
                instrumentation.runOnMainSync {
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    displayed = (content.getChildAt(0) as TextView).text.toString()
                }
                if (displayed == expected) break
                SystemClock.sleep(25)
            } while (SystemClock.elapsedRealtime() < deadline)
            assertEquals(expected, displayed)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}

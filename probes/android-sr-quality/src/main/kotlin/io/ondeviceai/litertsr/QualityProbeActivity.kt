package io.ondeviceai.litertsr

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/** Keep this test visible so OEM background freezing does not suspend its codecs. */
class QualityProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {
            text = "Video quality comparison is running.\nPlease keep this page visible."
            textSize = 20f
            setPadding(32, 64, 32, 32)
        })
    }
}

package io.ondeviceai.demo

import io.ondeviceai.binding.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoHostTest {
    @Test fun explicitPickerRoundTripCanReturnAfterActivityStopStart() {
        var reply: ((String?, String?) -> Unit)? = null
        val host = VideoHost(selectFile = { reply = it })
        host.selectVideoFile(); host.background(preservePicker = true); host.foreground()
        reply?.invoke("picked-token", "fixture.mov")
        assertEquals("picked-token", host.model.state.inputReference)
        host.close()
    }
    @Test fun missingBackendIsExplicitAndPickerCancelKeepsSelection() {
        var reply: ((String?, String?) -> Unit)? = null
        val host = VideoHost(selectFile = { reply = it })
        host.selectVideoFile(); reply?.invoke("opaque", "fixture.mov")
        host.selectVideoFile(); reply?.invoke(null, null)
        assertEquals("opaque", host.model.state.inputReference)
        host.model.process()
        assertEquals(VideoBridgeFailureCode.BACKEND_UNAVAILABLE, host.model.state.failure?.code)
        host.close()
    }

    @Test fun latePickerReplyAfterBackgroundLeaveOrCloseCannotSelectInput() {
        var reply: ((String?, String?) -> Unit)? = null
        val host = VideoHost(selectFile = { reply = it })
        host.selectVideoFile(); host.background(); reply?.invoke("late", "private.mov")
        assertNull(host.model.state.inputReference)
        host.foreground(); host.selectVideoFile(); host.leavePage(); reply?.invoke("late2", null)
        assertNull(host.model.state.inputReference)
        host.selectVideoFile(); host.close(); reply?.invoke("late3", null)
        assertNull(host.model.state.inputReference)
    }
}

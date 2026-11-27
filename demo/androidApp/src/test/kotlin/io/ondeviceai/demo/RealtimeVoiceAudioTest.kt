package io.ondeviceai.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeVoiceAudioTest {
    private fun device(type: LiveDeviceType, name: String? = null, sink: Boolean = true) =
        LiveDevice(type, name, sink)

    @Test
    fun wiredHeadphonesArePreferred() {
        val wired = device(LiveDeviceType.WIRED_HEADSET, "Cable")
        assertEquals(LiveRouteClass.WIRED_HEADPHONES, RoutePolicy.classify(wired))
        assertTrue(RoutePolicy.isHeadphones(wired))
    }

    @Test
    fun usbHeadsetMustBeIdentified() {
        assertTrue(RoutePolicy.isHeadphones(device(LiveDeviceType.USB_HEADSET, "Focusrite USB")))
        assertEquals(
            LiveRouteClass.USB_HEADSET_IDENTIFIED,
            RoutePolicy.classify(device(LiveDeviceType.USB_HEADSET, "Focusrite USB")),
        )
        assertFalse(RoutePolicy.isHeadphones(device(LiveDeviceType.USB_HEADSET)))
        assertEquals(
            LiveRouteClass.GENERIC_USB,
            RoutePolicy.classify(device(LiveDeviceType.USB_HEADSET, null)),
        )
    }

    @Test
    fun recognizedBluetoothHeadphonesOnly() {
        assertTrue(RoutePolicy.isHeadphones(device(LiveDeviceType.BLUETOOTH_SCO, "Pixel Buds")))
        assertTrue(RoutePolicy.isHeadphones(device(LiveDeviceType.BLUETOOTH_A2DP, "WH-1000XM5")))
        assertFalse(RoutePolicy.isHeadphones(device(LiveDeviceType.BLUETOOTH_A2DP, "Unknown Speaker")))
    }

    @Test
    fun genericUsbEarpieceAndVehicleRejected() {
        assertFalse(RoutePolicy.isHeadphones(device(LiveDeviceType.USB_DEVICE, "Generic USB")))
        assertFalse(RoutePolicy.isHeadphones(device(LiveDeviceType.BUILTIN_EARPIECE, "Earpiece")))
        val car = device(LiveDeviceType.BLUETOOTH_A2DP, "My Car Audio")
        assertEquals(LiveRouteClass.VEHICLE, RoutePolicy.classify(car))
        assertFalse(RoutePolicy.isHeadphones(car))
    }

    @Test
    fun sourcesAreRejected() {
        assertFalse(RoutePolicy.isHeadphones(device(LiveDeviceType.WIRED_HEADSET, "Mic", sink = false)))
    }

    @Test
    fun pickHeadphonesPrefersWiredThenUsbThenBt() {
        val bt = device(LiveDeviceType.BLUETOOTH_SCO, "Buds")
        val usb = device(LiveDeviceType.USB_HEADSET, "DAC")
        val wired = device(LiveDeviceType.WIRED_HEADPHONES, "Cable")
        assertEquals(wired, RoutePolicy.pickHeadphones(listOf(bt, usb, wired)))
        assertEquals(usb, RoutePolicy.pickHeadphones(listOf(bt, usb)))
        assertEquals(bt, RoutePolicy.pickHeadphones(listOf(bt)))
        assertNull(RoutePolicy.pickHeadphones(listOf(device(LiveDeviceType.BUILTIN_SPEAKER))))
    }

    private fun conditions(
        aec: Boolean = true,
        route: Boolean = true,
        mode: Boolean = true,
        source: Boolean = true,
        modify: Boolean = true,
        headset: Boolean = true,
    ) = SpeakerGuardPolicy.Conditions(modify, mode, source, route, aec, headset)

    @Test
    fun speakerAllowedOnlyWithAllGuards() {
        assertTrue(SpeakerGuardPolicy.canUseSpeaker(conditions()))
    }

    @Test
    fun speakerRejectedWithoutAec() {
        assertFalse(SpeakerGuardPolicy.canUseSpeaker(conditions(aec = false)))
    }

    @Test
    fun speakerRejectedWithoutVerifiedCommunicationRoute() {
        assertFalse(SpeakerGuardPolicy.canUseSpeaker(conditions(route = false)))
    }

    @Test
    fun speakerRejectedWithoutVoiceCommunicationSourceModeOrPermission() {
        assertFalse(SpeakerGuardPolicy.canUseSpeaker(conditions(source = false)))
        assertFalse(SpeakerGuardPolicy.canUseSpeaker(conditions(mode = false)))
        assertFalse(SpeakerGuardPolicy.canUseSpeaker(conditions(modify = false)))
    }

    @Test
    fun speakerRejectedWhenHeadphonesSelected() {
        assertFalse(SpeakerGuardPolicy.canUseSpeaker(conditions(headset = false)))
    }
}

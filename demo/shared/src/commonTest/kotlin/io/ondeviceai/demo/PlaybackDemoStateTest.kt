package io.ondeviceai.demo

import io.ondeviceai.binding.RealtimeSRMode
import kotlin.test.*

class PlaybackDemoStateTest {
    @Test fun pickerCancellationDoesNotInventPlayingState() {
        val player = FakePlayer()
        assertFalse(PlaybackDemoState.read(player, player).hasVideo)
        player.openPlayer()
        assertFalse(PlaybackDemoState.read(player, player).isPlaying)
        assertNull(PlaybackDemoState.read(player, player).presentedMode)
    }

    @Test fun snapshotsFollowActualPlaybackAndPresentationIndependently() {
        val player = FakePlayer()
        player.hasVideo = true
        player.isPlaying = true
        player.selectedMode = RealtimeSRMode.CUNNY
        assertTrue(PlaybackDemoState.read(player, player).isPlaying)
        assertNull(PlaybackDemoState.read(player, player).presentedMode)
        player.presentedMode = RealtimeSRMode.ORIGINAL
        player.fallbackToOriginal = true
        assertEquals(RealtimeSRMode.CUNNY, PlaybackDemoState.read(player, player).selectedMode)
        assertEquals(RealtimeSRMode.ORIGINAL, PlaybackDemoState.read(player, player).presentedMode)
        assertTrue(PlaybackDemoState.read(player, player).fallbackToOriginal)
        player.isPlaying = false
        assertFalse(PlaybackDemoState.read(player, player).isPlaying)
    }

    private class FakePlayer : PlaybackEnhancementDemoActions, RealtimeSRDemoActions {
        override val playerAvailable = true
        override val playerDescription = "test"
        override var hasVideo = false
        override var isPlaying = false
        override val supportedModes = listOf(RealtimeSRMode.ORIGINAL, RealtimeSRMode.CUNNY)
        override var selectedMode = RealtimeSRMode.ORIGINAL
        override var presentedMode: RealtimeSRMode? = null
        override var fallbackToOriginal = false
        override fun openPlayer() {}
        override fun selectMode(mode: RealtimeSRMode) = false
    }
}

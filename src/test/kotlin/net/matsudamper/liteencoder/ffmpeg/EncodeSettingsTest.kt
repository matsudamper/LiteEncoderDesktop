package net.matsudamper.liteencoder.ffmpeg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncodeSettingsTest {
    private val landscape = VideoInfo(displayWidth = 1920, displayHeight = 1080, frameRate = 29.97, durationSeconds = 10.0, bitRateKbps = null)
    private val portrait = VideoInfo(displayWidth = 1080, displayHeight = 1920, frameRate = 60.0, durationSeconds = 10.0, bitRateKbps = null)

    @Test
    fun keepsAspectRatioForLandscape() {
        assertEquals(Size(1280, 720), ResolutionPreset.P720.outputSize(landscape))
        assertEquals(Size(852, 480), ResolutionPreset.P480.outputSize(landscape))
    }

    @Test
    fun usesShortSideForPortrait() {
        assertEquals(Size(720, 1280), ResolutionPreset.P720.outputSize(portrait))
    }

    @Test
    fun originalKeepsSourceSize() {
        assertEquals(Size(1920, 1080), ResolutionPreset.Original.outputSize(landscape))
    }

    @Test
    fun disablesUpscalePresets() {
        assertFalse(ResolutionPreset.P1440.isAvailableFor(landscape))
        assertTrue(ResolutionPreset.P1080.isAvailableFor(landscape))
    }

    @Test
    fun disablesHigherFrameRates() {
        assertFalse(FrameRatePreset.Fps60.isAvailableFor(landscape))
        assertTrue(FrameRatePreset.Fps30.isAvailableFor(landscape))
        assertTrue(FrameRatePreset.Fps60.isAvailableFor(portrait))
    }
}

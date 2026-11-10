package com.music.bitchord

import com.music.bitchord.playback.SleepFade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timed sleep timer's fade-out shape: how long the ramp runs and the
 * gain it hands the player at each point of it.
 */
class SleepFadeTest {

    @Test
    fun `a long timer fades over the full window`() {
        assertEquals(SleepFade.MAX_FADE_MS, SleepFade.fadeWindowMs(45 * 60_000L))
        assertEquals(SleepFade.MAX_FADE_MS, SleepFade.fadeWindowMs(SleepFade.MAX_FADE_MS))
    }

    @Test
    fun `a short timer fades proportionally over its whole remainder`() {
        assertEquals(10_000L, SleepFade.fadeWindowMs(10_000L))
        assertEquals(1_000L, SleepFade.fadeWindowMs(1_000L))
        assertEquals(0L, SleepFade.fadeWindowMs(0L))
        assertEquals(0L, SleepFade.fadeWindowMs(-5_000L))
    }

    @Test
    fun `gain starts at full and lands on silence`() {
        val window = SleepFade.fadeWindowMs(60_000L)
        assertEquals(1f, SleepFade.gain(window, window), 0.0001f)
        assertEquals(0f, SleepFade.gain(0L, window), 0.0001f)
        assertEquals(0f, SleepFade.gain(-1_000L, window), 0.0001f)
    }

    @Test
    fun `gain falls monotonically through the window`() {
        val window = 25_000L
        var previous = Float.MAX_VALUE
        var remaining = window
        while (remaining >= 0L) {
            val gain = SleepFade.gain(remaining, window)
            assertTrue("gain $gain at ${remaining}ms rose above $previous", gain <= previous)
            assertTrue(gain in 0f..1f)
            previous = gain
            remaining -= 1_000L
        }
    }

    @Test
    fun `midpoint of the ramp is half gain`() {
        // Smoothstep passes through exactly 0.5 at its midpoint.
        assertEquals(0.5f, SleepFade.gain(12_500L, 25_000L), 0.0001f)
    }

    @Test
    fun `gain before the window is full and a missing window never fades`() {
        assertEquals(1f, SleepFade.gain(60_000L, 25_000L), 0.0001f)
        assertEquals(1f, SleepFade.gain(5_000L, 0L), 0.0001f)
        assertEquals(0f, SleepFade.gain(0L, 0L), 0.0001f)
    }
}

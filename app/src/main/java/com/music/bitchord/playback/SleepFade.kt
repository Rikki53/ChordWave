package com.music.bitchord.playback

/**
 * The shape of the timed sleep timer's fade-out, kept pure so it can be
 * tested without a player.
 *
 * The "stop after this track" timer already fades: [CrossfadeController]
 * runs its SLEEP_FADE phase over the crossfade span at the track's end.
 * A *timed* timer has no track end to hang a fade on, so its fade is a
 * window before the deadline instead — the last [MAX_FADE_MS] at most,
 * shortened proportionally when the timer itself is shorter than that,
 * so a 10-second timer fades over its whole 10 seconds rather than
 * starting its fade already a third of the way down.
 *
 * The gain curve is a smoothstep: flat at both ends, so the first step
 * down is inaudible as a step and the last step lands on silence without
 * a click — the same reason the crossfade's own curves are not linear.
 */
object SleepFade {

    /** Longest the fade may run: the tail end of any timer longer than this. */
    const val MAX_FADE_MS = 25_000L

    /** How often the watcher re-reads the clock and re-sets the gain. */
    const val TICK_MS = 200L

    /**
     * How long the fade for a timer with [remainingMs] left should run,
     * decided at the moment the fade begins: the full window for a long
     * timer, the whole remainder for a short one.
     */
    fun fadeWindowMs(remainingMs: Long): Long =
        remainingMs.coerceIn(0L, MAX_FADE_MS)

    /**
     * The gain multiplier for [remainingMs] left of a fade spanning
     * [windowMs]: 1f while the fade has not begun (or when there is no
     * window), 0f at and past the deadline, smoothstep between.
     */
    fun gain(remainingMs: Long, windowMs: Long): Float {
        if (windowMs <= 0L) return if (remainingMs <= 0L) 0f else 1f
        val fraction = (remainingMs.toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }
}

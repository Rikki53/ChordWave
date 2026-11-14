// LaunchGate.kt — the LaunchGate object, extracted verbatim from Mochy's
// ui/launch/LaunchIntro.kt. ChordWave's Modern host does not play Mochy's
// launch intro; the gate exists so ported code that waits on it (splash
// exit, heavy-startup guards) compiles and behaves. MochyApp calls skip()
// on launch so nothing waits.

package com.music.bitchord.ui.mochy.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** process-wide: heavy startup work waits here until the launch intro has landed */
object LaunchGate {
    private val introDone = MutableStateFlow(false)
    private val splashGone = MutableStateFlow(false)

    /** a launch that plays the intro shows Home's skeleton first, even when its data is in already */
    @Volatile
    internal var holdHome = false

    /** a fresh launch from the launcher: the intro will play */
    fun begin() {
        holdHome = true
        splashGone.value = false
        introDone.value = false
    }

    /** no intro this time (a deep link, a recreated activity): nothing waits */
    fun skip() {
        holdHome = false
        introDone.value = true
    }

    /** the system splash has stepped aside; the app's own mark is the one on screen now */
    fun onSplashGone() {
        splashGone.value = true
    }

    internal fun finish() {
        introDone.value = true
    }

    internal suspend fun awaitSplashGone(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { splashGone.first { it } }
    }

    /**
     * Suspends until the intro has landed. Never longer than [timeoutMillis]: a process started
     * for the player service alone has no intro, and its work shouldn't sit waiting for one.
     */
    suspend fun awaitIntro(timeoutMillis: Long = 8_000L) {
        withTimeoutOrNull(timeoutMillis) { introDone.first { it } }
    }
}

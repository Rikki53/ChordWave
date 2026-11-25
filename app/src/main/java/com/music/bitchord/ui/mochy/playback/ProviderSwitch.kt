// ProviderSwitch.kt — ported from Mochy Music (GPL-3.0): playback/ProviderSwitch.kt
//
// The engine behind stream info's "Play from" sheet (batch 37): stop the
// song, ask the picked provider up to 20 times in a minute, reload at the
// same spot — or carry on where it was. The flow is Mochy's; the plumbing
// is ChordWave's: a resolved route is a playback URI (a source-pinned
// bitchord://source URI, or the song's own bitchord://watch URI for
// YouTube), and the reload replaces the current media item at the held
// position. PlayingFrom / ProviderSwitchState live in PlayingFrom.kt.

package com.music.bitchord.ui.mochy.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** a provider picked by hand for one song, and the playback URI that reloads it from there */
class ForcedRoute(val providerId: String, val playbackUri: String)

/** moves the playing song to a picked provider: stop, ask up to 20 times in a minute, reload at the same spot or carry on */
// the scope is read on every switch: the host may be rebuilt while the sheet lives on
class ProviderSwitch(private val scope: () -> CoroutineScope, private val host: Host) {

    interface Host {
        /** the song playing, null when there's nothing to move */
        fun currentMediaId(): String?

        /** stops playback and says whether it was playing, so a failed switch can carry on */
        fun holdPlayback(): Boolean

        fun resumePlayback()

        /** one try at the provider; null when it didn't have the song or didn't answer */
        suspend fun resolve(providerId: String, mediaId: String): ForcedRoute?

        /** the provider answered: reload the song from there, at the held spot */
        fun switchTo(mediaId: String, route: ForcedRoute, play: Boolean)
    }

    private val _state = MutableStateFlow<ProviderSwitchState>(ProviderSwitchState.Idle)
    val state: StateFlow<ProviderSwitchState> = _state.asStateFlow()

    private var job: Job? = null

    fun force(providerId: String) {
        val mediaId = host.currentMediaId() ?: return
        job?.cancel()
        job = scope().launch {
            val wasPlaying = host.holdPlayback()
            _state.value = ProviderSwitchState.Working(providerId, 1, ProviderSwitchMaxTries)
            val route = withTimeoutOrNull(DeadlineMs) {
                var found: ForcedRoute? = null
                for (attempt in 1..ProviderSwitchMaxTries) {
                    _state.value = ProviderSwitchState.Working(providerId, attempt, ProviderSwitchMaxTries)
                    found = runCatching { host.resolve(providerId, mediaId) }
                        .onFailure { Timber.tag(Tag).w(it, "$providerId try $attempt failed for $mediaId") }
                        .getOrNull()
                    if (found != null) break
                    // a little longer between each try, so a server that's waking up gets the chance
                    delay((attempt * 400L).coerceAtMost(3_000L))
                }
                found
            }
            // the song changed under the switch: nothing to move any more
            if (host.currentMediaId() != mediaId) {
                _state.value = ProviderSwitchState.Idle
                return@launch
            }
            if (route == null) {
                _state.value = ProviderSwitchState.Failed(providerId)
                if (wasPlaying) host.resumePlayback()
                return@launch
            }
            host.switchTo(mediaId, route, wasPlaying)
            _state.value = ProviderSwitchState.Done(providerId)
        }
    }

    /** the sheet closed: a finished or failed switch has said what it had to */
    fun acknowledge() {
        if (_state.value !is ProviderSwitchState.Working) _state.value = ProviderSwitchState.Idle
    }

    companion object {
        private const val Tag = "ProviderSwitch"
        const val DeadlineMs = 60_000L
    }
}

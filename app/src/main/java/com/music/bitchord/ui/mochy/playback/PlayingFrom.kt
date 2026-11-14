// PlayingFrom.kt — data-only port of the two pure types in Mochy's
// playback/ProviderSwitch.kt (GPL-3.0): where a song is playing from, and
// what "force change" is doing. The ProviderSwitch engine itself (stop,
// resolve, reload) is NOT ported — see NEEDS.md §(b).

package com.music.bitchord.ui.mochy.playback

/** The provider id for YouTube Music (Mochy's SourceModules.YouTubeId). */
const val YouTubeProviderId = "youtube"

/** where a song is playing from right now, for stream info's provider row */
data class PlayingFrom(
    /** a provider id, "youtube", or one of [Device] and [Downloads] */
    val providerId: String,
    val name: String,
    /** the provider's own words for the stream ("flac 24-bit"), when it said */
    val quality: String? = null,
    /** what the provider matched the song to, when it's a module */
    val matchedId: String? = null,
) {
    companion object {
        const val Device = "device"
        const val Downloads = "downloads"
    }
}

/** what "force change" in stream info is doing */
sealed interface ProviderSwitchState {
    data object Idle : ProviderSwitchState
    data class Working(val providerId: String, val attempt: Int, val of: Int) : ProviderSwitchState
    data class Failed(val providerId: String) : ProviderSwitchState
    data class Done(val providerId: String) : ProviderSwitchState
}

/** how many tries a forced provider switch gets (Mochy's ProviderSwitch.MaxTries) */
const val ProviderSwitchMaxTries = 20

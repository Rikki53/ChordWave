// RadioPlayback — the Modern play path for a Radio Browser station:
// the Classic MainActivity.playStation verbatim, minus the activity.
//
// Classic (MainActivity.kt, playStation): refuse when the party host has
// locked controls, then controller.playSongs(listOf(station.asSong()), 0)
// — the station rides the ordinary queue as a one-song queue whose id is
// its stream URL (RadioStation.asSong / Song.toMediaItem) — and finally
// RadioBrowserApi.registerPlay so the directory counts the play. No
// commitRadioQueue: that belongs to YouTube's startRadio, not stations.
//
// The one adaptation: the Modern screen reaches the controller through
// MochyPlayerConnection.player (a Player?), so the MediaController the
// suspend playSongs extension needs comes from a cast. MochyApp binds the
// connection to rememberMediaController(), so the cast holds whenever a
// controller is bound at all; when it is not, the caller gets
// NO_CONTROLLER and shows the failure instead of failing silently.

package com.music.bitchord.ui.mochy.screens.radio

import androidx.media3.session.MediaController
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.playback.playSongs
import com.music.bitchord.radio.RadioBrowserApi
import com.music.bitchord.radio.RadioStation
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import timber.log.Timber

/** What [MochyPlayerConnection.playStation] did — or why it did nothing. */
enum class PlayStationResult {
    /** The station is playing (or starting to). */
    PLAYED,

    /** A Listen Together party is running and the host holds the controls. */
    LOCKED_BY_HOST,

    /** No MediaController is bound to the connection right now. */
    NO_CONTROLLER,
}

/**
 * Plays [station] exactly the way the Classic radio tab does: the station
 * replaces the queue as a single endless stream, and the play is
 * registered with Radio Browser. Suspends for playSongs' queue build.
 */
suspend fun MochyPlayerConnection.playStation(station: RadioStation): PlayStationResult {
    if (ListenTogether.state.value.controlsLocked) return PlayStationResult.LOCKED_BY_HOST
    val controller = player as? MediaController
    if (controller == null) {
        Timber.w("Radio: no MediaController bound — cannot play ${station.name}")
        return PlayStationResult.NO_CONTROLLER
    }
    controller.playSongs(listOf(station.asSong()), 0)
    RadioBrowserApi.registerPlay(station)
    return PlayStationResult.PLAYED
}

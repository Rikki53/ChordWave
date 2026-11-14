// MenuActions.kt — coordinator-side helpers (not a Mochy port).
//
// The CW-backed actions the seven ported item menus share. Mochy inlined
// these per menu against its own database and innertube engine; here they
// are shared so the menus stay consistent:
// - like / library writes go to YtMusicRepository (+ LikeState for the
//   session override, so the UI updates instantly),
// - sharing is a plain ACTION_SEND intent, as Mochy's album menu did,
// - download removal goes through CW's Downloads object.

package com.music.bitchord.ui.mochy.player.menu

import android.content.Context
import android.content.Intent
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/** Flips a track's like: session override first (instant UI), then the server rating. */
internal fun CoroutineScope.toggleSongLike(videoId: String, liked: Boolean) {
    val status = if (liked) LikeStatus.LIKE else LikeStatus.INDIFFERENT
    LikeState.set(videoId, status)
    launch { YtMusicRepository.rate(videoId, status) }
}

/**
 * Sends the library feedback token for [videoId]: the token on hand when
 * there is one, otherwise the one fetched off the track's own menu. CW has
 * no local library flag to flip afterwards — the server state is the state.
 */
internal fun CoroutineScope.sendLibraryFeedback(
    videoId: String,
    adding: Boolean,
    addToken: String?,
    removeToken: String?,
) {
    val token = if (adding) addToken else removeToken
    if (token != null) {
        launch { YtMusicRepository.setLibraryStatus(token) }
    } else {
        launch {
            val menu = YtMusicRepository.songMenu(videoId).getOrNull()
            val fetched = if (adding) menu?.addToLibraryToken else menu?.removeFromLibraryToken
            if (fetched != null) {
                YtMusicRepository.setLibraryStatus(fetched)
            } else {
                Timber.w("sendLibraryFeedback: no library token for $videoId (signed out?)")
            }
        }
    }
}

/** Plain text share, as the album menu already did in Mochy. */
internal fun Context.shareText(text: String) {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, null))
}

/** Cancels an in-flight download and deletes a saved one. */
internal fun CoroutineScope.removeDownload(context: Context, videoId: String) {
    Downloads.cancel(videoId)
    launch { Downloads.delete(context, videoId) }
}

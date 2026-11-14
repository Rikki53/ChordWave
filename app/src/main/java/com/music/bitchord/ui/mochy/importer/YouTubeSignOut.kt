// The bridge between the Hilt-owned import/sync engine and MainViewModel,
// which is activity-scoped and not injectable. Follows the SpotifyLibrary
// .auth pattern (installed once by the host, read by whoever needs it).
//
// The Modern host (MochyApp) wires both delegates from the MainViewModel
// it already holds — see NEEDS.md §(a) A2:
//   YouTubeSignOut.delegate = mainViewModel::signOut
//   YouTubeSignOut.refreshLibrary = { mainViewModel.loadLibrary(); mainViewModel.loadPlaylists() }
//
// Until the host wires it, signOut() falls back to clearing the SAME
// AuthStore / Innertube / SignedIn / LikeState state MainViewModel
// .signOut clears (never a second session store) — MainViewModel's own
// in-memory account/library flows then refresh on their next load
// instead of immediately.
package com.music.bitchord.ui.mochy.importer

import android.content.Context
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.StreamResolver
import timber.log.Timber

object YouTubeSignOut {

    /** MainViewModel.signOut, installed by the Modern host. Null until wired. */
    @Volatile var delegate: (() -> Unit)? = null

    /** MainViewModel library + playlists reload, installed by the Modern host. Null until wired. */
    @Volatile var refreshLibrary: (() -> Unit)? = null

    /** Signs out but keeps everything already synced in the library. */
    fun signOut(context: Context) {
        val signOut = delegate
        if (signOut != null) {
            signOut()
            return
        }
        Timber.w("YouTubeSignOut: no host delegate; signing out directly (MainViewModel state refreshes on next load)")
        AuthStore(context.applicationContext).signOut()
        Innertube.cookie = null
        StreamResolver.onSessionChanged()
        SignedIn.state(context.applicationContext).value = false
        LikeState.clear()
        Innertube.selectChannel(null, null)
    }
}

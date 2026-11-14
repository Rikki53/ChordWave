// Ported from Mochy Music (GPL-3.0): importer/PlaylistResync.kt
// (PlaylistResync class + ResyncAllStart only — the ResyncStart sealed
// interface lives in the Phase 4E file ResyncStart.kt, same package;
// do NOT redeclare it here.)
//
// HONEST STUB: Mochy's resync reads imported-playlist records (which
// playlist came from which service, under which remote id) out of its
// database. ChordWave has no import store yet (playlist NEEDS C2 — the
// same gap that blocks startImport's landing), so no playlist is known
// to have come from a service: target() finds nothing, accountTargets()
// is empty, and every start answers Failed with Mochy's own
// "nothing to re-sync" words. The menu (PlaylistResyncMenu) already
// hides itself the same way — source is always null there — so no
// re-sync route can be opened until the store lands. When it does, this
// class grows Mochy's lookUp/routeOf/launch, and MusicImportService's
// startResync is already waiting for the requests.
package com.music.bitchord.ui.mochy.importer

import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.importer.account.ResyncTarget
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** how far re-sync all got on its own. busy: an import was running, so nothing started */
data class ResyncAllStart(
    val started: Int,
    val throughAccount: List<AccountService>,
    val needLink: Int,
    val busy: Boolean = false,
)

/** works out how to read an imported playlist again (its link or youtube id, or the service's sign-in page), old imports saved neither so they're found by name, deezer ones ask for a link */
@Singleton
class PlaylistResync @Inject constructor(
    // Kept for when imported-playlist records exist: the launch path
    // hands ResyncRequests to the import service.
    @Suppress("unused") private val importService: MusicImportService,
) {
    suspend fun start(playlistId: String): ResyncStart {
        Timber.w("PlaylistResync: no import store yet, nothing to re-sync (playlist $playlistId)")
        return ResyncStart.Failed(NOT_IMPORTED)
    }

    /** re-sync all as far as it goes without the user: playlists that can be read straight away start now, the rest (sign-in pages, old imports needing a link) are left for the caller */
    suspend fun startAll(): ResyncAllStart {
        Timber.w("PlaylistResync: no import store yet, re-sync all has nothing to do")
        return ResyncAllStart(started = 0, throughAccount = emptyList(), needLink = 0)
    }

    /** with the playlist's link, pasted by the user for an import that didn't save one */
    suspend fun startFromLink(playlistId: String, link: String): ResyncStart {
        Timber.w("PlaylistResync: no import store yet, can't re-sync from a link (playlist $playlistId)")
        return ResyncStart.Failed(NOT_IMPORTED)
    }

    /** with songs the account page has already read, by the playlist they're for */
    suspend fun startWithReads(reads: Map<String, ResyncRead>): ResyncStart {
        Timber.w("PlaylistResync: no import store yet, can't start a re-sync from reads")
        return ResyncStart.Failed(NOT_IMPORTED)
    }

    /** what the account page looks for once the user is signed in */
    suspend fun target(playlistId: String): ResyncTarget? {
        Timber.w("PlaylistResync: no import store yet, no target for playlist $playlistId")
        return null
    }

    /** every imported playlist only service's signed-in account can read, for re-sync all */
    suspend fun accountTargets(service: AccountService): List<ResyncTarget> {
        Timber.w("PlaylistResync: no import store yet, no ${service.label} targets")
        return emptyList()
    }

    private companion object {
        const val NOT_IMPORTED = "This playlist didn't come from a music service, so there's nothing to re-sync."
    }
}

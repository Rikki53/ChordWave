// Ported from Mochy Music (GPL-3.0): viewmodels/AccountImportViewModel.kt
//
// ADAPTATION — route args, no saved-state handle: the host parses the route
// and the screen calls load(service, resync, then) once (the route-args
// rule used across the port). The screen collects state/ready/notice
// before that call runs, so this ViewModel owns its own flows and
// forwards from the session created in load(). load() is idempotent for
// repeat calls with the same args (LaunchedEffect re-fires on
// recomposition keys) and re-creates the session when the args change
// (re-sync all chains one service's page into the next).
//
// NOTE: `then` arrives already parsed as a list of services (the host
// does the split), unlike Mochy, which split a comma string itself.
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.ui.mochy.importer.ParsedImport
import com.music.bitchord.ui.mochy.importer.PlaylistResync
import com.music.bitchord.ui.mochy.importer.ResyncStart
import com.music.bitchord.ui.mochy.importer.account.AccountImportSession
import com.music.bitchord.ui.mochy.importer.account.AccountImportState
import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.screens.settings.importsync.ResyncAllArg
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Import settings page's account import; all the work is in [AccountImportSession]. Opened by a
 * Re-sync instead, it signs in the same way and reads just the playlists being re-synced: one
 * playlist's, or with Re-sync all, every one this service has.
 */
@HiltViewModel
class AccountImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistResync: PlaylistResync,
) : ViewModel() {

    var service: AccountService = AccountService.SPOTIFY
        private set

    /** What a Re-sync opened this page for: a playlist's id, or [ResyncAllArg]. Null for a plain import. */
    private var resyncArg: String? = null
    var isResync: Boolean = false
        private set

    /** The services Re-sync all goes on to once this one is done. */
    var nextServices: List<AccountService> = emptyList()
        private set

    /** Playlists the Re-sync couldn't find in this account, for the message as it starts. */
    var missingFromAccount: List<String> = emptyList()
        private set

    private var session: AccountImportSession? = null
    private var loadedArgs: Triple<AccountService, String?, List<AccountService>>? = null
    private var forwardJob: Job? = null
    private var resyncJob: Job? = null

    private val _state = MutableStateFlow<AccountImportState>(AccountImportState.Loading("Getting ready…"))
    val state: StateFlow<AccountImportState> = _state.asStateFlow()

    private val _ready = MutableStateFlow<ParsedImport?>(null)
    val ready: StateFlow<ParsedImport?> = _ready.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** How the Re-sync went once it was handed on; the page closes on Started. */
    private val _resyncStart = MutableStateFlow<ResyncStart?>(null)
    val resyncStart: StateFlow<ResyncStart?> = _resyncStart.asStateFlow()

    fun load(service: AccountService, resync: String?, then: List<AccountService>) {
        val args = Triple(service, resync, then)
        if (args == loadedArgs && session != null) return
        loadedArgs = args
        this.service = service
        resyncArg = resync
        isResync = resync != null
        nextServices = then
        missingFromAccount = emptyList()
        _resyncStart.value = null

        session?.cancel()
        resyncJob?.cancel()
        val newSession = AccountImportSession(service, context, viewModelScope)
        session = newSession
        forwardJob?.cancel()
        forwardJob = viewModelScope.launch {
            launch { newSession.state.collect { _state.value = it } }
            launch { newSession.ready.collect { _ready.value = it } }
            launch { newSession.notice.collect { _notice.value = it } }
        }

        if (resync == null) {
            newSession.start()
        } else {
            resyncJob = viewModelScope.launch {
                val targets = if (resync == ResyncAllArg) {
                    playlistResync.accountTargets(service)
                } else {
                    listOfNotNull(playlistResync.target(resync))
                }
                if (targets.isEmpty()) {
                    _resyncStart.value = ResyncStart.Failed("There's nothing from ${service.label} to re-sync any more.")
                    return@launch
                }
                newSession.startResync(targets)
                newSession.resyncReads.filterNotNull().collect { result ->
                    missingFromAccount = result.missing
                    _resyncStart.value = if (result.reads.isEmpty()) {
                        ResyncStart.Failed("Couldn't find ${result.missing.joinToString(", ")} in your ${service.label} library.")
                    } else {
                        playlistResync.startWithReads(result.reads)
                    }
                }
            }
        }
    }

    fun onHeader(name: String, value: String, url: String) = session?.onHeader(name, value, url)
    fun onApplePageProbe(raw: String?) = session?.onApplePageProbe(raw)
    fun onCookies(cookieHeader: String?) = session?.onCookies(cookieHeader)
    fun signInAgain() = session?.signInAgain()
    fun refreshYouTube() = session?.refreshYouTube()
    fun retry() = session?.retry()
    fun toggle(id: String) = session?.toggle(id)
    fun selectAll() = session?.selectAll()
    fun selectNone() = session?.selectNone()
    fun readSelected() = session?.readSelected()
    fun pickForResync(id: String) = session?.pickForResync(id)
    fun cancelReading() = session?.cancelReading()
    fun consumeReady() = session?.consumeReady()
    fun clearNotice() = session?.clearNotice()

    /** A Re-sync whose playlist isn't in this account, read from its link instead. */
    fun resyncFromLink(link: String) {
        val playlistId = resyncArg?.takeIf { it != ResyncAllArg } ?: return
        viewModelScope.launch { _resyncStart.value = playlistResync.startFromLink(playlistId, link) }
    }

    /** A failed Re-sync start has been shown; the page stays for another try. */
    fun clearResyncStart() {
        _resyncStart.value = null
    }

    override fun onCleared() {
        session?.cancel()
        super.onCleared()
    }
}

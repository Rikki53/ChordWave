// UpdateDeepLink — coordinator-written (15 Nov 2026, Phase 4 merge). A
// request from the update notification to open the version sheet. Follows
// PlayerDeepLink's relay pattern exactly: MainActivity is singleTask, so
// the tap arrives via onCreate on a cold launch and onNewIntent every
// other time, and neither can reach the Modern host's sheet state — both
// relay through here and MochyApp watches [requests] (see
// UpdateNotificationOpener). The action is cleared off the intent when
// consumed, so a configuration change does not re-open the sheet.
package com.music.bitchord.ui.mochy.screens.update

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateDeepLink {

    private val _requests = MutableStateFlow(0)

    /** How many update-sheet requests have arrived. MochyApp watches this. */
    val requests: StateFlow<Int> = _requests.asStateFlow()

    /** Reads an incoming intent, and reports whether it asked for the update sheet. */
    fun consume(intent: Intent?): Boolean {
        if (intent?.action != ActionOpenUpdate) return false
        intent.action = null
        _requests.value += 1
        return true
    }
}

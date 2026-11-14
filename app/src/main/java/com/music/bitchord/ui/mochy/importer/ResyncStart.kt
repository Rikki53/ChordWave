// Ported from Mochy Music (GPL-3.0): importer/PlaylistResync.kt (ResyncStart
// sealed interface only). The PlaylistResync engine is NOT ported — the
// AccountImportViewModel adapter owns it (see NEEDS.md).
package com.music.bitchord.ui.mochy.importer

import com.music.bitchord.ui.mochy.importer.account.AccountService

/** how a re-sync gets going, for the menu that asked for it */
sealed interface ResyncStart {
    /** reading in the background now; a toast says what changed */
    data object Started : ResyncStart

    /** only the signed-in account can read it: open the service's sign-in page in re-sync mode */
    data class ThroughAccount(val service: AccountService) : ResyncStart

    /** nothing says where it lives, so the user pastes its link */
    data object NeedsLink : ResyncStart

    data class Failed(val message: String) : ResyncStart
}

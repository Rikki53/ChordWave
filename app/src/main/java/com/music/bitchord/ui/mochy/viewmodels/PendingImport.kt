// Ported from Mochy Music (GPL-3.0): viewmodels/ImportSyncViewModel.kt
// (PendingImport sealed interface only — in Mochy it lives in the ViewModel
// file; here it is its own file so the screens compile against the adapter's
// ViewModel without the adapter redeclaring it).
//
// ADAPTER NOTE: ImportSyncViewModel.kt must NOT redeclare PendingImport —
// import this one (same package, no import needed).
package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.ui.mochy.importer.ParsedImport

sealed interface PendingImport {
    data object Idle : PendingImport
    data class Loading(val label: String) : PendingImport
    data class Ready(val parsed: ParsedImport, val sourceLabel: String) : PendingImport
    data class Failed(val message: String) : PendingImport
}

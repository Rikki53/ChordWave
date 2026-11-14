// Ported from Mochy Music (GPL-3.0): importer/YouTubeSyncManager.kt
// (YtSyncState data class only). The YouTubeSyncManager engine is NOT ported —
// the ImportSyncViewModel adapter owns it (see NEEDS.md).
package com.music.bitchord.ui.mochy.importer

data class YtSyncState(
    val running: Boolean = false,
    val label: String = "",
    val message: String? = null,
    val canUndo: Boolean = false,
)

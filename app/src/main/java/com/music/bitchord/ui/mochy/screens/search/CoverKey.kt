// Ported from Mochy Music (GPL-3.0): viewmodels/MoodAndGenresViewModel.kt (coverKey only)
// The key the mood/genre cover cache is filed under: browse id + params.
// A one-line pure function, ported so SearchBrowseContent and the
// MoodAndGenresViewModel adapter share it instead of each inventing one.
// If the coordinator prefers it next to the adapter, move it to
// ui.mochy.viewmodels and update the import in SearchScreen.kt.

package com.music.bitchord.ui.mochy.screens.search

fun coverKey(browseId: String, params: String?): String = "$browseId|${params.orEmpty()}"

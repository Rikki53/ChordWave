// Ported from Mochy Music (GPL-3.0): utils/ArtistVideos.kt (FeedVideoKind,
// line 11). Moved to ui.mochy.models per DATA-MAPPING §2.4 (HomeScreen
// imports it from there).
package com.music.bitchord.ui.mochy.models

/** the kinds of video row YouTube's own Home feed sends */
enum class FeedVideoKind { Live, MusicVideos, Other }

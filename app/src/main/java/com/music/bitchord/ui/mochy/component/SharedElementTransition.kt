// Ported from Mochy Music (GPL-3.0): ui/component/SharedElementTransition.kt
// SharedElementTransition.kt

package com.music.bitchord.ui.mochy.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedContentScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * shared cover between a grid card and the screen it opens. playlists open with the full container
 * transform (the whole screen grows out of the card), albums keep the plain cover morph.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.homeSharedElement(
    key: String?,
    cornerRadius: Dp = HomeCardCornerRadius,
    coverUrl: String? = null,
): Modifier {
    if (key == null) return this
    // playlists and albums open the same way now: the page grows out of the card
    if (key.startsWith("playlist-") || key.startsWith("album-")) return containerTransformSource(key, cornerRadius, coverUrl)
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedTransitionScope) {
        this@homeSharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

/**
 * the key an album or playlist cover hands its page over with, matching the one the page itself
 * grows from ("album-<id>" in AlbumScreen, "playlist-<id>" in the playlist screens). anything that
 * doesn't open a cover page has none.
 */
// Ported note: Mochy took a YTItem (Innertube model) here and returned
// "album-<id>" / "playlist-<id>" (null otherwise). ChordWave has no equivalent
// Innertube model types (it uses innertubex plus its own parser), so callers
// pass the kind ("album" or "playlist") and the id directly.
fun coverTransitionKey(kind: String, id: String): String = "$kind-$id"

// YTItem overload (phase2-search NEEDS.md §(a) A8): Mochy's verbatim
// overload — now possible because the YTItem family is staged.
fun coverTransitionKey(item: YTItem): String? = when (item) {
    is AlbumItem -> "album-${item.id}"
    is PlaylistItem -> "playlist-${item.id}"
    else -> null
}

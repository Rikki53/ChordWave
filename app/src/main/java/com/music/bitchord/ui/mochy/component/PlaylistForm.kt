// Ported from Mochy Music (GPL-3.0): ui/component/PlaylistForm.kt
// PlaylistForm.kt
//
// ADAPTATIONS:
// - PlaylistFormState drops `choice`: the cover carousel engine
//   (PlaylistCovers / PlaylistCoverCarousel, ~850 lines) is NOT ported —
//   engine work, not referenced by the screens. The form keeps name /
//   description / sync / busy.
// - PlaylistFormContent replaces the carousel with a simplified static
//   cover preview (existingImage ?? artworkUrl, or a placeholder). The
//   signature is kept identical to Mochy's so the create/edit call sites
//   match; `seed`, `startPage` and `keepAllowed` are currently unused
//   (they only drove the carousel).

package com.music.bitchord.ui.mochy.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R

private const val MaxNameLength = 80
private const val MaxDescriptionLength = 300

/**
 * What the create and edit sheets are editing. Plain state held outside the composition: the sheet
 * body and its pinned button bar are composed separately by the popup host, and both read it.
 */
@Stable
internal class PlaylistFormState(name: String, description: String) {
    var name by mutableStateOf(name)
    var description by mutableStateOf(description)

    /** Whether a new playlist is created on YouTube Music as well. */
    var sync by mutableStateOf(false)

    /** Set while the work runs after Create/Done - the button is off and the sheet can't be closed. */
    var busy by mutableStateOf(false)
}

/**
 * The body of the create and edit sheets, as in the reference mocks: the cover preview, then
 * "Playlist title" and "Playlist desc". Scrolls when the screen is short or the text large; the
 * sheet's own button bar stays pinned underneath.
 *
 * @param seed what a playlist with no top song yet takes its colours from (carousel only; unused).
 * @param extra anything the caller wants below the fields (the create sheet's sync switch).
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun PlaylistFormContent(
    form: PlaylistFormState,
    seed: String,
    artworkUrl: String?,
    startPage: Int,
    existingImage: String?,
    keepAllowed: Boolean,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        // Simplified cover preview: the carousel engine is not ported.
        val previewUrl = existingImage ?: artworkUrl
        if (previewUrl != null) {
            AsyncImage(
                model = previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.height(48.dp),
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SheetLabel("Playlist title")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = form.name,
            onValueChange = { form.name = it.take(MaxNameLength) },
            placeholder = "Name your playlist",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(18.dp))

        SheetLabel("Playlist desc")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = form.description,
            onValueChange = { form.description = it.take(MaxDescriptionLength) },
            placeholder = "What is it about?",
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        if (extra != null) {
            Spacer(Modifier.height(18.dp))
            extra()
        }
    }
}

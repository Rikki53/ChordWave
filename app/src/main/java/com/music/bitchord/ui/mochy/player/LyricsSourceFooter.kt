// Ported from Mochy Music (GPL-3.0): ui/player/LyricsSourceFooter.kt
package com.music.bitchord.ui.mochy.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.lyrics.EmbeddedLyrics
import com.music.bitchord.ui.mochy.lyrics.LyricsProviderRegistry
import com.music.bitchord.ui.mochy.lyrics.UserEditedProvider
import com.music.bitchord.ui.mochy.lyrics.getWebsite
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.safeOpenUri
import com.music.bitchord.ui.mochy.component.RemoteFocus

private val LabelColor = Color.White.copy(alpha = 0.55f)
private val ValueColor = Color.White.copy(alpha = 0.38f)

private val FooterTextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 17.sp,
    letterSpacing = (-0.01).em,
    color = ValueColor,
)

private val ButtonTextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
)

/**
 * The end of the lyrics page: where these lyrics came from, and a button
 * to pick another source.
 */
@Composable
internal fun LyricsSourceFooter(
    provider: String,
    onSwitchSource: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val website = remember(provider) { LyricsProviderRegistry.getWebsite(provider) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledText(
                label = "Source",
                value = sourceName(provider),
                onValueClick = website?.let { url -> { uriHandler.safeOpenUri(context, url) } },
            )
            if (onSwitchSource != null) {
                Spacer(modifier = Modifier.width(10.dp))
                SwitchSourceButton(onClick = onSwitchSource)
            }
        }
    }
}

/** The name to show for a saved provider, including the two that aren't online sources. */
private fun sourceName(provider: String): String = when (provider) {
    EmbeddedLyrics.Provider -> "Song file"
    UserEditedProvider -> "Your edit"
    else -> LyricsProviderRegistry.getDisplayName(provider)
}

@Composable
private fun LabeledText(label: String, value: String?, onValueClick: (() -> Unit)? = null) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = LabelColor)) { append(label) }
            if (value == null) return@buildAnnotatedString
            append(" ")
            // an underline tells people the source name opens its website
            if (onValueClick != null) {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(value) }
            } else {
                append(value)
            }
        },
        style = FooterTextStyle,
        maxLines = 1,
        modifier = if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier,
    )
}

@Composable
private fun SwitchSourceButton(onClick: () -> Unit) {
    FooterButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        background = Color.White.copy(alpha = 0.08f),
    ) {
        FooterIcon(Icons.Rounded.SwapVert, tint = LabelColor)
        Text(text = "Switch", style = ButtonTextStyle.copy(color = LabelColor))
    }
}

/** Shrinks a little while held, so a tap feels like pressing something. */
@Composable
private fun FooterButton(
    onClick: () -> Unit,
    shape: Shape,
    background: Color,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "footerButtonScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun FooterIcon(icon: ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
}

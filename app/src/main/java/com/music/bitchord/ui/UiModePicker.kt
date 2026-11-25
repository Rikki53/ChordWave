package com.music.bitchord.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.UiMode
import com.music.bitchord.ui.mochy.theme.InterFontFamily

/**
 * The first-run UI mode picker (redesigned in batch 21): fresh installs
 * choose between the Classic ChordWave look and the Modern look before
 * anything else. Styled after the Modern theme — dark backdrop, Inter,
 * large rounded cards, mint accent from the ChordWave mark.
 *
 * Tapping a card only selects it: a phone-frame preview of that look
 * shows above the cards, and nothing is applied until Continue is
 * tapped. The choice is recorded by
 * [com.music.bitchord.data.settings.AppSettings.setUiMode], so this
 * screen never shows again, and Settings can switch modes at any time.
 */
private val PickerBackdrop = Color(0xFF0B0B0C)
private val PickerCard = Color(0xFF1C1C1F)
private val PickerMint = Color(0xFF5CF0D2)
private val PickerTextDim = Color(0xFF9A9AA3)

@Composable
fun UiModePicker(
    onChoose: (UiMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(UiMode.MODERN) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PickerBackdrop)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.ui_mode_picker_title),
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ui_mode_picker_subtitle),
            color = PickerTextDim,
            fontFamily = InterFontFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        // The preview: a phone frame holding a small mock of the look
        // that is currently selected, crossfading when it changes.
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "uiModePreview",
        ) { mode ->
            PhonePreview(mode)
        }
        Spacer(Modifier.height(28.dp))

        UiModeOptionCard(
            title = stringResource(R.string.ui_mode_classic),
            description = stringResource(R.string.ui_mode_classic_desc),
            selected = selected == UiMode.CLASSIC,
            onClick = { selected = UiMode.CLASSIC },
        )
        Spacer(Modifier.height(12.dp))
        UiModeOptionCard(
            title = stringResource(R.string.ui_mode_modern),
            description = stringResource(R.string.ui_mode_modern_desc),
            selected = selected == UiMode.MODERN,
            onClick = { selected = UiMode.MODERN },
        )
        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(CircleShape)
                .background(PickerMint)
                .clickable { onChoose(selected) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ui_mode_picker_continue),
                color = Color(0xFF06231B),
                fontFamily = InterFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UiModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PickerCard)
            .then(
                if (selected) {
                    Modifier.border(BorderStroke(2.dp, PickerMint), RoundedCornerShape(24.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = InterFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    color = PickerTextDim,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = PickerMint,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** A small phone-frame mock of one interface, drawn with plain boxes. */
@Composable
private fun PhonePreview(mode: UiMode) {
    Box(
        modifier = Modifier
            .width(190.dp)
            .aspectRatio(9f / 19f)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF151517))
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(25.dp))
                .background(
                    when (mode) {
                        UiMode.MODERN -> Brush.verticalGradient(
                            listOf(Color(0xFF173B34), Color(0xFF101014), Color(0xFF101014)),
                        )
                        UiMode.CLASSIC -> Brush.verticalGradient(
                            listOf(Color(0xFF1B1B1F), Color(0xFF1B1B1F)),
                        )
                    },
                ),
        ) {
            when (mode) {
                UiMode.MODERN -> ModernMock()
                UiMode.CLASSIC -> ClassicMock()
            }
        }
    }
}

@Composable
private fun ModernMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        // greeting + avatar
        Row(verticalAlignment = Alignment.CenterVertically) {
            MockBar(width = 64, height = 9, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f)),
            )
        }
        Spacer(Modifier.height(12.dp))
        // two rounded cards
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PickerMint.copy(alpha = 0.55f)),
            )
        }
        Spacer(Modifier.height(14.dp))
        repeat(3) {
            MockSongRow(modern = true)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.weight(1f))
        // mini player pill
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Spacer(Modifier.height(8.dp))
        // bottom nav pill
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
    }
}

@Composable
private fun ClassicMock() {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // top app bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            MockBar(width = 70, height = 9, color = Color.White.copy(alpha = 0.85f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            repeat(4) {
                MockSongRow(modern = false)
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        // mini player bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.14f)),
        )
        // standard bottom navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == 0) {
                                Color.White.copy(alpha = 0.8f)
                            } else {
                                Color.White.copy(alpha = 0.3f)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun MockSongRow(modern: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(if (modern) 8.dp else 4.dp))
                .background(Color.White.copy(alpha = 0.28f)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            MockBar(width = 72, height = 7, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            MockBar(width = 48, height = 6, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun MockBar(width: Int, height: Int, color: Color) {
    Box(
        Modifier
            .width(width.dp)
            .height(height.dp)
            .clip(CircleShape)
            .background(color),
    )
}

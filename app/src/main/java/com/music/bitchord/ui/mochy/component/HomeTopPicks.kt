// Round 2 item 6 (15 Nov 2026): Classic Home parity on Modern Home — the
// "Top Picks for You" row and the "Find Your Mood" shelf, in Mochy's visual
// language (Inter type, Home card metrics, reveal cascades from HomeScreen).
// The data is ChordWave's own: TopPick/TopPickMood from :shared, built by
// TopPicks.build in the HomeViewModel adapter. Mood colours and lines are
// Classic's own values (sharedUi TopPicksShelf), reused so a mood looks the
// same in both UI modes.

package com.music.bitchord.ui.mochy.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.ui.mochy.theme.InterFontFamily

// ---- Mood colours, titles, lines (Classic's values, sharedUi TopPicksShelf) ----

/** A mood's flat colour + pale stroke; the eyebrow is the stroke lightened, unless set. */
private class MoodColors(
    val background: Color,
    val stroke: Color,
    val eyebrow: Color? = null,
) {
    fun eyebrowColor(): Color = eyebrow ?: lerp(stroke, Color.White, 0.35f)
}

private fun TopPickMood.colors(): MoodColors = when (this) {
    TopPickMood.ENERGY -> MoodColors(Color(0xFF1EBD38), Color(0xFFE2F6A2), Color(0xFFE8FF9C))
    TopPickMood.RELAX -> MoodColors(Color(0xFF31AFF3), Color(0xFFB4E9FF), Color(0xFFD6F3FF))
    TopPickMood.FEELING_BLUE -> MoodColors(Color(0xFF253AB2), Color(0xFF6CC4FF), Color(0xFFA9DBFF))
    TopPickMood.FEEL_GOOD -> MoodColors(Color(0xFFF2A900), Color(0xFFFFE8A0), Color(0xFFFFF27A))
    TopPickMood.FOCUS -> MoodColors(Color(0xFFF28400), Color(0xFFFFD6A4), Color(0xFFFFE2B8))
    TopPickMood.HEARTBREAK -> MoodColors(Color(0xFFEA3520), Color(0xFFFFA690), Color(0xFFFFC9BC))
    TopPickMood.LOVE -> MoodColors(Color(0xFFF34CA0), Color(0xFFFFB8DC), Color(0xFFFFD0EA))
    TopPickMood.AFTER_HOURS -> MoodColors(Color(0xFF3A1F78), Color(0xFFC9B6FF))
    TopPickMood.ALONE_TIME -> MoodColors(Color(0xFF2C6E8C), Color(0xFFA9E2F5))
    TopPickMood.CHILL -> MoodColors(Color(0xFF138A86), Color(0xFFA6F0E6))
    TopPickMood.COMMUTING -> MoodColors(Color(0xFF17855F), Color(0xFFA8EFD0))
    TopPickMood.EATING_COOKING -> MoodColors(Color(0xFFD9621A), Color(0xFFFFD3A8))
    TopPickMood.EVENING -> MoodColors(Color(0xFF2A3A8C), Color(0xFFB8C6FF))
    TopPickMood.FITNESS -> MoodColors(Color(0xFF8DB014), Color(0xFFEFFFA8))
    TopPickMood.GAMING -> MoodColors(Color(0xFF239A2A), Color(0xFFB8FFB0))
    TopPickMood.HOME -> MoodColors(Color(0xFF0E8A6C), Color(0xFFA8F2DB))
    TopPickMood.MORNING -> MoodColors(Color(0xFFF08C1A), Color(0xFFFFE2A6))
    TopPickMood.MOTIVATION -> MoodColors(Color(0xFFC01E4A), Color(0xFFFFB3C6))
    TopPickMood.OUTDOORS -> MoodColors(Color(0xFF4C8A1E), Color(0xFFD4F5A8))
    TopPickMood.PARTY -> MoodColors(Color(0xFFC2287A), Color(0xFFFFC0E0))
    TopPickMood.SLEEP -> MoodColors(Color(0xFF26266E), Color(0xFFC2C6FF))
    TopPickMood.SOCIAL -> MoodColors(Color(0xFFE0507A), Color(0xFFFFD0DE))
    TopPickMood.SPORTS -> MoodColors(Color(0xFF1C6CD0), Color(0xFFB8D8FF))
    TopPickMood.SUMMERTIME -> MoodColors(Color(0xFFFF8C00), Color(0xFFFFE6A8))
    TopPickMood.HOLIDAY -> MoodColors(Color(0xFFC62828), Color(0xFFFFC8C8))
    TopPickMood.WEEKEND -> MoodColors(Color(0xFF7A3CC2), Color(0xFFE2CCFF))
    TopPickMood.WELLBEING -> MoodColors(Color(0xFF36A98A), Color(0xFFC6F7E6))
    TopPickMood.WORK -> MoodColors(Color(0xFF3E63C9), Color(0xFFC4D2FF))
}

fun moodTitleRes(mood: TopPickMood): Int = when (mood) {
    TopPickMood.ENERGY -> R.string.mood_energy
    TopPickMood.RELAX -> R.string.mood_relax
    TopPickMood.FEELING_BLUE -> R.string.mood_feeling_blue
    TopPickMood.FEEL_GOOD -> R.string.mood_feel_good
    TopPickMood.FOCUS -> R.string.mood_focus
    TopPickMood.HEARTBREAK -> R.string.mood_heartbreak
    TopPickMood.LOVE -> R.string.mood_love
    TopPickMood.AFTER_HOURS -> R.string.mood_after_hours
    TopPickMood.ALONE_TIME -> R.string.mood_alone_time
    TopPickMood.CHILL -> R.string.mood_chill
    TopPickMood.COMMUTING -> R.string.mood_commuting
    TopPickMood.EATING_COOKING -> R.string.mood_eating_cooking
    TopPickMood.EVENING -> R.string.mood_evening
    TopPickMood.FITNESS -> R.string.mood_fitness
    TopPickMood.GAMING -> R.string.mood_gaming
    TopPickMood.HOME -> R.string.mood_home
    TopPickMood.MORNING -> R.string.mood_morning
    TopPickMood.MOTIVATION -> R.string.mood_motivation
    TopPickMood.OUTDOORS -> R.string.mood_outdoors
    TopPickMood.PARTY -> R.string.mood_party
    TopPickMood.SLEEP -> R.string.mood_sleep
    TopPickMood.SOCIAL -> R.string.mood_social
    TopPickMood.SPORTS -> R.string.mood_sports
    TopPickMood.SUMMERTIME -> R.string.mood_summertime
    TopPickMood.HOLIDAY -> R.string.mood_holiday
    TopPickMood.WEEKEND -> R.string.mood_weekend
    TopPickMood.WELLBEING -> R.string.mood_wellbeing
    TopPickMood.WORK -> R.string.mood_work
}

/** Only the seven core moods have a line — TopPicks.moodFor() never picks another. */
fun moodLineRes(mood: TopPickMood): Int? = when (mood) {
    TopPickMood.ENERGY -> R.string.mood_energy_line
    TopPickMood.RELAX -> R.string.mood_relax_line
    TopPickMood.FEELING_BLUE -> R.string.mood_feeling_blue_line
    TopPickMood.FEEL_GOOD -> R.string.mood_feel_good_line
    TopPickMood.FOCUS -> R.string.mood_focus_line
    TopPickMood.HEARTBREAK -> R.string.mood_heartbreak_line
    TopPickMood.LOVE -> R.string.mood_love_line
    else -> null
}

// ---- Top Picks card ----

private val TopPickEyebrowStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.02).em,
)

private val TopPickTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 21.sp,
    letterSpacing = (-0.03).em,
)

private val TopPickSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.02).em,
)

// the caption sits on the picture's own darkened foot, as on Apple's photo cards
private val TopPickPhotoScrim = Brush.verticalGradient(
    0.45f to Color.Transparent,
    0.72f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.82f),
)

private val PhotoEyebrow = Color.White.copy(alpha = 0.62f)
private val PersonalStationEyebrow = Color(0xFFFFD2DA)
private val DiscoveryStationEyebrow = Color(0xFFCDBDFF)

// Classic draws these two as canvas art; Modern uses the same hues as gradients.
private val PersonalStationGradient = Brush.verticalGradient(
    listOf(Color(0xFFF06292), Color(0xFFAD1457)),
)
private val DiscoveryStationGradient = Brush.verticalGradient(
    listOf(Color(0xFF9575CD), Color(0xFF4527A0)),
)

/** What a card says: the small coloured line, the title, and an optional line under it. */
private class TopPickCardText(val eyebrow: String, val title: String, val subtitle: String)

@Composable
private fun topPickCardText(pick: TopPick, listenerName: String?): TopPickCardText {
    val artist = pick.artistName.orEmpty()
    return when (pick.kind) {
        TopPickKind.LATEST_RELEASE -> TopPickCardText(
            eyebrow = stringResource(R.string.top_pick_new_from, artist),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.PERSONAL_STATION -> TopPickCardText(
            eyebrow = stringResource(R.string.top_pick_made_for_you),
            title = listenerName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.top_pick_named_station, it) }
                ?: stringResource(R.string.top_pick_your_station),
            subtitle = "",
        )
        TopPickKind.MOOD -> {
            val mood = pick.mood ?: TopPickMood.FEEL_GOOD
            TopPickCardText(
                eyebrow = stringResource(R.string.top_pick_mood_for_you),
                title = stringResource(moodTitleRes(mood)),
                subtitle = moodLineRes(mood)?.let { stringResource(it) }.orEmpty(),
            )
        }
        TopPickKind.ARTIST_AND_SIMILAR -> TopPickCardText(
            eyebrow = stringResource(R.string.top_pick_featuring, artist),
            title = stringResource(R.string.top_pick_and_similar, artist),
            subtitle = "",
        )
        TopPickKind.FEATURED_ARTIST -> TopPickCardText(
            eyebrow = stringResource(R.string.top_pick_featuring, artist),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.NEW_RELEASE_MIX -> TopPickCardText(
            eyebrow = stringResource(R.string.top_pick_made_for_you),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.DISCOVERY_STATION -> TopPickCardText(
            eyebrow = stringResource(R.string.top_pick_made_for_you),
            title = stringResource(R.string.top_pick_discovery_station),
            subtitle = "",
        )
    }
}

/**
 * One Top Picks card: a tall (3:4) card, artwork filling it, caption overlaid
 * at the bottom. [onClick] gets the card and the title it was showing — the
 * station's name for the queue. The caller sets the width (Classic's rule:
 * 60% of the page, capped at 280dp).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopPickCard(
    pick: TopPick,
    listenerName: String?,
    onClick: (TopPick, String) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = topPickCardText(pick, listenerName)
    // the & Similar eyebrow is a deterministic hue off the artist's name, so
    // every artist gets their own accent and the same artist keeps it
    val eyebrowColor = when (pick.kind) {
        TopPickKind.PERSONAL_STATION -> PersonalStationEyebrow
        TopPickKind.DISCOVERY_STATION -> DiscoveryStationEyebrow
        TopPickKind.MOOD -> (pick.mood ?: TopPickMood.FEEL_GOOD).colors().eyebrowColor()
        TopPickKind.ARTIST_AND_SIMILAR -> {
            val hue = ((pick.artistName.orEmpty().hashCode() and 0x7FFFFFFF) % 360).toFloat()
            Color.hsv(hue, 0.45f, 1f)
        }
        else -> PhotoEyebrow
    }
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(HomeCardCornerRadius))
            .background(BoneColor)
            .combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = { onClick(pick, text.title) },
                onLongClick = onLongClick,
            ),
    ) {
        when (pick.kind) {
            TopPickKind.PERSONAL_STATION -> Box(
                Modifier.fillMaxSize().background(PersonalStationGradient),
            )
            TopPickKind.DISCOVERY_STATION -> Box(
                Modifier.fillMaxSize().background(DiscoveryStationGradient),
            )
            TopPickKind.MOOD -> {
                val moodColors = (pick.mood ?: TopPickMood.FEEL_GOOD).colors()
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                moodColors.background,
                                lerp(moodColors.background, Color.Black, 0.38f),
                            ),
                        ),
                    ),
                )
            }
            else -> {
                AsyncImage(
                    model = pick.images.firstOrNull(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(TopPickPhotoScrim))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = text.eyebrow,
                style = TopPickEyebrowStyle,
                color = eyebrowColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text.title,
                style = TopPickTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.subtitle.isNotBlank()) {
                Text(
                    text = text.subtitle,
                    style = TopPickSubtitleStyle,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- Find Your Mood ----

private val HomeMoodTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)

/**
 * A mood's square tile and its name under it, laid out like a HomeCoverCard.
 * The tile is the mood's own colour, lit from the top; [onClick] gets the
 * mood and the title it was showing, for the station's queue name.
 */
@Composable
fun HomeMoodTile(
    mood: TopPickMood,
    onClick: (TopPickMood, String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = homeCardSize(),
) {
    val title = stringResource(moodTitleRes(mood))
    val colors = remember(mood) { mood.colors() }
    val tileBrush = remember(colors) {
        Brush.verticalGradient(
            listOf(
                colors.background,
                lerp(colors.background, Color.Black, 0.38f),
            ),
        )
    }
    val washBrush = remember(colors) {
        Brush.radialGradient(
            0f to colors.stroke.copy(alpha = 0.30f),
            0.65f to Color.Transparent,
        )
    }
    Column(
        modifier = modifier
            .width(size)
            .clickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = { onClick(mood, title) },
            ),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(HomeCardCornerRadius))
                .background(tileBrush),
        ) {
            // a soft pale wash so the flat colour reads as lit, not printed
            Box(Modifier.fillMaxSize().background(washBrush))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = HomeMoodTitleStyle,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The Find Your Mood shelf: the seven core moods as a row; expanded, all
 * twenty-seven moods and activities as a grid. The expand/collapse is an
 * AnimatedVisibility so the grid grows in place instead of popping.
 */
@Composable
fun FindYourMoodContent(
    expanded: Boolean,
    onMoodClick: (TopPickMood, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coreMoods = remember { TopPickMood.entries.filter { it.isCoreMood } }
    BoxWithConstraints(modifier = modifier) {
        // three tiles across, on the Home inset, at the Home card spacing
        val gridSize = (maxWidth - HomeContentInset * 2 - HomeCardSpacing * 2) / 3
        Column {
            AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = HomeContentInset),
                    horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                ) {
                    items(coreMoods, key = { it.name }) { mood ->
                        HomeMoodTile(mood = mood, onClick = onMoodClick)
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HomeContentInset),
                ) {
                    // Apple's seven moods first, then the activities, as on Classic
                    TopPickMood.entries.chunked(3).forEach { rowMoods ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowMoods.forEach { mood ->
                                HomeMoodTile(mood = mood, onClick = onMoodClick, size = gridSize)
                            }
                            repeat(3 - rowMoods.size) {
                                Spacer(Modifier.width(gridSize))
                            }
                        }
                    }
                }
            }
        }
    }
}

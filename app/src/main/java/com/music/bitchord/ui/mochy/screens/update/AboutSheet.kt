// about app, upstream credits and license sheets pushed from the version sheet
// Ported from Mochy Music (GPL-3.0). Rebranded to ChordWave with ChordWave's own lineage:
// BitChord (kushagrasinghx) ← Metrolist ← RiMusic, and Mochy Music for the Modern UI design.
// The donate row and the DevSpace hook are gone (both out of scope for the clone);
// lead-dev rows open the GitHub profile like every other credit.

package com.music.bitchord.ui.mochy.screens.update

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.pressBounce
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private const val ChordWaveOwner = "Rikki53"
private const val ChordWaveRepo = "ChordWave"
private const val ChordWaveRepoUrl = "https://github.com/$ChordWaveOwner/$ChordWaveRepo"

@Immutable
private data class LeadDevEntry(
    val name: String,
    val githubUser: String,
    @DrawableRes val fallbackAvatar: Int? = null,
)

@Immutable
private data class LicenseEntry(
    val tag: String,
    val title: String,
    val subtitle: String,
    val url: String,
)

@Immutable
private data class ContributorEntry(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

private val LeadDevs = listOf(
    LeadDevEntry(
        name = "Rikki53",
        githubUser = "rikki53",
    ),
)

// ChordWave-only: upstream GPL attribution lives in the repo LICENSE/source,
// not in the in-app license sheet.
private val Licenses = listOf(
    LicenseEntry(
        tag = "GPL-3.0",
        title = "ChordWave · GNU General Public License v3.0",
        subtitle = "Free and open source software, source code on GitHub",
        url = "$ChordWaveRepoUrl/blob/main/LICENSE",
    ),
)

private val FallbackContributors = listOf(
    ContributorEntry(
        login = "Rikki53",
        avatarUrl = githubAvatarUrl("rikki53"),
        profileUrl = "https://github.com/rikki53",
    ),
)

// kept in memory for the session so reopening the about sheet never hits github twice
@Volatile
private var cachedContributors: List<ContributorEntry>? = null

// github redirects username.png straight to the user's current avatar without counting against the api rate limit
private fun githubAvatarUrl(githubUser: String): String =
    "https://github.com/$githubUser.png?size=160"

private suspend fun fetchContributors(): List<ContributorEntry> {
    cachedContributors?.let { return it }
    // GitHub counts the fork's inherited history, so the upstream projects'
    // authors would list as contributors; the row stays ChordWave-only.
    val upstreamLogins = setOf(
        "kushagrasinghx", "mostafaalagamy", "realidkroo", "fast4x",
        "maxrave-dev", "tranxuanthang", "jaakkopasanen", "rukamori",
        "ibratabian17", "boidushya",
    )
    return withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$ChordWaveOwner/$ChordWaveRepo/contributors?per_page=30")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "chordwave-android")
            }
            try {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val type = obj.optString("type", "User")
                        if (type.equals("Bot", ignoreCase = true)) continue
                        val login = obj.optString("login").takeIf { it.isNotBlank() } ?: continue
                        if (login.endsWith("[bot]", ignoreCase = true)) continue
                        if (login.lowercase() in upstreamLogins) continue
                        val avatar = obj.optString("avatar_url").ifBlank { githubAvatarUrl(login) }
                        val html = obj.optString("html_url").ifBlank { "https://github.com/$login" }
                        add(ContributorEntry(login = login, avatarUrl = avatar, profileUrl = html))
                    }
                }.ifEmpty { FallbackContributors }
                    .distinctBy { it.login.lowercase() }
                    .also { cachedContributors = it }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(FallbackContributors)
    }
}

fun PopupSheetState.showAboutSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        AboutSheetContent(popup = popup)
    }
}

private fun PopupSheetState.showLicenseSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        LicenseSheetContent()
    }
}

@Composable
private fun ColumnScope.AboutSheetContent(
    popup: PopupSheetState,
) {
    val context = LocalContext.current
    val installed = shortVersion(BuildConfig.VERSION_NAME)
    val scrollState = rememberScrollState()
    val ringScrollRangePx = with(LocalDensity.current) { 180.dp.toPx() }
    var contributors by remember { mutableStateOf(cachedContributors ?: FallbackContributors) }

    LaunchedEffect(Unit) {
        contributors = fetchContributors()
    }

    Box(Modifier.fillMaxWidth()) {
        // zero-size layout places the spinning ring behind the top icon without pushing the column down
        VersionRing(
            text = "CHORDWAVE $installed",
            scrollProgress = { scrollState.value / ringScrollRangePx },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            minHeight = 0,
                            maxWidth = Constraints.Infinity,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                    layout(0, 0) {
                        placeable.place(x = -placeable.width / 2, y = (-166).dp.roundToPx())
                    }
                },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MenuRowSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chordwave_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "About app",
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ChordWave | $installed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MenuRowSurface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "ChordWave is a music app but\nkinda cool.",
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("actions")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutPillRow(
                    text = "ChordWave on GitHub \u2192",
                    onClick = { context.openUrl(ChordWaveRepoUrl) },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("meet the lead dev")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                LeadDevs.forEach { dev ->
                    PersonRow(
                        name = dev.name,
                        subtitle = null,
                        avatarUrl = githubAvatarUrl(dev.githubUser),
                        fallbackRes = dev.fallbackAvatar,
                        showArrow = true,
                        onClick = { context.openUrl("https://github.com/${dev.githubUser}") },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("License")
            Spacer(Modifier.height(8.dp))
            AboutCardButton(
                text = "More about license",
                onClick = { popup.showLicenseSheet() },
            )

            Spacer(Modifier.height(18.dp))
            SectionHeader("Cool contributors")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                contributors.forEach { contributor ->
                    PersonRow(
                        name = contributor.login,
                        subtitle = null,
                        avatarUrl = contributor.avatarUrl,
                        showArrow = false,
                        onClick = { context.openUrl(contributor.profileUrl) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.LicenseSheetContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "License",
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Licenses.forEach { entry ->
                LicenseCard(
                    entry = entry,
                    onClick = { context.openUrl(entry.url) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun AboutPillRow(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutCardButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonRow(
    name: String,
    subtitle: String?,
    avatarUrl: String,
    showArrow: Boolean,
    onClick: () -> Unit,
    @DrawableRes fallbackRes: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        GithubAvatar(
            url = avatarUrl,
            size = 44.dp,
            fallbackRes = fallbackRes,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showArrow) {
            Spacer(Modifier.width(10.dp))
            ArrowBadge()
        }
    }
}

@Composable
private fun LicenseCard(
    entry: LicenseEntry,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.subtitle,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(12.dp))
        ArrowBadge()
    }
}

@Composable
private fun ArrowBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun GithubAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes fallbackRes: Int? = null,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build()
    }
    val fallback = fallbackRes?.let { painterResource(it) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            placeholder = fallback,
            error = fallback,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

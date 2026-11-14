// Ported from Mochy Music (GPL-3.0):
// ui/screens/settings/importsync/YouTubeSyncScreen.kt
//
// ADAPTATIONS:
// - Navigation: MochyNavigator instead of NavController (no NavHost in the
//   Modern host).
// - Account name/email come from MainViewModel.account (CW's data.model
//   Account, loaded in MainViewModel.init when signed in and refreshed by
//   onWebSession) — NOT from DataStore prefs. Mochy's AccountNameKey /
//   AccountEmailKey are therefore NOT needed here; CW's AuthStore owns the
//   session and MainViewModel owns the identity. See NEEDS.md.
// - Brand: "Mochy's history" -> "ChordWave's history", "only exists in
//   Mochy" -> "only exists in ChordWave".
package com.music.bitchord.ui.mochy.screens.settings.importsync

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.R
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.constants.YtExcludeImportsKey
import com.music.bitchord.ui.mochy.constants.YtPullHistoryKey
import com.music.bitchord.ui.mochy.constants.YtReportPlaysKey
import com.music.bitchord.ui.mochy.constants.YtSyncNewPlaylistsKey
import com.music.bitchord.ui.mochy.constants.YtmSyncKey
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.rememberYouTubeSignedIn
import com.music.bitchord.ui.mochy.viewmodels.ImportSyncViewModel

@Composable
fun YouTubeSyncScreen(
    navigator: MochyNavigator,
    viewModel: ImportSyncViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = viewModel(),
) {
    val signedIn = rememberYouTubeSignedIn()
    val account by mainViewModel.account.collectAsState()
    val accountName = account?.name.orEmpty()
    val accountEmail = account?.email.orEmpty()
    val (autoSync, onAutoSyncChange) = rememberPreference(YtmSyncKey, true)
    val (syncNewPlaylists, onSyncNewPlaylistsChange) = rememberPreference(YtSyncNewPlaylistsKey, false)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, true)
    val (pullHistory, onPullHistoryChange) = rememberPreference(YtPullHistoryKey, false)
    val (excludeImports, onExcludeImportsChange) = rememberPreference(YtExcludeImportsKey, false)
    val state by viewModel.youTubeState.collectAsState()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmUpload by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    SubSettingsScaffold(
        title = "Sync",
        onBack = { navigator.popBackStack() },
    ) {
        SettingsGroup(
            title = "YouTube account",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                if (signedIn) {
                    SettingsItem(
                        title = { Text(accountName.ifBlank { "Signed in" }) },
                        descriptionText = accountEmail.ifBlank { "YouTube Music" },
                        icon = painterResource(R.drawable.account),
                        iconShape = CircleShape,
                        trailingContent = {
                            TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
                        },
                    )
                } else {
                    SettingsItem(
                        title = { Text("Sign in to YouTube") },
                        descriptionText = "Needed for sync. Your login stays on this phone",
                        icon = painterResource(R.drawable.login),
                        iconShape = CircleShape,
                        onClick = { navigator.navigate(YouTubeLoginRoute) },
                    )
                }
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Keep in sync",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                toggleItem(
                    title = "Sync library on launch",
                    description = "Likes, library, artists and saved playlists from YouTube",
                    icon = R.drawable.sync,
                    checked = autoSync,
                    enabled = signedIn,
                    onChange = onAutoSyncChange,
                ),
                toggleItem(
                    title = "New playlists go to YouTube",
                    description = "Playlists you create here are created on YouTube too",
                    icon = R.drawable.playlist_add,
                    checked = syncNewPlaylists,
                    enabled = signedIn,
                    onChange = onSyncNewPlaylistsChange,
                ),
                toggleItem(
                    title = "Send plays to YouTube history",
                    description = "Songs you play here show up in your YouTube history",
                    icon = R.drawable.history,
                    checked = reportPlays,
                    enabled = signedIn,
                    onChange = onReportPlaysChange,
                ),
                toggleItem(
                    title = "Copy YouTube history here",
                    description = "Plays from your other devices join ChordWave's history on each sync",
                    icon = R.drawable.history,
                    checked = pullHistory,
                    enabled = signedIn,
                    onChange = onPullHistoryChange,
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Other apps",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                toggleItem(
                    title = stringResource(R.string.yt_sync_exclude_title),
                    description = stringResource(R.string.yt_sync_exclude_desc),
                    icon = R.drawable.cloud,
                    checked = excludeImports,
                    enabled = true,
                    onChange = onExcludeImportsChange,
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Now",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Sync now") },
                    descriptionText = if (pullHistory) "Library, playlists and history" else "Library and playlists",
                    icon = painterResource(R.drawable.refresh),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = viewModel::syncNow,
                ),
                SettingsItem(
                    title = { Text("Copy YouTube history now") },
                    descriptionText = "Only adds plays ChordWave doesn't have yet",
                    icon = painterResource(R.drawable.history),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = viewModel::pullHistoryNow,
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.yt_sync_undo_title)) },
                    descriptionText = stringResource(R.string.yt_sync_undo_desc),
                    icon = painterResource(R.drawable.undo),
                    iconShape = CircleShape,
                    enabled = signedIn && state.canUndo && !state.running,
                    onClick = { confirmUndo = true },
                ),
                SettingsItem(
                    title = { Text("Put local playlists on YouTube") },
                    descriptionText = "Copy local playlists to YouTube",
                    detail = "Creates a YouTube copy of every playlist that only exists here",
                    icon = painterResource(R.drawable.cloud),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = { confirmUpload = true },
                ),
            ),
        )

        if (state.running || state.message != null) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                if (state.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(state.label, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::clearYouTubeMessage) { Text("OK") }
                }
            }
        }

        if (!signedIn) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Import from a YouTube playlist link doesn't need an account. Use Import → YouTube Music.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmSignOut) {
        DefaultDialog(
            onDismiss = { confirmSignOut = false },
            horizontalAlignment = Alignment.Start,
            title = { Text("Sign out of YouTube?") },
            buttons = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
        ) {
            Text(
                "Everything already in your library stays. Sync stops until you sign in again.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmUndo) {
        DefaultDialog(
            onDismiss = { confirmUndo = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.yt_sync_undo_dialog_title)) },
            buttons = {
                TextButton(onClick = { confirmUndo = false }) { Text("Cancel") }
                TextButton(onClick = {
                    confirmUndo = false
                    viewModel.undoLastSync(withYouTube = false)
                }) { Text(stringResource(R.string.yt_sync_undo_phone)) }
                TextButton(
                    enabled = signedIn,
                    onClick = {
                        confirmUndo = false
                        viewModel.undoLastSync(withYouTube = true)
                    },
                ) { Text(stringResource(R.string.yt_sync_undo_both)) }
            },
        ) {
            Text(
                stringResource(R.string.yt_sync_undo_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.yt_sync_undo_both_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmUpload) {
        DefaultDialog(
            onDismiss = { confirmUpload = false },
            horizontalAlignment = Alignment.Start,
            title = { Text("Put local playlists on YouTube?") },
            buttons = {
                TextButton(onClick = { confirmUpload = false }) { Text("Cancel") }
                TextButton(onClick = {
                    confirmUpload = false
                    viewModel.uploadLocalPlaylists()
                }) { Text("Create") }
            },
        ) {
            Text(
                "Each playlist that only exists in ChordWave gets a private copy on your YouTube account. After that, changes go to both.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun toggleItem(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = title,
    descriptionText = description,
    icon = painterResource(icon),
    iconShape = CircleShape,
    enabled = enabled,
    onClick = { onChange(!checked) },
    trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
)

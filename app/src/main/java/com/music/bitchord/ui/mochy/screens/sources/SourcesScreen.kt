// ChordWave Modern restyle of the classic ui/screens/SourcesScreen.kt (Phase 5).
//
// Where the app is allowed to get audio from, in Mochy's modules-page idiom:
// reorderable source cards (Mochy ModuleList: drag handle beside the card,
// swipe left to remove, dashed slot to add), the remote libraries as Mochy
// SettingsGroup cards, and the editors as PopupSheets via LocalMenuState.
//
// Engine behaviour is UNCHANGED — every read and write below is the classic
// screen's own: SourceRegistry for configs/order/enable, the same health
// probe (including the manifest rename), LosslessOutput for the lossless
// gate, AppSettings for WebDAV/SMB/matching, SmbRepository/WebDavRepository
// for connection tests. What changed is only presentation, plus where the
// classic screen raised alerts to MainActivity: the editor, quality picker
// and outdated explanation are PopupSheets here (SourceEditorSheet.kt),
// and the two confirmations are Mochy ActionPromptDialogs in this file.
//
// Classic flows NOT carried over, deliberately (see NEEDS.md):
// - SignatureApiEditor: the classic Sources screen never linked it — it
//   lives in the classic settings sheet (artist signatures) and is mounted
//   from MainActivity. It belongs to a future Modern settings page, not here.

package com.music.bitchord.ui.mochy.screens.sources

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.permits
import com.music.bitchord.data.smb.SmbRepository
import com.music.bitchord.data.sources.AddonSource
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.webdav.WebDavRepository
import com.music.bitchord.playback.audio.LosslessOutput
import com.music.bitchord.ui.mochy.component.ActionPromptDialog
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.audiolab.LabSlider
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The Modern route for this screen. Registered by the coordinator in MochyApp (see NEEDS.md). */
const val SourcesRoute = "sources"

@Composable
fun SourcesScreen(navigator: MochyNavigator) {
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val upgradeLengthSlackSeconds by AppSettings.upgradeLengthSlackSeconds.collectAsStateWithLifecycle()
    val webdavUrl by AppSettings.webdavUrl.collectAsStateWithLifecycle()
    val webdavUsername by AppSettings.webdavUsername.collectAsStateWithLifecycle()
    val webdavPassword by AppSettings.webdavPassword.collectAsStateWithLifecycle()
    val smbHost by AppSettings.smbHost.collectAsStateWithLifecycle()
    val smbShare by AppSettings.smbShare.collectAsStateWithLifecycle()
    val smbUsername by AppSettings.smbUsername.collectAsStateWithLifecycle()
    val smbPassword by AppSettings.smbPassword.collectAsStateWithLifecycle()

    /** Last known reachability per source, filled in as the probes come back. */
    val health = remember { mutableStateMapOf<String, SourceHealth>() }
    val losslessOutput by LosslessOutput.state.collectAsStateWithLifecycle()

    val menuState = LocalMenuState.current
    var removing by remember { mutableStateOf<SourceConfig?>(null) }
    var confirmJioSaavn by remember { mutableStateOf(false) }

    // Every source that has a server to reach is probed, so an addon gets a
    // reachability line — which is the only feedback that a URL just pasted in
    // was any good, and the only place a manifest that fails validation gets to
    // say why. Except an addon waiting on a lossless output: it is not to be
    // contacted at all until one is connected, and connecting one re-runs this.
    val probeKey = configs.filter { it.kind.needsServer }
        .joinToString { "${it.id}@${it.baseUrl}@${it.checkValidLossless}" } + "@${losslessOutput.capable}"
    LaunchedEffect(probeKey) {
        configs.filter { it.kind.needsServer && it.isComplete && !it.awaitsLosslessOutput }.forEach { config ->
            val source = SourceRegistry.instance(config.id) ?: return@forEach
            health[config.id] = withContext(Dispatchers.IO) {
                runCatching { source.health() }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            // An addon names itself, so the row takes that name rather than a
            // hostname — and rather than a field asking the user to make one up.
            //
            // Written here, off the probe that has just fetched the manifest
            // anyway, because this is the one place that both talks to every
            // configured source and is allowed to change what is stored. It
            // settles after one pass: the next probe finds the label already
            // equal and writes nothing, so there is no loop between this and
            // the [configs] it is reading. A rename on the addon's side is
            // picked up the next time this screen is opened.
            if (source is AddonSource) {
                val named = withContext(Dispatchers.IO) { runCatching { source.manifestName() }.getOrNull() }
                if (named != null && named != config.label) {
                    // Off the stored config, not the one this pass started with:
                    // the probe above may have just refreshed it, and the user
                    // may have picked a quality in the meantime.
                    SourceRegistry.config(config.id)?.let { SourceRegistry.update(it.copy(label = named)) }
                }
            }
        }
    }

    // The ceiling in force *right now*, which is the only one this screen can
    // speak for: the rows below are switches the user set once, and whether a
    // given source is reached also depends on which connection is up. Reading
    // it here rather than baking it into the switches is deliberate — the
    // preset write that used to do that is what made a mobile-data rung follow
    // the user onto Wi-Fi. See [AudioQuality.permits].
    val ceiling = if (metered == true) cellularQuality else wifiQuality

    /** Whether the ceiling in force right now would cap a lossless stream anyway. */
    val cappedByQuality = ceiling != AudioQuality.LOSSLESS
    // Asked of the kinds themselves rather than of the module specifically, so
    // a source added later answers this on its own terms instead of being
    // invisible to it.
    val anyLosslessSource = configs.any { it.enabled && it.isComplete && it.kind.canServeLossless }

    // The addons are split out from the rest because they are the only
    // rows whose order is the *user's*. Everything else ranks by kind,
    // which is fixed in [SourceKind] and not something a drag should be
    // able to argue with — a gesture that let JioSaavn be dragged above
    // an addon would be offering a choice the resolver does not
    // actually honour.
    //
    // They are still one continuously numbered list. The numbers say
    // what order the sources are tried in, and restarting the count
    // under a second heading would break the one thing this screen is
    // for.
    val addons = configs.filter { it.kind.isUserAdded }
    val fixed = configs.filterNot { it.kind.isUserAdded }
        .sortedBy { it.kind.rank }

    // One card's behaviour, shared by the reorderable addons and the fixed
    // rows: tap to edit (only a source with an address has anything to
    // edit), quality and outdated lines open their sheets, and the switch
    // is JioSaavn's opt-in gate or a plain toggle. YouTube gets no switch
    // at all — see [SourceRegistry.setEnabled] for why one would be a lie.
    val card: @Composable (Int, SourceConfig, Boolean) -> Unit =
        { position, config, isDragging ->
            SourceRowCard(
                position = position,
                config = config,
                health = health[config.id],
                // Switched on, but not reached on this connection. Said
                // on the row rather than by moving the switch, so the
                // switch keeps meaning "I want this source" and the
                // connection keeps meaning "…and here is what it costs
                // today".
                skippedByQuality = config.enabled && !ceiling.permits(config.kind),
                awaitingOutput = if (config.enabled && config.awaitsLosslessOutput) losslessOutput else null,
                // Read off the stored manifest, so it shows before the
                // probe below answers, and on a server that is down.
                needsUpdate = config.needsAddonUpdate,
                onMetered = metered == true,
                ceiling = ceiling,
                // Anything the user configured is theirs to edit or
                // delete. JioSaavn and YouTube have no address to
                // change, so a tap on them would open an empty editor.
                onClick = if (config.kind.needsServer) ({ menuState.showSourceEditorSheet(config) }) else null,
                onChooseQuality = { menuState.showSourceQualitySheet(config) },
                onExplainOutdated = { menuState.showOutdatedAddonSheet(config) },
                onToggle = if (config.kind == SourceKind.YOUTUBE) {
                    null
                } else {
                    ({ enabled ->
                        if (config.kind == SourceKind.JIOSAAVN && enabled && !config.enabled) {
                            confirmJioSaavn = true
                        } else {
                            SourceRegistry.setEnabled(config.id, enabled)
                        }
                    })
                },
                lifted = isDragging,
            )
        }

    SubSettingsScaffold(
        title = stringResource(R.string.source),
        onBack = { navigator.popBackStack() },
    ) {
        Text(
            text = buildString {
                append(stringResource(R.string.sources_order_footer))
                if (cappedByQuality) {
                    append("\n\n")
                    append(stringResource(R.string.sources_quality_capped))
                } else if (!anyLosslessSource) {
                    append("\n\n")
                    append(stringResource(R.string.sources_no_lossless))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 18.dp),
        )

        SourcesSectionTitle(
            title = stringResource(R.string.sources_order_header),
            subtitle = stringResource(R.string.sources_swipe_to_delete),
        )

        ReorderableAddonList(
            addons = addons,
            onReorder = { SourceRegistry.reorderAddons(it.map(SourceConfig::id)) },
            onRemove = { removing = it },
            card = card,
        )

        fixed.forEachIndexed { index, config ->
            card(addons.size + index + 1, config, false)
            Spacer(Modifier.height(SourceCardGap))
        }

        // The one shut-out an addon can't tell apart from "not lossless":
        // headphones on LDAC, LHDC or SSC UHQ whose codec Android won't name without
        // the Nearby devices permission. Offered only then, and only from
        // here, where the reason for asking is on screen.
        val waiting = configs.any { it.enabled && it.awaitsLosslessOutput }
        if (waiting && losslessOutput.bluetoothCodecUnknown) {
            BluetoothCodecPermissionCard()
            Spacer(Modifier.height(SourceCardGap))
        }

        DashedSlot(
            title = stringResource(R.string.add_addon),
            detail = stringResource(R.string.add_addon_detail),
            compact = addons.isNotEmpty(),
            onClick = { menuState.showSourceEditorSheet(SourceConfig(kind = SourceKind.ADDON)) },
        )

        Spacer(Modifier.height(34.dp))

        // A personal library rather than a catalogue in the order above: it
        // holds the listener's own files instead of answering for YouTube's,
        // so it takes no part in the ranking and gets its own group.
        RemoteLibrarySection(
            iconRes = R.drawable.cloud,
            title = stringResource(R.string.webdav),
            description = stringResource(R.string.webdav_description),
            subtitle = if (webdavUrl.isBlank()) {
                stringResource(R.string.webdav_not_configured)
            } else if (webdavUsername.isNotBlank()) {
                "$webdavUrl · $webdavUsername"
            } else {
                webdavUrl
            },
            listSubtitle = stringResource(R.string.webdav_subtitle),
            disconnectTitle = stringResource(R.string.webdav_disconnect),
            configured = webdavUrl.isNotBlank(),
            testFailedRes = R.string.webdav_test_failed,
            onEdit = { menuState.showWebDavEditorSheet() },
            onTest = {
                WebDavRepository.testConnection(webdavUrl, webdavUsername, webdavPassword)
            },
            onDisconnect = { AppSettings.clearWebDav() },
        )

        Spacer(Modifier.height(24.dp))

        // A personal library on a file share, filed next to the WebDAV one
        // for the same reason: it holds the listener's own files instead of
        // answering for YouTube's, so it takes no part in the ranking above.
        RemoteLibrarySection(
            iconRes = R.drawable.storage,
            title = stringResource(R.string.smb),
            description = stringResource(R.string.smb_description),
            subtitle = if (smbHost.isBlank() || smbShare.isBlank()) {
                stringResource(R.string.smb_not_configured)
            } else if (smbUsername.isNotBlank()) {
                "$smbHost · $smbShare · $smbUsername"
            } else {
                "$smbHost · $smbShare"
            },
            listSubtitle = stringResource(R.string.smb_subtitle),
            disconnectTitle = stringResource(R.string.smb_disconnect),
            configured = smbHost.isNotBlank() && smbShare.isNotBlank(),
            testFailedRes = R.string.smb_test_failed,
            onEdit = { menuState.showSmbEditorSheet() },
            onTest = {
                SmbRepository.testConnection(
                    smbHost,
                    smbShare,
                    AppSettings.smbBasePath.value,
                    smbUsername,
                    smbPassword,
                )
            },
            onDisconnect = { AppSettings.clearSmb() },
        )

        Spacer(Modifier.height(34.dp))

        SourcesSectionTitle(title = stringResource(R.string.source_matching))
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SearchColors.Field),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                LabSlider(
                    label = stringResource(R.string.upgrade_length_slack),
                    valueText = stringResource(R.string.seconds_short, upgradeLengthSlackSeconds),
                    value = upgradeLengthSlackSeconds.toFloat(),
                    onValueChange = {
                        AppSettings.setUpgradeLengthSlackSeconds(it.roundToInt())
                    },
                    valueRange = AppSettings.MIN_UPGRADE_LENGTH_SLACK_SECONDS.toFloat()..
                        AppSettings.MAX_UPGRADE_LENGTH_SLACK_SECONDS.toFloat(),
                    steps = AppSettings.MAX_UPGRADE_LENGTH_SLACK_SECONDS -
                        AppSettings.MIN_UPGRADE_LENGTH_SLACK_SECONDS - 1,
                )
                Text(
                    text = stringResource(R.string.upgrade_length_slack_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.upgrade_length_slack_footer),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )

        Spacer(Modifier.height(140.dp))
    }

    // Swipe-to-delete asks first, Mochy's modules-page pattern: a removal
    // is a destructive act on a server the user set up, and a swipe is
    // easy to fire by accident. (The editor sheet's Remove stays
    // immediate, as the classic alert's was.)
    val removingConfig = removing
    if (removingConfig != null) {
        ActionPromptDialog(
            title = stringResource(R.string.sources_remove_title, removingConfig.displayName),
            onDismiss = { removing = null },
            onCancel = { removing = null },
            onConfirm = {
                SourceRegistry.remove(removingConfig.id)
                removing = null
            },
        ) {
            Text(
                text = stringResource(R.string.sources_remove_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    // JioSaavn is opt-in because catalogue matches can select the wrong
    // recording; the warning is shown before the switch is allowed to move.
    if (confirmJioSaavn) {
        ActionPromptDialog(
            title = stringResource(R.string.enable_jiosaavn),
            onDismiss = { confirmJioSaavn = false },
            onCancel = { confirmJioSaavn = false },
            onConfirm = {
                SourceRegistry.configs.value
                    .firstOrNull { it.kind == SourceKind.JIOSAAVN }
                    ?.let { SourceRegistry.setEnabled(it.id, true) }
                confirmJioSaavn = false
            },
        ) {
            Text(
                text = stringResource(R.string.jiosaavn_mismatch_warning),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A personal library section: the server row, plus test and disconnect rows
 * once something is configured. Shared by WebDAV and SMB, which differ only
 * in what "configured" means and which repository answers the test — the
 * test state lives here rather than in the screen, so each section tracks
 * its own result.
 */
@Composable
private fun RemoteLibrarySection(
    iconRes: Int,
    title: String,
    description: String,
    subtitle: String,
    listSubtitle: String,
    disconnectTitle: String,
    configured: Boolean,
    testFailedRes: Int,
    onEdit: () -> Unit,
    onTest: suspend () -> Result<Unit>,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
    )
    SettingsGroup(
        title = title,
        style = SettingsGroupStyle.Grouped,
        items = buildList {
            add(
                SettingsItem(
                    icon = painterResource(iconRes),
                    title = { Text(title) },
                    descriptionText = subtitle,
                    onClick = onEdit,
                ),
            )
            if (configured) {
                add(
                    SettingsItem(
                        icon = painterResource(R.drawable.network_update),
                        title = {
                            Text(
                                if (testing) {
                                    stringResource(R.string.testing)
                                } else {
                                    testResult ?: stringResource(R.string.test)
                                },
                            )
                        },
                        descriptionText = listSubtitle,
                        onClick = {
                            if (testing) return@SettingsItem
                            testing = true
                            testResult = null
                            scope.launch {
                                onTest().fold(
                                    onSuccess = {
                                        testResult = context.getString(R.string.connected)
                                    },
                                    onFailure = {
                                        testResult = context.getString(
                                            testFailedRes,
                                            it.message ?: context.getString(R.string.failed),
                                        )
                                    },
                                )
                                testing = false
                            }
                        },
                    ),
                )
                add(
                    SettingsItem(
                        icon = painterResource(R.drawable.delete),
                        title = { Text(disconnectTitle) },
                        onClick = {
                            onDisconnect()
                            testResult = null
                        },
                    ),
                )
            }
        },
    )
}

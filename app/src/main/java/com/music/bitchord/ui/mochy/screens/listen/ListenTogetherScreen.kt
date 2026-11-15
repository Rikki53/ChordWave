// Modern (Mochy-style) restyle of the Classic Listen Together screen
// (ui/screens/ListenTogetherScreen.kt, 1,180 lines). Classic UI is kept; this
// file lives under ui/mochy/* and never touches it.
//
// What changed vs Classic:
// - SubSettingsScaffold + the mochy SettingsGroup (items-based) + custom cards
//   in the Grouped style, instead of the Classic settings sheet.
// - All four party sheets + the server editor + the server-switch consent are
//   PopupSheetState extensions (ListenTogetherSheets.kt, PartyServerSheet.kt)
//   via LocalMenuState, instead of ModalBottomSheet / AlertDialog / the haze
//   AddonEditorAlert. The haze root-mount constraint from PartyServerEditor
//   does not apply here: PopupSheetHost is app-level in the Modern host.
// - Signed-in state comes from rememberYouTubeSignedIn() (CW auth.SignedIn);
//   Sign in navigates to the existing Modern "youtube_login" route.
// - Invite deeplinks arrive via JamInviteLink.pending (host-owned, consumed by
//   MainActivity); the screen runs the same server-consent + preview flow the
//   Classic screen ran from its inviteCode/inviteServer params. The host must
//   navigate here when a pending invite arrives — see NEEDS.md.
// - NEW vs Classic: the shared party queue is shown and manageable
//   (PartyQueueSection.kt), and the server-health row Classic defined but
//   never called (ServerHealthRow, dead code) is live here.
// - No text chat, per the product decision. Max 5 members by default.
//
// Engine use is read-only: ListenTogether's flows and suspend functions are
// called exactly as Classic called them; no engine behaviour is changed.

package com.music.bitchord.ui.mochy.screens.listen

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyActivity
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.ServerConnectionState
import com.music.bitchord.ui.components.rememberQrCodeScanner
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import com.music.bitchord.ui.mochy.utils.rememberYouTubeSignedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The Modern route for this screen; the host dispatches it in DetailContent. */
const val ListenTogetherRoute = "listen_together"

/** Modern sign-in destination (existing route, YouTubeLoginScreen). */
private const val SignInRoute = "youtube_login"

@Composable
fun ListenTogetherScreen(navigator: MochyNavigator) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val menuState = LocalMenuState.current

    val state by ListenTogether.state.collectAsStateWithLifecycle()
    val customServer by ListenTogether.customServerUrl.collectAsStateWithLifecycle()
    val connectionState by ListenTogether.serverConnectionState.collectAsStateWithLifecycle()
    val serverStatus by ListenTogether.serverStatus.collectAsStateWithLifecycle()
    val activity by ListenTogether.activity.collectAsStateWithLifecycle()
    val pendingInvite by JamInviteLink.pending.collectAsStateWithLifecycle()
    val signedIn = rememberYouTubeSignedIn()

    var failure by remember { mutableStateOf<String?>(null) }

    /** The invite URL for a code, pointing at whichever server holds the party. */
    val inviteLinkFor: (String) -> String = { partyCode ->
        val host = ListenTogether.activePartyServerBase()
        if (host == null || host == ListenTogether.defaultServer) {
            JamInviteLink.url(partyCode, null)
        } else {
            JamInviteLink.url(partyCode, host)
        }
    }

    /**
     * Looks a party up and puts its faces on screen, rather than joining it.
     *
     * Both doors in — a tapped link and six characters typed — come through
     * here, so what the listener agrees to is the same picture either way.
     */
    val openPartyPreview: (String, String?) -> Unit = { previewCode, previewServer ->
        failure = null
        scope.launch {
            ListenTogether.previewParty(previewCode, previewServer)
                .onSuccess { preview ->
                    menuState.showJoinConfirmSheet(
                        preview = preview,
                        server = previewServer,
                        customServer = customServer,
                        inParty = state.inParty,
                    )
                }
                .onFailure { problem -> failure = problem.message }
        }
    }

    /**
     * Routes an invite target to either the server-consent sheet or the
     * preview, following the same server-consent rule Classic used: a party
     * on a different server than this device talks to needs explicit consent
     * before anything is fetched from it. The official-server switch (an
     * invite with no server while a custom server is configured) only applies
     * to deeplinks — a scanned or typed code stays on the current server.
     */
    val routeInvite: (String, String?, Boolean) -> Unit = { code, server, offerOfficialSwitch ->
        val activeServer = (ListenTogether.activePartyServerBase()
            ?: ListenTogether.effectiveIdleServerBase()).trim().trimEnd('/')
        val targetServer = server?.trim()?.trimEnd('/')
        if (!targetServer.isNullOrBlank() && !targetServer.equals(activeServer, ignoreCase = true)) {
            menuState.showServerSwitchSheet(code, targetServer) { toJoin, toSet ->
                openPartyPreview(toJoin, toSet)
            }
        } else if (offerOfficialSwitch && targetServer.isNullOrBlank() && customServer.isNotBlank()) {
            menuState.showServerSwitchSheet(code, "") { toJoin, toSet ->
                openPartyPreview(toJoin, toSet)
            }
        } else {
            // No already-in-this-party check here on purpose: a deeplink to
            // the party this device is already in is short-circuited by the
            // LaunchedEffect that routes it, while a scanned code still opens
            // the preview — the same asymmetry Classic kept.
            openPartyPreview(code, targetServer)
        }
    }

    /**
     * Hands the create sheet over to the invite sheet, with the party page
     * visible in between — the same beat Classic kept: the create sheet is
     * dismissed, a second passes so the new party page is actually seen, and
     * only then the invite rises. Skipped if the user has already opened
     * something else in that second.
     */
    val showInviteAfterCreate: () -> Unit = {
        scope.launch {
            menuState.dismiss()
            delay(INVITE_SHEET_DELAY_MS)
            val code = ListenTogether.state.value.code
            if (code != null && !menuState.isVisible) {
                menuState.showInviteSheet(code = code, link = inviteLinkFor(code))
            }
        }
    }

    /**
     * The third door in, beside a tapped link and six typed characters: the
     * host's QR code, read by this phone's camera. Whatever it decodes to —
     * an invite link, a custom server's invite page, or a bare code — lands
     * in the same preview a typed code reaches, under the same server
     * consent rule a tapped link follows.
     */
    val launchQrScanner = rememberQrCodeScanner { raw ->
        if (raw == null) return@rememberQrCodeScanner
        val parsed = JamInviteLink.parseInvite(raw)
        val scannedCode: String?
        val scannedServer: String?
        if (parsed != null) {
            scannedCode = parsed.code
            scannedServer = parsed.serverUrl
        } else {
            // The strict parser only knows the official host's links. A
            // custom server's invite page is <server>/invite/<CODE> — read
            // that shape directly and keep the server it names.
            val match = Regex("""/invite/([A-Za-z0-9]{${ListenTogether.CODE_LENGTH}})""").find(raw)
            if (match != null) {
                scannedCode = match.groupValues[1].uppercase()
                scannedServer = raw.substring(0, match.range.first).trimEnd('/')
            } else {
                val cleaned = raw.filter(Char::isLetterOrDigit).uppercase()
                if (cleaned.length == ListenTogether.CODE_LENGTH) {
                    scannedCode = cleaned
                    scannedServer = null
                } else {
                    scannedCode = null
                    scannedServer = null
                }
            }
        }
        if (scannedCode == null) {
            failure = context.getString(R.string.listen_together_scan_invalid)
            return@rememberQrCodeScanner
        }
        routeInvite(scannedCode, scannedServer, false)
    }

    // A membership outlives the process; the socket does not. Opening it when
    // the screen is looked at — rather than on every cold start — is what keeps
    // a feature nobody is currently using off the radio.
    LaunchedEffect(Unit) { ListenTogether.ensureConnected() }
    DisposableEffect(Unit) {
        ListenTogether.setScreenActive(true)
        onDispose { ListenTogether.setScreenActive(false) }
    }

    // A link tap is already an explicit request to join. Signed-out users keep
    // the pending invite while the sign-in page is open; the effect re-runs
    // once they are back, signed in.
    LaunchedEffect(pendingInvite, signedIn) {
        val invite = pendingInvite ?: return@LaunchedEffect
        if (!signedIn) return@LaunchedEffect
        if (state.code.equals(invite.code, ignoreCase = true)) {
            JamInviteLink.handled()
            return@LaunchedEffect
        }
        // A link says which party, not that this device has agreed to join it.
        // The confirm sheet is what actually joins — the same one a typed
        // code goes through, whether or not this device is already in a party.
        routeInvite(invite.code, invite.serverUrl, true)
        JamInviteLink.handled()
    }

    SubSettingsScaffold(
        title = stringResource(R.string.listen_together),
        onBack = { navigator.popBackStack() },
    ) {
        if (!signedIn) {
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        icon = painterResource(R.drawable.login),
                        title = { Text(stringResource(R.string.sign_in)) },
                        description = { Text(stringResource(R.string.not_signed_in)) },
                        onClick = { navigator.navigate(SignInRoute) },
                    ),
                ),
                style = SettingsGroupStyle.Grouped,
            )
            GroupFooter(stringResource(R.string.listen_together_sign_in_footer))
            Spacer(Modifier.height(12.dp))
        }

        if (!state.inParty) {
            PartyLanding(
                avatarUrl = ListenTogether.myAvatarUrl(),
                enabled = signedIn && ListenTogether.hasServer,
                onCreate = {
                    failure = null
                    menuState.showCreatePartySheet(onCreated = showInviteAfterCreate)
                },
                onJoin = {
                    failure = null
                    menuState.showJoinPartySheet(
                        onScanQr = { launchQrScanner() },
                        onPreview = { preview, server ->
                            menuState.showJoinConfirmSheet(
                                preview = preview,
                                server = server,
                                customServer = customServer,
                                inParty = state.inParty,
                            )
                        },
                    )
                },
            )
        } else {
            InAParty(
                state = state,
                onCopy = {
                    clipboard.setText(AnnotatedString(state.code.orEmpty()))
                    Toast.makeText(context, context.getString(R.string.copy_code), Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    val partyCode = state.code
                    if (partyCode != null) {
                        menuState.showInviteSheet(code = partyCode, link = inviteLinkFor(partyCode))
                    }
                },
                onLeave = { scope.launch { ListenTogether.leaveParty() } },
                onSetCapacity = ListenTogether::setMaxMembers,
                onSetHostOnlyControl = ListenTogether::setHostOnlyControl,
                onKick = ListenTogether::kick,
            )
            PartyQueueSection(state = state)
            // Inside the party branch on purpose. A log of who skipped what is
            // a thing to look back over while a party is running; on the page
            // that offers to start one it is a list of somebody else's evening,
            // under a button that has not been pressed yet.
            PartyActivityList(activity)
        }

        // Only when nothing is covering it. A sheet is a drawer over the bottom
        // of this page, and every failure it can produce is already shown
        // inside it — printing the same line down here as well means printing
        // it where it cannot be read.
        if (!menuState.isVisible) {
            val message = failure ?: state.error
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                )
            }
        }

        // Last, and empty by default. Nobody setting up a party needs to think
        // about an address — there is one built in — so this is where somebody
        // running their own server comes looking, rather than the first thing
        // everybody else has to read past.
        Spacer(Modifier.height(12.dp))
        SettingsGroup(
            title = stringResource(R.string.listen_together_server),
            items = listOf(
                SettingsItem(
                    icon = painterResource(R.drawable.cloud),
                    title = { Text(stringResource(R.string.listen_together_server)) },
                    description = { Text(serverStatusLine(serverStatus)) },
                    trailingContent = {
                        if (serverStatus.health == ListenTogether.Health.CHECKING) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.refresh),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = { ListenTogether.refreshServerHealth(showChecking = true) },
                ),
                SettingsItem(
                    icon = painterResource(R.drawable.web_link),
                    title = { Text(stringResource(R.string.listen_together_custom_server)) },
                    description = {
                        Column {
                            Text(
                                text = customServer.ifBlank {
                                    stringResource(R.string.listen_together_using_default)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (customServer.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                ServerConnectionLine(connectionState)
                            }
                        }
                    },
                    // Locked while in a party: changing the address under a live
                    // membership would leave this device holding a token for a
                    // server it no longer talks to, and the party unable to say
                    // why it went quiet.
                    enabled = !state.inParty,
                    onClick = { menuState.showPartyServerEditorSheet() },
                ),
            ),
            style = SettingsGroupStyle.Grouped,
        )
        GroupFooter(stringResource(R.string.listen_together_custom_server_footer))
        Spacer(Modifier.height(12.dp))
    }
}

/** One line saying whether the party server is answering, before anything else. */
@Composable
private fun serverStatusLine(status: ListenTogether.ServerStatus): String = when (status.health) {
    ListenTogether.Health.ONLINE ->
        if (status.isFallback) {
            stringResource(R.string.listen_together_server_online_fallback, status.latencyMs)
        } else {
            stringResource(R.string.listen_together_server_online, status.latencyMs)
        }
    ListenTogether.Health.OFFLINE -> stringResource(R.string.listen_together_server_offline)
    ListenTogether.Health.CHECKING -> stringResource(R.string.listen_together_server_checking)
    ListenTogether.Health.UNKNOWN -> stringResource(R.string.listen_together_server_unknown)
}

/**
 * Whether the address that was typed is actually answering.
 *
 * Under the address rather than in a toast, because the interesting case is the
 * one nobody is watching for: a server that answered when it was saved and has
 * since gone quiet.
 */
@Composable
private fun ServerConnectionLine(connection: ServerConnectionState) {
    val label = when (connection) {
        ServerConnectionState.Checking ->
            stringResource(R.string.listen_together_server_checking)
        is ServerConnectionState.CustomOnline ->
            stringResource(R.string.listen_together_status_custom_online, connection.latencyMs)
        is ServerConnectionState.CustomFallback ->
            stringResource(R.string.listen_together_status_custom_fallback, connection.latencyMs)
        ServerConnectionState.Offline ->
            stringResource(R.string.listen_together_status_all_offline)
        // Nothing to say: the row above already reads "using the built-in one".
        is ServerConnectionState.DefaultOnline -> return
    }
    val tint = when (connection) {
        is ServerConnectionState.CustomOnline -> MaterialTheme.colorScheme.primary
        ServerConnectionState.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (connection == ServerConnectionState.Checking) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = tint,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.cloud),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How long the newly made party sits on screen before the invite rises over it.
 *
 * Long enough to be a page that was arrived at rather than a frame that flashed
 * past, short enough that nobody has started reading the member list yet.
 */
private const val INVITE_SHEET_DELAY_MS = 1_000L

/**
 * What the screen is when there is no party yet.
 *
 * Two doors rather than one, and they are not equals: creating is the common
 * case and gets the filled pill; joining is what somebody does who already
 * has a code, and a text button is enough for a person who arrived looking
 * for it.
 */
@Composable
private fun PartyLanding(
    avatarUrl: String?,
    enabled: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        GlowingAvatar(url = avatarUrl)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.listen_together_landing_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.listen_together_landing_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        MochyPillButton(
            text = stringResource(R.string.listen_together_create_action),
            onClick = onCreate,
            enabled = enabled,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onJoin,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                text = stringResource(R.string.listen_together_join_existing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The page's one main action: a filled pill in the theme's primary. */
@Composable
internal fun MochyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InAParty(
    state: ListenTogether.State,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onLeave: () -> Unit,
    onSetCapacity: (Int) -> Unit,
    onSetHostOnlyControl: (Boolean) -> Unit,
    onKick: (String) -> Unit,
) {
    SectionTitle(stringResource(R.string.listen_together_code))
    MochyCard {
        Text(
            text = state.code.orEmpty(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            // Wide tracking because this is a string to be read out loud and
            // typed by somebody else, not a word to be scanned.
            letterSpacing = 8.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 14.dp),
        )
        CardDivider()
        CardActionRow(
            icon = painterResource(R.drawable.content_copy),
            title = stringResource(R.string.copy_code),
            onClick = onCopy,
        )
        CardDivider()
        CardActionRow(
            icon = painterResource(R.drawable.share),
            title = stringResource(R.string.share),
            onClick = onShare,
        )
    }
    GroupFooter(stringResource(R.string.listen_together_code_footer))
    Spacer(Modifier.height(12.dp))

    NowPlayingInTheParty(state)

    SectionTitle(
        stringResource(
            R.string.listen_together_listening,
            state.members.size,
            state.maxMembers,
        ),
    )
    MochyCard {
        if (state.you?.isHost == true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.listen_together_party_size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.listen_together_host_controls),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { onSetCapacity(state.maxMembers - 1) },
                    enabled = state.maxMembers > maxOf(2, state.members.size),
                ) { Text("−", color = MaterialTheme.colorScheme.onSurface) }
                Text(
                    "${state.maxMembers}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = { onSetCapacity(state.maxMembers + 1) },
                    enabled = state.maxMembers < 10,
                ) { Text("+", color = MaterialTheme.colorScheme.onSurface) }
            }
            CardDivider()
            CardActionRow(
                icon = painterResource(R.drawable.lock),
                title = stringResource(R.string.listen_together_host_only),
                subtitle = stringResource(R.string.listen_together_host_only_subtitle),
                trailing = {
                    AppSwitch(
                        checked = state.hostOnlyControl,
                        onCheckedChange = onSetHostOnlyControl,
                    )
                },
                onClick = { onSetHostOnlyControl(!state.hostOnlyControl) },
            )
            CardDivider()
        }
        state.members.forEachIndexed { index, member ->
            if (index > 0) CardDivider()
            val isYou = member.memberId == state.you?.memberId
            MemberRow(
                member = member,
                isYou = isYou,
                canKick = state.you?.isHost == true && !member.isHost,
                onKick = { onKick(member.memberId) },
            )
        }
    }
    GroupFooter(stringResource(R.string.listen_together_members_footer, state.maxMembers))
    Spacer(Modifier.height(12.dp))

    SettingsGroup(
        items = listOf(
            SettingsItem(
                icon = painterResource(R.drawable.logout),
                title = { Text(stringResource(R.string.listen_together_leave)) },
                description = { Text(stringResource(R.string.listen_together_leave_subtitle)) },
                onClick = onLeave,
            ),
        ),
        style = SettingsGroupStyle.Grouped,
    )
    Spacer(Modifier.height(12.dp))
}

/**
 * What the party is playing, and where its playhead is right now.
 *
 * The position is recomputed on a tick rather than read out of the last frame,
 * because that *is* the feature: between server updates each device advances
 * the same anchored position on its own clock, and two phones side by side
 * should show the same number. A value that only moved when a frame arrived
 * would prove nothing.
 */
@Composable
private fun NowPlayingInTheParty(state: ListenTogether.State) {
    val track = state.playback.track
    var positionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.playback.seq, state.playback.isPlaying) {
        while (true) {
            positionMs = ListenTogether.partyPositionMs() ?: 0L
            delay(250)
        }
    }

    val connection = connectionLine(state)
    val nothingPlaying = stringResource(R.string.listen_together_nothing_playing)

    SectionTitle(stringResource(R.string.listen_together_now_playing))
    MochyCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(painterResource(R.drawable.music_note))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track?.title?.takeIf { it.isNotBlank() } ?: nothingPlaying,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (track == null) {
                        connection
                    } else {
                        buildString {
                            if (track.artist.isNotBlank()) {
                                append(track.artist)
                                append(" · ")
                            }
                            append(formatElapsed(positionMs))
                            track.durationMs?.let {
                                append(" / ")
                                append(formatElapsed(it))
                            }
                            append("\n")
                            append(connection)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** One line saying whether this device is actually keeping up, and how well. */
@Composable
private fun connectionLine(state: ListenTogether.State): String = when {
    state.connection != ListenTogether.Connection.LIVE ->
        stringResource(R.string.listen_together_reconnecting)
    // Live, but the clock has not been measured yet — so the position above is
    // the server's last word on it rather than this device's own reckoning, and
    // saying so is more use than a spinner.
    !state.clockSynced -> stringResource(R.string.listen_together_syncing_clock)
    else -> stringResource(R.string.listen_together_in_sync, state.roundTripMs)
}

@Composable
private fun MemberRow(member: PartyMember, isYou: Boolean, canKick: Boolean, onKick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (member.avatarUrl != null) {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = member.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isYou) {
                    Spacer(Modifier.width(8.dp))
                    MiniBadge(stringResource(R.string.listen_together_you))
                }
            }
            // "Away" rather than "offline": the slot is still theirs, and the
            // server holds it through a grace period precisely so a tunnel or
            // a locked screen does not read to everyone else as leaving.
            if (!member.connected) {
                Text(
                    text = stringResource(R.string.listen_together_away),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (member.isHost) {
            Spacer(Modifier.width(8.dp))
            MiniBadge(stringResource(R.string.listen_together_host))
        } else if (canKick) {
            TextButton(onClick = onKick) { Text(stringResource(R.string.listen_together_remove)) }
        }
    }
}

@Composable
private fun PartyActivityList(entries: List<PartyActivity>) {
    if (entries.isEmpty()) return
    SectionTitle(stringResource(R.string.listen_together_activity))
    MochyCard {
        val logScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .verticalScroll(logScroll)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            entries.forEach { entry ->
                val timestamp = DateFormat.format("HH:mm:ss", entry.atMs)
                Text(
                    text = "[$timestamp] ${entry.by}: ${entry.detail.ifBlank { entry.action }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    GroupFooter(stringResource(R.string.listen_together_activity_footer))
    Spacer(Modifier.height(12.dp))
}

/** A small pill badge (You / Host) beside a member's name. */
@Composable
private fun MiniBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** A card in the mochy Grouped style: 28dp corners on the settings field grey. */
@Composable
internal fun MochyCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SearchColors.Field),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
    }
}

/** The label above a group, matching SettingsGroup's own title. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
    )
}

/** The small explanatory line under a group. */
@Composable
internal fun GroupFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

/** The hairline between rows inside a card. */
@Composable
internal fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(0.8.dp)
            .background(Color.White.copy(alpha = 0.1f)),
    )
}

/**
 * The icon disc a settings row carries: a light circle with a dark glyph, the
 * same 32dp disc SettingsItemRow draws.
 */
@Composable
internal fun RowIcon(painter: Painter, size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE5E5EA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = Color(0xFF1C1C1E),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A tappable row inside a custom card: icon disc, title, optional second line, trailing. */
@Composable
internal fun CardActionRow(
    icon: Painter,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

internal fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

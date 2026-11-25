// The Listen Together sheets, as PopupSheetState extensions — the Modern
// equivalent of the Classic ListenTogetherSheets.kt (ModalBottomSheet) and of
// the server-switch AlertDialog in the Classic screen.
//
// Every sheet owns its own working state (busy / error / draft text) and
// calls the ListenTogether engine directly; the screen only decides which
// sheet opens next. That keeps the sheet content live while it is up —
// values passed into show() would be frozen at open time — and matches the
// AppLanguageSheet / ScrobbleSignInSheet pattern already in the tree.
//
// Sheets here: create, join-by-code, join-confirm, invite, server-switch.
// The custom-server editor is its own file (PartyServerSheet.kt).

package com.music.bitchord.ui.mochy.screens.listen

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyPreview
import com.music.bitchord.ui.components.QrCode
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.SheetLabel
import com.music.bitchord.ui.mochy.component.SheetOptionRow
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetTextField
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.player.menu.MenuSurface
import com.music.bitchord.ui.mochy.utils.stableSystemBars
import kotlinx.coroutines.launch

// --------------------------------------------------------------- create --

/** Name and size, asked once, before a party exists. */
fun PopupSheetState.showCreatePartySheet(onCreated: () -> Unit) {
    showWithHandle {
        CreatePartySheetContent(onCreated = onCreated)
    }
}

@Composable
private fun CreatePartySheetContent(onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(ListenTogether.nickname()) }
    var maxMembers by remember { mutableIntStateOf(5) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val avatarUrl = ListenTogether.myAvatarUrl()
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.listen_together_create))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.listen_together_create_sheet_subtitle),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PartySheetAvatar(url = avatarUrl)
            Spacer(Modifier.width(12.dp))
            SheetTextField(
                value = nickname,
                onValueChange = {
                    nickname = it.take(80)
                    ListenTogether.setNickname(nickname)
                },
                placeholder = stringResource(R.string.listen_together_nickname_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.listen_together_party_size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.listen_together_party_size_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            StepperButton("−", enabled = !busy && maxMembers > 2) {
                maxMembers -= 1
            }
            Text(
                "$maxMembers",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center,
            )
            StepperButton("+", enabled = !busy && maxMembers < 10) {
                maxMembers += 1
            }
        }
        Spacer(Modifier.height(24.dp))
        SheetPillButton(
            text = stringResource(
                if (busy) R.string.listen_together_creating else R.string.listen_together_create_action,
            ),
            enabled = !busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    // The sheet is NOT dismissed here: the screen's
                    // showInviteAfterCreate dismisses it, waits a beat so the
                    // new party page is seen, and only then raises the invite
                    // sheet — the same handover Classic kept.
                    val problem = ListenTogether.createParty(nickname, maxMembers)
                        .exceptionOrNull()?.message
                    error = problem
                    busy = false
                    if (problem == null) onCreated()
                }
            },
        )
        SheetError(error)
        Spacer(Modifier.height(12.dp + navBottom))
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

// ----------------------------------------------------------------- join --

/** Six characters, and the button that looks them up. */
fun PopupSheetState.showJoinPartySheet(
    onScanQr: (() -> Unit)?,
    onPreview: (PartyPreview, String?) -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        JoinPartySheetContent(
            onScanQr = onScanQr,
            onPreview = { preview, server ->
                sheet?.dismiss()
                onPreview(preview, server)
            },
        )
    }
}

@Composable
private fun JoinPartySheetContent(
    onScanQr: (() -> Unit)?,
    onPreview: (PartyPreview, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    val submit: () -> Unit = {
        // a bare `return` is prohibited in a lambda held in a val, so
        // the guard wraps the body instead of exiting early
        if (code.length == ListenTogether.CODE_LENGTH && !busy) {
            busy = true
            error = null
            scope.launch {
                ListenTogether.previewParty(code, null)
                    .onSuccess { preview -> onPreview(preview, null) }
                    .onFailure { problem -> error = problem.message }
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.listen_together_join))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.listen_together_code_hint),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        PartyCodeField(
            code = code,
            onCodeChange = { typed ->
                code = typed.filter(Char::isLetterOrDigit)
                    .uppercase()
                    .take(ListenTogether.CODE_LENGTH)
                // Typing is the retry. Keeping "no party with that code" on
                // screen while the code is being corrected makes it read as a
                // verdict on what is there now.
                error = null
            },
            enabled = !busy,
            onSubmit = submit,
        )
        Spacer(Modifier.height(20.dp))
        SheetPillButton(
            text = stringResource(
                if (busy) R.string.listen_together_joining else R.string.listen_together_join_action,
            ),
            enabled = !busy && code.length == ListenTogether.CODE_LENGTH,
            onClick = submit,
        )
        if (onScanQr != null) {
            Spacer(Modifier.height(12.dp))
            SheetOptionRow(
                title = stringResource(R.string.listen_together_scan_qr),
                onClick = onScanQr,
                enabled = !busy,
                leading = {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
        SheetError(error)
        Spacer(Modifier.height(12.dp + navBottom))
    }
}

// -------------------------------------------------------- join confirm --

/**
 * The last step before a device slot is committed: who is in there, and a way
 * out that is not the back button.
 *
 * [customServer] and [inParty] are captured at open time: a server named on
 * the invite itself always wins, otherwise already being in a party means a
 * switch, which keeps this device where it is if the new party turns it away.
 */
fun PopupSheetState.showJoinConfirmSheet(
    preview: PartyPreview,
    server: String?,
    customServer: String,
    inParty: Boolean,
    onJoined: () -> Unit = {},
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        JoinConfirmSheetContent(
            preview = preview,
            server = server,
            customServer = customServer,
            inParty = inParty,
            onDismiss = { sheet?.dismiss() },
            onJoined = {
                sheet?.dismiss()
                onJoined()
            },
        )
    }
}

@Composable
private fun JoinConfirmSheetContent(
    preview: PartyPreview,
    server: String?,
    customServer: String,
    inParty: Boolean,
    onDismiss: () -> Unit,
    onJoined: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(ListenTogether.nickname()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val avatarUrl = ListenTogether.myAvatarUrl()
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MemberAvatarStack(
            members = preview.members.map { it.displayName to it.avatarUrl },
            total = preview.memberCount,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = preview.hostName.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.listen_together_join_question, it) }
                ?: stringResource(R.string.listen_together_join_question_unnamed),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Only when the server actually told us. A party looked up on a server
        // with no preview endpoint comes back as a code and nothing else, and
        // "0 of 5 listening" would be this screen inventing a fact about a
        // party it cannot see into. See ListenTogether.previewParty.
        if (preview.memberCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.listen_together_listening,
                        preview.memberCount,
                        preview.maxMembers,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PartySheetAvatar(url = avatarUrl)
            Spacer(Modifier.width(12.dp))
            SheetTextField(
                value = nickname,
                onValueChange = {
                    nickname = it.take(80)
                    ListenTogether.setNickname(nickname)
                },
                placeholder = stringResource(R.string.listen_together_nickname_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        SheetPillButton(
            text = stringResource(
                when {
                    preview.isFull -> R.string.listen_together_full
                    busy -> R.string.listen_together_joining
                    else -> R.string.listen_together_join_action
                },
            ),
            enabled = !busy && !preview.isFull,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val problem = if (server != null || inParty) {
                        val target = ListenTogether.resolveSwitchTarget(server, customServer)
                        when (
                            val switched = ListenTogether.switchPartyWithRecovery(
                                target,
                                preview.code,
                                nickname,
                            )
                        ) {
                            is ListenTogether.SwitchPartyResult.Success -> null
                            is ListenTogether.SwitchPartyResult.TargetFailedRecovered ->
                                switched.targetError
                            is ListenTogether.SwitchPartyResult.TargetFailedNoParty ->
                                switched.targetError
                        }
                    } else {
                        ListenTogether.joinParty(preview.code, nickname)
                            .exceptionOrNull()?.message
                    }
                    error = problem
                    busy = false
                    if (problem == null) onJoined()
                }
            },
        )
        SheetError(error)
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                text = stringResource(R.string.listen_together_not_now),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.listen_together_preview_footer),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp + navBottom))
    }
}

// --------------------------------------------------------------- invite --

/** How somebody else gets in: a link to send, or a square to point a camera at. */
fun PopupSheetState.showInviteSheet(code: String, link: String) {
    showWithHandle {
        InviteSheetContent(code = code, link = link)
    }
}

@Composable
private fun InviteSheetContent(code: String, link: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetLabel(stringResource(R.string.listen_together_invite_title))
        Spacer(Modifier.height(20.dp))
        SheetPillButton(
            text = stringResource(R.string.listen_together_share_link),
            onClick = {
                val message = link +
                    System.lineSeparator() + System.lineSeparator() +
                    context.getString(R.string.listen_together_share_text, code)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        },
                        null,
                    ),
                )
            },
        )
        Spacer(Modifier.height(24.dp))
        // The square big and in the middle (batch 30): this sheet is pulled
        // up to be scanned, so the code is the biggest thing on it, with
        // the words and the invite code centred under it.
        QrCode(content = link, size = 240.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.listen_together_scan_qr),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.listen_together_scan_qr_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        // The code under the square, because the square is useless to
        // somebody on the phone the party was made on — reading six
        // characters out is still the fastest way into a party in the
        // same room.
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(code))
                TopToaster.show(context.getString(R.string.copy_code))
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp + navBottom))
    }
}

// -------------------------------------------------------- server switch --

/**
 * Consent to talk to a different server at all — the Modern form of the
 * Classic AlertDialog. [server] is the address to switch to, or "" for the
 * official server. Consent only opens the preview; the join that follows is
 * what actually commits the server change (see switchPartyWithRecovery).
 */
fun PopupSheetState.showServerSwitchSheet(
    code: String,
    server: String,
    onConsent: (String, String?) -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        ServerSwitchSheetContent(
            code = code,
            server = server,
            onConsent = { toJoin, toSet ->
                sheet?.dismiss()
                onConsent(toJoin, toSet)
            },
            onDismiss = { sheet?.dismiss() },
        )
    }
}

@Composable
private fun ServerSwitchSheetContent(
    code: String,
    server: String,
    onConsent: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val isSwitchToOfficial = server.isBlank()
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxWidth()) {
        SheetLabel(
            stringResource(
                if (isSwitchToOfficial) {
                    R.string.listen_together_official_server_dialog_title
                } else {
                    R.string.listen_together_custom_server_dialog_title
                },
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (isSwitchToOfficial) {
                stringResource(R.string.listen_together_official_server_dialog_message, code)
            } else {
                stringResource(
                    R.string.listen_together_custom_server_dialog_message,
                    server,
                    code,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(24.dp))
        SheetPillButton(
            text = stringResource(R.string.listen_together_custom_server_switch_and_join),
            onClick = { onConsent(code, server.ifBlank { null }) },
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(12.dp + navBottom))
    }
}

// ------------------------------------------------------------ shared bits --

/** Why the last attempt from this sheet did not work, said inside the sheet. */
@Composable
private fun SheetError(message: String?) {
    if (message.isNullOrBlank()) return
    Spacer(Modifier.height(14.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The six cells of [PartyCodeField], and the Join button under them. */
internal val CodeCellShape = RoundedCornerShape(12.dp)

/**
 * The party code, entered as six cells rather than one box.
 *
 * A code that gets read out loud and typed in by somebody else is a sequence
 * of characters, not a word. Six cells say so without a hint line: they show
 * how many are wanted, which one is being typed, and how far in the reading
 * has got.
 *
 * One [BasicTextField] underneath, not six. Six fields means six focus
 * targets to hand along on every keystroke and back again on every backspace,
 * and a pasted code that lands entirely in the first one. So the real field
 * is invisible, laid over the cells at [Modifier.matchParentSize], and the
 * cells are only ever a picture of what it holds.
 */
@Composable
internal fun PartyCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Rebuilt on every change so the selection is pinned back to the end. The
    // field spans the whole row and shows nothing, so without this a tap
    // anywhere along it would drop the caret into the middle of the code and
    // the next character would appear in a cell that isn't the lit one.
    val field = remember(code) { TextFieldValue(code, TextRange(code.length)) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(ListenTogether.CODE_LENGTH) { index ->
                CodeCell(
                    char = code.getOrNull(index),
                    // Only ever one cell, and only while the keyboard is up: a
                    // ring left lit on a field nobody is typing into reads as
                    // something being wrong with it.
                    active = focused && index == code.length.coerceAtMost(ListenTogether.CODE_LENGTH - 1),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BasicTextField(
            value = field,
            onValueChange = { onCodeChange(it.text) },
            enabled = enabled,
            singleLine = true,
            // Both invisible: the cells are where the typing shows up, and a
            // second caret drifting along behind them would be the giveaway
            // that this is one field wearing a costume.
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.length == ListenTogether.CODE_LENGTH) onSubmit() },
            ),
            modifier = Modifier
                .matchParentSize()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

/** One character's worth of [PartyCodeField]. */
@Composable
private fun CodeCell(
    char: Char?,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Animated because the ring moves cell to cell as the code is typed, and a
    // border that simply appears one box to the right on each keystroke reads
    // as flicker rather than as travel. Every cell keeps a ring; only its
    // colour changes.
    val ring by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.12f)
            active -> Color.White
            else -> Color.White.copy(alpha = 0.18f)
        },
        label = "party code cell ring",
    )
    Box(
        modifier = modifier
            .height(52.dp)
            .background(MenuRowSurface, CodeCellShape)
            .border(1.5.dp, ring, CodeCellShape),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            )
        } else if (active) {
            // A caret, and only in the empty cell being typed into — once there
            // is a character to show, the character already answers "where am I".
            val blink = rememberInfiniteTransition(label = "party code caret")
            val alpha by blink.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "party code caret alpha",
            )
            Box(
                Modifier
                    .size(width = 2.dp, height = 22.dp)
                    .background(
                        Color.White.copy(alpha = alpha),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

/**
 * This device's own face, lit from behind.
 *
 * The glow is the whole of the decoration on the landing screen, and it is one
 * blurred circle rather than a pattern: the listener's picture is the subject,
 * and a field of shapes around it — the obvious thing to reach for — competes
 * with the one element that is actually about them.
 *
 * It breathes on a long, offset pair of animations so the rise and the fade do
 * not turn over together, which is what a single looping scale reads as: a
 * pulse rather than something lit.
 */
@Composable
internal fun GlowingAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    glow: Color = MaterialTheme.colorScheme.primary,
) {
    val breathe = rememberInfiniteTransition(label = "partyGlow")
    val spread by breathe.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "partyGlowSpread",
    )
    val intensity by breathe.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            // Deliberately not the spread's period: equal periods make the
            // brightest frame always the widest one, which reads as one object
            // throbbing instead of light moving.
            animation = tween(3_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "partyGlowIntensity",
    )

    Box(
        modifier = modifier.size(size * 2.1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 1.75f)
                .scale(spread)
                // Blurred as well as faded to transparent: a radial gradient
                // alone bands visibly on the dark background at this size.
                //
                // Unbounded, which is the whole point: the default edge
                // treatment clips the blur to the layout rectangle, so a glow
                // that grows past its own box gets a straight edge sliced
                // across it — a round light inside a visible square, breathing
                // in and out of its own corners.
                .blur(28.dp, BlurredEdgeTreatment.Unbounded)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = intensity),
                            glow.copy(alpha = intensity * 0.35f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MenuRowSurface)
                    .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        }
    }
}

/** The avatar as the sheets draw it — larger than a settings row's glyph. */
@Composable
private fun PartySheetAvatar(url: String?, size: Dp = 40.dp) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MenuRowSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/**
 * The faces already in a party, overlapped, with a count for the rest.
 *
 * Three and a number rather than all of them: the row has to stay the same
 * width at two members and at ten, and past three faces nobody is identifying
 * anyone — they are reading "a few people", which the number says better.
 */
@Composable
internal fun MemberAvatarStack(
    members: List<Pair<String, String?>>,
    total: Int,
    modifier: Modifier = Modifier,
    faceSize: Dp = 52.dp,
    shown: Int = 3,
) {
    val faces = members.take(shown)
    val extra = (total - faces.size).coerceAtLeast(0)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        faces.forEach { (name, avatar) ->
            Box(
                modifier = Modifier
                    .size(faceSize)
                    .clip(CircleShape)
                    .background(MenuRowSurface)
                    .border(2.dp, MenuSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(faceSize).clip(CircleShape),
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .size(faceSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MenuSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extra",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// The Sources screen's sheets: the addon editor (the classic
// SourceEditorAlert + AddonEditorAlert, restyled as a Modern PopupSheet),
// the per-addon quality picker, the outdated-addon explanation, and the
// WebDAV/SMB server editors (the classic ServerEditorHost calls from
// MainActivity, restyled the same way).
//
// All four keep the classic behaviour exactly: the editor still identifies
// the URL before anything is stored, still refuses a duplicate, still saves
// a server that is asleep once its format is known, and the server editors
// still save without testing first — Test is offered, never required.

package com.music.bitchord.ui.mochy.screens.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.smb.SmbRepository
import com.music.bitchord.data.sources.addon.AddonQualityOption
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.webdav.WebDavRepository
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.SheetIcon
import com.music.bitchord.ui.mochy.component.SheetLabel
import com.music.bitchord.ui.mochy.component.SheetOptionRow
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetTextField
import com.music.bitchord.ui.mochy.utils.stableSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Addon editor ────────────────────────────────────────────────────────

private class SourceEditorForm(config: SourceConfig) {
    val isNew = SourceRegistry.config(config.id) == null
    var baseUrl by mutableStateOf(config.baseUrl)
    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var statusIsGood by mutableStateOf(false)
}

/**
 * Add or edit a source that has an address.
 *
 * Test is offered rather than required: a server that happens to be asleep is
 * still worth saving, and refusing to store it until it answers would make
 * setting one up from a coffee shop impossible. What Test does buy is the
 * difference between "not answering" and "answering, but not with something
 * this app can use" — a manifest missing the `stream` resource is a mistake
 * worth hearing about before the first track rather than after it.
 */
fun PopupSheetState.showSourceEditorSheet(config: SourceConfig) {
    val form = SourceEditorForm(config)
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        SourceEditorContent(
            form = form,
            config = config,
            onLock = { locked -> sheet?.setLocked(locked) },
            onSaved = { sheet?.dismiss() },
            onDeleted = { sheet?.dismiss() },
        )
    }
}

@Composable
private fun SourceEditorContent(
    form: SourceEditorForm,
    config: SourceConfig,
    onLock: (Boolean) -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    val connected = stringResource(R.string.connected)
    val unreadable = stringResource(R.string.source_unrecognised)
    val alreadyAdded = stringResource(R.string.source_already_added)

    /**
     * Identify what is at the URL, then say so.
     *
     * Both buttons run this — the only difference is whether a success is then
     * stored. Testing and saving asking the *same* question is the point: a
     * Save that skipped identification could store a config whose kind was
     * guessed, and the guess is exactly what this screen no longer makes.
     */
    fun run(thenSave: Boolean) {
        form.busy = true
        form.status = null
        onLock(true)
        scope.launch {
            val identified = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.identify(form.baseUrl.trim(), config.takeUnless { form.isNew }) }
                    .getOrElse { Result.failure(it) }
            }
            val found = identified.getOrNull()
            if (found == null) {
                form.statusIsGood = false
                form.status = identified.exceptionOrNull()?.message ?: unreadable
                form.busy = false
                onLock(false)
                return@launch
            }

            // Already here? Checked against the *identified* base rather than
            // the typed text, so an addon's root and its manifest.json are
            // recognised as the one source they are — which is the whole reason
            // this runs after identification and not before it. Reported on
            // Test as well as Save: finding out by pressing Test beats finding
            // out by ending up with the same catalogue searched twice on every
            // track.
            SourceRegistry.duplicateOf(found.baseUrl, exceptId = config.id.takeUnless { form.isNew })
                ?.let { existing ->
                    form.statusIsGood = false
                    form.status = String.format(alreadyAdded, existing.displayName)
                    form.busy = false
                    onLock(false)
                    return@launch
                }

            // Identified, and now asked whether it actually works. The two are
            // different questions: a manifest can be perfectly well formed and
            // its server still be refusing every search.
            val health = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.probeCandidate(found) }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            form.statusIsGood = health.isOk
            form.status = when (health) {
                is SourceHealth.Ok -> listOfNotNull(
                    connected,
                    found.kind.label,
                    health.detail,
                ).joinToString(" · ")
                is SourceHealth.Rejected -> health.reason
                is SourceHealth.Unreachable -> health.reason
            }
            form.busy = false
            onLock(false)

            // Saved even when the probe came back unhappy, but only once the
            // format is known: a server that is asleep is still worth storing —
            // that was true before and is why Test was never mandatory — while
            // a URL nothing can be made of is not.
            if (thenSave) {
                if (form.isNew) SourceRegistry.add(found) else SourceRegistry.update(found)
                onSaved()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 12.dp + navBottom),
    ) {
        SheetLabel(
            text = if (form.isNew) {
                stringResource(R.string.add_addon)
            } else {
                config.displayName
            },
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.addon_url_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
        )
        SheetTextField(
            value = form.baseUrl,
            // A result describes the address it was run against, so the moment that
            // address is edited it stops being true and is cleared. Left up, it
            // would report "Connected" over a URL nobody has tried.
            onValueChange = { form.baseUrl = it; form.status = null },
            placeholder = "https://my-addon.example.com",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (form.baseUrl.isNotBlank() && !form.busy) run(thenSave = true) },
            ),
        )
        val status = form.status
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (form.statusIsGood) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SheetPillButton(
                text = if (form.busy) {
                    stringResource(R.string.testing)
                } else {
                    stringResource(R.string.test)
                },
                enabled = form.baseUrl.isNotBlank() && !form.busy,
                onClick = { run(thenSave = false) },
                modifier = Modifier.weight(1f),
            )
            SheetPillButton(
                text = stringResource(R.string.save),
                enabled = form.baseUrl.isNotBlank() && !form.busy,
                onClick = { run(thenSave = true) },
                modifier = Modifier.weight(1f),
            )
        }
        // Offered only for a source already stored — there is nothing to
        // remove otherwise. Immediate, as the classic alert's was: the
        // swipe-to-delete path is the one that asks first.
        if (!form.isNew) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.remove_source),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !form.busy) {
                        SourceRegistry.remove(config.id)
                        onDeleted()
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

// ── Per-addon quality picker ────────────────────────────────────────────

/** Picks which of an addon's manifest quality choices is in force. */
fun PopupSheetState.showSourceQualitySheet(config: SourceConfig) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        SourceQualityPicker(config) { option ->
            // Off the stored config, not the one this list opened with:
            // a probe may have refreshed it since.
            SourceRegistry.config(config.id)?.let {
                SourceRegistry.update(it.copy(pickedQuality = option.value))
            }
            sheet?.dismiss()
        }
    }
}

@Composable
private fun SourceQualityPicker(
    config: SourceConfig,
    onPick: (AddonQualityOption) -> Unit,
) {
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()
    val selected = config.activeQuality
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp + navBottom),
    ) {
        SheetLabel(
            text = stringResource(R.string.audio_quality),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        for (option in config.qualityOptions) {
            SheetOptionRow(
                title = option.label,
                onClick = { onPick(option) },
                leading = { SourceChoiceMark(option == selected) },
            )
        }
    }
}

// The radio mark a pick-one sheet's rows start with (Mochy's ChoiceMark,
// restated here so this batch does not depend on the settings package).
@Composable
private fun SourceChoiceMark(selected: Boolean) {
    SheetIcon(
        icon = if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked,
        tint = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
    )
}

// ── Outdated-addon explanation ──────────────────────────────────────────

/** Explains the "Outdated addon" line: the addon still works, but its developer should update it. */
fun PopupSheetState.showOutdatedAddonSheet(config: SourceConfig) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp + navBottom),
        ) {
            SheetLabel(
                text = config.displayName,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.source_needs_update),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            )
            SheetPillButton(
                text = stringResource(R.string.done),
                onClick = { sheet?.dismiss() },
            )
        }
    }
}

// ── WebDAV / SMB server editors ─────────────────────────────────────────

/** One field of a server editor: the saved value it opens with, the hint, and how the keyboard behaves. */
private data class ServerField(
    val initial: String,
    val placeholderRes: Int,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val isPassword: Boolean = false,
)

private class ServerEditorForm(fields: List<ServerField>) {
    val values = mutableStateListOf<String>().apply { addAll(fields.map { it.initial }) }
    var busy by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var statusIsGood by mutableStateOf(false)
}

/**
 * A server editor that owns its own state: field values, the last test
 * result and the testing flag — the classic ServerEditorHost, restyled as
 * a PopupSheet. The caller hands over fixed field configs plus what test
 * and save mean for those values.
 *
 * Save does not test first: a server that happens to be asleep is still
 * worth storing, and Test was never mandatory.
 */
private fun PopupSheetState.showServerEditorSheet(
    titleRes: Int,
    descriptionRes: Int,
    fields: List<ServerField>,
    canSubmit: (List<String>) -> Boolean,
    testFailedRes: Int,
    onTest: suspend (List<String>) -> Result<Unit>,
    onSave: (List<String>) -> Unit,
) {
    val form = ServerEditorForm(fields)
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()
        val values = form.values.toList()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 12.dp + navBottom),
        ) {
            SheetLabel(
                text = stringResource(titleRes),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            // The status replaces the description once a test has run, as
            // the classic card's did.
            val status = form.status
            Text(
                text = status ?: stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    status == null -> Color.White.copy(alpha = 0.6f)
                    form.statusIsGood -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )
            fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                SheetTextField(
                    value = form.values[index],
                    onValueChange = { form.values[index] = it; form.status = null },
                    placeholder = stringResource(field.placeholderRes),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = field.keyboardType,
                        imeAction = if (index == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSubmit(values) && !form.busy) {
                                onSave(values)
                                sheet?.dismiss()
                            }
                        },
                    ),
                    visualTransformation = if (field.isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SheetPillButton(
                    text = if (form.busy) {
                        stringResource(R.string.testing)
                    } else {
                        stringResource(R.string.test)
                    },
                    enabled = canSubmit(values) && !form.busy,
                    onClick = {
                        form.busy = true
                        form.status = null
                        sheet?.setLocked(true)
                        scope.launch {
                            onTest(values).fold(
                                onSuccess = {
                                    form.status = context.getString(R.string.connected)
                                    form.statusIsGood = true
                                },
                                onFailure = {
                                    form.status = context.getString(
                                        testFailedRes,
                                        it.message ?: context.getString(R.string.failed),
                                    )
                                    form.statusIsGood = false
                                },
                            )
                            form.busy = false
                            sheet?.setLocked(false)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                SheetPillButton(
                    text = stringResource(R.string.save),
                    enabled = canSubmit(values) && !form.busy,
                    onClick = {
                        onSave(values)
                        sheet?.dismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The WebDAV library's address, username and password. */
fun PopupSheetState.showWebDavEditorSheet() {
    showServerEditorSheet(
        titleRes = R.string.webdav,
        descriptionRes = R.string.webdav_description,
        fields = listOf(
            ServerField(
                initial = AppSettings.webdavUrl.value,
                placeholderRes = R.string.webdav_server_url_hint,
                keyboardType = KeyboardType.Uri,
            ),
            ServerField(
                initial = AppSettings.webdavUsername.value,
                placeholderRes = R.string.username,
            ),
            ServerField(
                initial = AppSettings.webdavPassword.value,
                placeholderRes = R.string.password,
                keyboardType = KeyboardType.Password,
                isPassword = true,
            ),
        ),
        canSubmit = { it[0].isNotBlank() },
        testFailedRes = R.string.webdav_test_failed,
        onTest = { (url, username, password) ->
            WebDavRepository.testConnection(url.trim(), username.trim(), password)
        },
        onSave = { (url, username, password) ->
            AppSettings.setWebDavUrl(url.trim())
            AppSettings.setWebDavUsername(username.trim())
            AppSettings.setWebDavPassword(password)
        },
    )
}

/** The SMB share's host, share, folder, username and password. */
fun PopupSheetState.showSmbEditorSheet() {
    showServerEditorSheet(
        titleRes = R.string.smb,
        descriptionRes = R.string.smb_description,
        fields = listOf(
            ServerField(
                initial = AppSettings.smbHost.value,
                placeholderRes = R.string.smb_server_hint,
                keyboardType = KeyboardType.Uri,
            ),
            ServerField(
                initial = AppSettings.smbShare.value,
                placeholderRes = R.string.smb_share_hint,
            ),
            ServerField(
                initial = AppSettings.smbBasePath.value,
                placeholderRes = R.string.smb_folder_hint,
            ),
            ServerField(
                initial = AppSettings.smbUsername.value,
                placeholderRes = R.string.username,
            ),
            ServerField(
                initial = AppSettings.smbPassword.value,
                placeholderRes = R.string.password,
                keyboardType = KeyboardType.Password,
                isPassword = true,
            ),
        ),
        canSubmit = { it[0].isNotBlank() && it[1].isNotBlank() },
        testFailedRes = R.string.smb_test_failed,
        onTest = { (host, share, folder, username, password) ->
            SmbRepository.testConnection(host.trim(), share.trim(), folder.trim(), username.trim(), password)
        },
        onSave = { (host, share, folder, username, password) ->
            AppSettings.setSmbHost(host.trim())
            AppSettings.setSmbShare(share.trim())
            AppSettings.setSmbBasePath(folder.trim())
            AppSettings.setSmbUsername(username.trim())
            AppSettings.setSmbPassword(password)
        },
    )
}

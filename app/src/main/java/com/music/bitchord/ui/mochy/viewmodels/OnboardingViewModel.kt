// Ported from Mochy Music (GPL-3.0): viewmodels/OnboardingViewModel.kt
// OnboardingViewModel.kt

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.ui.mochy.constants.ProfileCardNumberKey
import com.music.bitchord.ui.mochy.constants.ProfileJoinedEpochDayKey
import com.music.bitchord.ui.mochy.constants.SelectedYtmPlaylistsKey
import com.music.bitchord.ui.mochy.constants.YtPullHistoryKey
import com.music.bitchord.ui.mochy.constants.YtReportPlaysKey
import com.music.bitchord.ui.mochy.constants.YtSyncLikedSongsKey
import com.music.bitchord.ui.mochy.constants.YtSyncNewPlaylistsKey
import com.music.bitchord.ui.mochy.constants.YtmSyncKey
import com.music.bitchord.ui.mochy.importer.MusicImportService
import com.music.bitchord.ui.mochy.importer.ParsedImport
import com.music.bitchord.ui.mochy.importer.YouTubeSyncManager
import com.music.bitchord.ui.mochy.importer.account.AccountImportSession
import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.importer.parseImportCsv
import com.music.bitchord.ui.mochy.screens.setup.onboarding.BlurCapability
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.random.Random

/** What the "Import & sync" options page sets. */
data class YouTubeSyncChoices(
    val syncOnLaunch: Boolean = true,
    val newPlaylistsToYouTube: Boolean = true,
    val reportPlays: Boolean = true,
    val copyHistory: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importService: MusicImportService,
    private val youTubeSync: YouTubeSyncManager,
) : ViewModel() {

    var session by mutableStateOf<AccountImportSession?>(null)
        private set

    /** Set on the Hello page from its frame timings. */
    var blur by mutableStateOf<BlurCapability.Result?>(null)
        private set

    val cardNumber: String = (10_000_000_000L + Random.nextLong(89_999_999_999L)).toString()
    val joinedText: String = "joined at " + LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    /** Onboarding can be run again from Settings; start each run clean. */
    fun reset() {
        session?.cancel()
        session = null
        blur = null
    }

    // ---- providers ----

    fun startProvider(service: AccountService) {
        session?.cancel()
        session = AccountImportSession(service, context, viewModelScope).also { it.start() }
    }

    fun closeProvider() {
        session?.cancel()
        session = null
    }

    /** Whatever was read from the account goes to the background importer; onboarding moves on. */
    fun startImport(parsed: ParsedImport) {
        importService.startImport(parsed)
    }

    /** "Import & sync": remember what to sync and how, then run the first sync in the background. */
    fun startYouTubeSync(selectedIds: Set<String>, likedId: String, choices: YouTubeSyncChoices) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                // nothing picked means everything, which is also what sync did before picking existed
                prefs[SelectedYtmPlaylistsKey] = selectedIds.filterNot { it == likedId }.joinToString(",")
                prefs[YtSyncLikedSongsKey] = selectedIds.isEmpty() || likedId in selectedIds
                prefs[YtmSyncKey] = choices.syncOnLaunch
                prefs[YtSyncNewPlaylistsKey] = choices.newPlaylistsToYouTube
                prefs[YtReportPlaysKey] = choices.reportPlays
                prefs[YtPullHistoryKey] = choices.copyHistory
            }
            // its own scope: keeps going after onboarding closes
            youTubeSync.syncNow()
        }
    }

    /** A Mochy (or any playlist) CSV; null on success, otherwise what went wrong. */
    suspend fun importBackup(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return@withContext "Couldn't open that file."
            val parsed = parseImportCsv(text, fallbackPlaylistName = "Imported playlist")
            if (parsed.totalSongs == 0 && parsed.totalPlaylists == 0) return@withContext "No songs in that file."
            if (!importService.startImport(parsed)) return@withContext "Another import is still running."
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Backup import failed")
            "Couldn't read that file. Make sure it's a .csv backup."
        }
    }

    // ---- profile ----

    /** The signed-in account's name, to start the profile from. */
    fun accountName(): String? = session?.library?.accountName?.takeIf { it.isNotBlank() }

    /** Downloads the account's picture to a local file the profile can keep. */
    suspend fun accountPhoto(): Uri? = withContext(Dispatchers.IO) {
        val url = session?.library?.accountPhotoUrl ?: return@withContext null
        // YouTube serves tiny avatars unless asked for a size
        val sized = url.replace(Regex("=s\\d+(-[^&]*)?$"), "=s512")
        try {
            val connection = (URL(sized).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            connection.inputStream.use { input ->
                val file = File(context.cacheDir, "onboarding_account_photo.jpg")
                file.outputStream().use { input.copyTo(it) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Timber.w(e, "Account photo download failed")
            null
        }
    }

    fun saveCard() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[ProfileCardNumberKey] = cardNumber
                prefs[ProfileJoinedEpochDayKey] = LocalDate.now().toEpochDay()
            }
        }
    }

    // ---- blur ----

    fun onBlurProbe(probe: BlurCapability.Probe) {
        blur = BlurCapability.decide(context, probe)
    }

    override fun onCleared() {
        session?.cancel()
    }
}

// Ported from Mochy Music (GPL-3.0): lyrics/LyricsTranslationHelper.kt
// PARTIAL — the state machine and the pure helpers are verbatim; the translation ENGINES
// (Google Translate, OpenRouter/DeepL AI, YouTube caption translation) are NOT ported and
// their entry points are stubs (see NEEDS.md §(b)). The UI compiles and degrades gracefully:
// turning translation on shows the loading dots, then nothing arrives, and the status
// returns to Idle instead of hanging on Translating.
//
// Differences from Mochy, forced by the staged types:
// - No `database` parameter anywhere: ChordWave has no MusicDatabase equivalent, and the
//   stubs persist nothing. The coordinator owns the signature if a real engine lands.
// - loadTranslationsFromDatabase / hasTranslations / clearTranslations are stubs: the
//   staged LyricsEntity (phase3-player-menus) has no translatedLyrics / translationLanguage
//   / translationMode fields yet. Once the coordinator adds those three fields, the Mochy
//   bodies (lyrics/LyricsTranslationHelper.kt lines ~174-241) can be restored verbatim.

package com.music.bitchord.ui.mochy.lyrics

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val _translationSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

    private var translationJob: Job? = null
    private var isCompositionActive = true

    private val translationCache = mutableMapOf<String, List<String>>()

    private fun getCacheKey(lyricsText: String, mode: String, language: String): String =
        "${lyricsText.hashCode()}_${mode}_$language"

    fun getCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): List<String>? {
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val key = getCacheKey(lyricsText, mode, language)
        return translationCache[key]
    }

    fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        if (cached.size >= nonEmptyEntries.size) {
            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cached[idx]
            }
            return true
        }
        return false
    }

    /**
     * Whether translations should be on screen. This is the toggle the translate button flips.
     *
     * It used to be implicit: "on" meant "some line currently has a translation", and "off" only
     * blanked the lines. Nothing remembered the choice, so the stored translation reappeared on
     * the next database emission, a new song never translated, and changing the language had
     * nothing to re-run against.
     */
    private val _translationOn = MutableStateFlow(false)
    val translationOn: StateFlow<Boolean> = _translationOn.asStateFlow()

    private var lastSongId: String? = null

    /**
     * What a stored translation is filed under. Just the target when the source is detected
     * (as before, so existing stored translations still match), "source>target" when the user
     * picked the source, so picking one after a bad detection really translates again instead of
     * reloading the stored copy.
     */
    fun languageTag(sourceLanguage: String, targetLanguage: String): String =
        if (sourceLanguage.isBlank() || sourceLanguage == "auto") targetLanguage else "$sourceLanguage>$targetLanguage"

    fun triggerManualTranslation() {
        _translationOn.value = true
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _translationOn.value = false
        _hasActiveTranslations.value = false
        translationJob?.cancel()
        _status.value = TranslationStatus.Idle
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    /**
     * Called when the playing song changes. Translation carries over to the next song only when
     * "translate this whole session" is on; otherwise each song starts untranslated.
     */
    fun onSongChanged(songId: String?, sessionWide: Boolean) {
        if (songId == null) return
        if (lastSongId != null && lastSongId != songId && !sessionWide) {
            _translationOn.value = false
            _hasActiveTranslations.value = false
        }
        lastSongId = songId
    }

    // TODO(lyrics-engine): the staged LyricsEntity has no translatedLyrics field yet (see the
    // header). Always false until the coordinator adds the translation fields.
    fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean {
        warnOnce("hasTranslations")
        return false
    }

    // TODO(lyrics-engine): see hasTranslations — returns the row unchanged for now.
    fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity {
        warnOnce("clearTranslations")
        return lyricsEntity
    }

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        translationCache.clear()
    }

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun cancelTranslation() {
        isCompositionActive = false
        translationJob?.cancel()
        translationJob = null
    }

    /**
     * @return true when a stored translation matching [targetLanguage] and [mode] was applied.
     * TODO(lyrics-engine): always false — the staged LyricsEntity carries no stored
     * translation yet (see the header). Restore the Mochy body once the fields exist.
     */
    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ): Boolean {
        warnOnce("loadTranslationsFromDatabase")
        lyrics.forEach { it.translatedTextFlow.value = null }
        _hasActiveTranslations.value = false
        return false
    }

    // TODO(lyrics-engine): AI translation (OpenRouter / DeepL) is not ported. Logs and leaves
    // the status Idle so the translate button never hangs on "Translating…".
    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
    ) {
        warnOnce("translateLyrics")
        _status.value = TranslationStatus.Idle
    }

    // TODO(lyrics-engine): Google Translate is not ported. See translateLyrics.
    fun translateWithGoogle(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        mode: String,
        sourceLanguage: String = "auto",
        scope: CoroutineScope,
        songId: String = "",
    ) {
        warnOnce("translateWithGoogle")
        lyrics.forEach { it.translatedTextFlow.value = null }
        _status.value = TranslationStatus.Idle
    }

    /**
     * Shows the video's own uploaded translation when YouTube has one in the target language;
     * false when it doesn't, so the caller translates the usual way.
     * TODO(lyrics-engine): YouTube caption translation is not ported; always false.
     */
    suspend fun translateWithYouTubeCaptions(
        lyrics: List<LyricsEntry>,
        request: YouTubeCaptionTranslation.Request,
        lyricsEntity: LyricsEntity?,
        mode: String,
    ): Boolean {
        warnOnce("translateWithYouTubeCaptions")
        return false
    }

    private val warned = mutableSetOf<String>()

    private fun warnOnce(name: String) {
        if (warned.add(name)) {
            Timber.w("LyricsTranslationHelper.%s is not wired yet (Phase 3 lyrics engine)", name)
        }
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}

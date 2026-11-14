// Ported from Mochy Music (GPL-3.0): constants/PreferenceKeys.kt (lyrics slice)
// Only the lyrics/translation/romanisation keys this chunk's UI reads. The four translation
// keys the player menus also read (TranslateLyricsKey, TranslateLanguageKey,
// TranslateSourceLanguageKey, YouTubeCaptionTranslationKey) are staged by
// phase3-player-menus in constants/PlayerMenuKeys.kt — NOT duplicated here.

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// NOTE (coordinator, 15 Nov): LyricsHighBloomKey, LyricsMotionStyleKey and the
// LyricsMotionStyle enum live in constants/PreferenceKeys.kt (same package) —
// NOT duplicated here, or the build breaks twice.
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeRussianKey = booleanPreferencesKey("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = booleanPreferencesKey("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = booleanPreferencesKey("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = booleanPreferencesKey("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = booleanPreferencesKey("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = booleanPreferencesKey("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = booleanPreferencesKey("lyricsRomanizeMacedonian")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = booleanPreferencesKey("lyricsRomanizePunjabi")
val LyricsRomanizeAsMainKey = booleanPreferencesKey("lyricsRomanizeAsMain")
val OpenRouterApiKey = stringPreferencesKey("openRouterApiKey")
val AiProviderKey = stringPreferencesKey("aiProvider")
val OpenRouterBaseUrlKey = stringPreferencesKey("openRouterBaseUrl")
val OpenRouterModelKey = stringPreferencesKey("openRouterModel")
val TranslateModeKey = stringPreferencesKey("translateMode")
val DeeplApiKey = stringPreferencesKey("deeplApiKey")
val DeeplFormalityKey = stringPreferencesKey("deeplFormality")

// (LyricsMotionStyleKey + LyricsMotionStyle live in PreferenceKeys.kt — see the note at the top of this file.)

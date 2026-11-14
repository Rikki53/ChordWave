// PlayerMenuKeys.kt — preference keys the player menus + song edit sheets read.
// Verbatim key names/strings from Mochy's PreferenceKeys.kt. The coordinator
// may fold these into constants/PreferenceKeys.kt; they live here so this
// chunk is self-contained until then.
//
// - KeepScreenOn: Mochy PreferenceKeys.kt:86 ("keepScreenOn")
// - UsbPassthroughKey lives in PlayerQueueKeys.kt (queue-info chunk —
//   USB DAC passthrough belongs with the audio-pass state); NOT
//   duplicated here (same package — a second declaration would break
//   the build).
// - PlayVideoBackgroundKey: Mochy PreferenceKeys.kt:467 ("playVideoBackground")
// - TranslateLyricsKey: Mochy PreferenceKeys.kt:940 ("translateLyrics")
// - TranslateLanguageKey: Mochy PreferenceKeys.kt:946 ("translateLanguage")
// - TranslateSourceLanguageKey: Mochy PreferenceKeys.kt:948 ("translateSourceLanguage")
// - YouTubeCaptionTranslationKey: Mochy PreferenceKeys.kt:950 ("youtubeCaptionTranslation")

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val KeepScreenOn = booleanPreferencesKey("keepScreenOn")

val PlayVideoBackgroundKey = booleanPreferencesKey("playVideoBackground")

val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")

val TranslateLanguageKey = stringPreferencesKey("translateLanguage")

val TranslateSourceLanguageKey = stringPreferencesKey("translateSourceLanguage")

val YouTubeCaptionTranslationKey = booleanPreferencesKey("youtubeCaptionTranslation")

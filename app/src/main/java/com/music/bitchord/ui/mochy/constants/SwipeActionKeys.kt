// Swipe-action preference keys, from Mochy Music (GPL-3.0)
// constants/PreferenceKeys.kt:53-54. Kept in this phase's directory because
// the phase1-deps PreferenceKeys.kt is owned by another staging phase —
// merge these two keys into it at integration time (same key strings,
// "swipeRightAction" / "swipeLeftAction", so stored values carry over).

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.stringPreferencesKey

val SwipeRightActionKey = stringPreferencesKey("swipeRightAction")
val SwipeLeftActionKey = stringPreferencesKey("swipeLeftAction")

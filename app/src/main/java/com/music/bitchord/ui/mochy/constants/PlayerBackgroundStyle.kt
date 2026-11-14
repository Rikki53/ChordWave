// Ported from Mochy Music (GPL-3.0): constants/PreferenceKeys.kt — PlayerBackgroundStyle.
// ChordWave keeps its own settings; this enum is the player background style
// the ported Mochy player UI reads.
package com.music.bitchord.ui.mochy.constants

enum class PlayerBackgroundStyle {

    COVER_GRADIENT,

    SOLID,

    DARK_GRADIENT,

    APPLE_MUSIC,

    // the cover's colours drifting slowly, following a playing video or animated cover as it changes
    AMBIENT,
}

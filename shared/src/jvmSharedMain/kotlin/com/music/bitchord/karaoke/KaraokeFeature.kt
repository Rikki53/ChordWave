package com.music.bitchord.karaoke

/**
 * Master switch for the karaoke feature: Instant centre-cancel, AI stem
 * separation, Karaoke YT source pairing, the lyrics mic rail, the "Sing"
 * pill and the phone-as-remote mode.
 *
 * Parked off for now (product call, 9 Nov 2026): karaoke is heavy and needs
 * a serious round of testing before it faces listeners again, so the app is
 * music only. Every karaoke surface and behaviour gates on this one flag —
 * the Equalizer block, the player ⋯ sheet, the lyrics mic rail, the TV
 * dialogs, the DSP stages and both source swaps — so turning the feature
 * back on is this one line.
 *
 * Nothing is deleted and no user data goes with it: the saved karaoke
 * preferences, YT pairings and AI stems on disk are simply ignored while
 * the flag is off, and playback carries on with the original source,
 * untouched.
 */
object KaraokeFeature {
    const val ENABLED: Boolean = false
}

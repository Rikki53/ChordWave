// AudioPassState.kt — data-only port of the state types in Mochy's
// playback/audio/pass/AudioPass.kt (Status, Skip) and
// playback/audio/pass/PassBlockers.kt (PassBlocker), plus one snapshot that
// carries what the stats sheet reads. The AudioPass engine (exclusive usb
// output, dac control) is NOT ported — see NEEDS.md §(b). Names are
// prefixed (AudioPassStatus / AudioPassSkip) because Mochy's live in the
// AudioPass class, which does not exist here.

package com.music.bitchord.ui.mochy.playback.audio.pass

import com.music.bitchord.ui.mochy.constants.AudioPassMode

sealed interface AudioPassStatus {
    data object Off : AudioPassStatus
    data object Waiting : AudioPassStatus
    data class Playing(
        val mode: AudioPassMode,
        val device: String,
        val sampleRate: Int,
        val bits: Int,
        val sourceBits: Int,
        val lossless: Boolean,
        val exact: Boolean,
    ) : AudioPassStatus

    data class Fell(val mode: AudioPassMode, val sampleRate: Int, val sourceBits: Int) : AudioPassStatus
}

/** why a song went the normal way while audio pass was on, so the stats sheet can say it instead of leaving people guessing */
enum class AudioPassSkip { Busy, Format, Lossy, FellBefore, NoDac, NoUsbAccess, DacCantPlay }

enum class PassBlocker { EQUALIZER, AUDIO_FX, VOLUME, NORMALIZATION, SPEED, TRANSITIONS, SILENCE_OR_HAPTICS }

/** everything the passthrough stat line reads, gathered in one place */
data class AudioPassSnapshot(
    val status: AudioPassStatus,
    val skip: AudioPassSkip?,
    val blockers: Set<PassBlocker>,
)

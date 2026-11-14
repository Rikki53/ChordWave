// AudioOutputs.kt — data-only port of the nested types in Mochy's
// playback/audio/AudioOutputs.kt (GPL-3.0): the output device kinds, one
// device, and the usb dac's mixer setting. The AudioOutputs engine (device
// tracking, routing, mixer control) is NOT ported — see NEEDS.md §(b).
// Kept as an object so StreamStats' `AudioOutputs.Device` / `.Kind` /
// `.Mixer` references read exactly as they do in Mochy.

package com.music.bitchord.ui.mochy.playback.audio

object AudioOutputs {

    enum class Kind { PHONE, WIRED, USB, BLUETOOTH, HDMI }

    data class Device(
        val id: Int,
        val type: Int,
        /** blank for the phone and wired headphones: the ui names those itself */
        val name: String,
        val kind: Kind,
        val isActive: Boolean,
        val isChosen: Boolean,
        /** the highest rate it says it takes, null when it takes whatever it's given */
        val maxSampleRate: Int? = null,
        val bitDepths: List<Int> = emptyList(),
    )

    /** what the usb dac's own mixer was set to for the song playing */
    data class Mixer(
        val deviceName: String,
        val sampleRate: Int,
        val encoding: Int,
        val bitPerfect: Boolean,
    )
}

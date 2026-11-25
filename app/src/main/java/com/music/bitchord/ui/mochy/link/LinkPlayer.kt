// Ported from Mochy Music (GPL-3.0): link/LinkPlayer.kt
// LinkPlayer.kt
//
// The queue interceptors are ChordWave's seam for Mochy's
// RemoteControl.takeQueue hooks: MochyPlayerConnection asks them before
// playing locally, and RemoteControl fills them in while a device is
// being steered.

package com.music.bitchord.ui.mochy.link

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.Queue

// the open app's player, for a paired device that plays, skips or changes something here; null while no screen is up
object LinkPlayer {
    @Volatile var connection: MochyPlayerConnection? = null

    // a quiet stand-in from android auto, for a phone in the car whose own ChordWave screen was never opened
    @Volatile var background: MochyPlayerConnection? = null

    // the screen's player when there is one, otherwise the car's
    fun current(): MochyPlayerConnection? = connection ?: background

    // only the screen that set it may clear it, so a recreated activity doesn't wipe the new one's player
    fun release(connection: MochyPlayerConnection?) {
        if (connection != null && this.connection === connection) this.connection = null
    }

    fun releaseBackground(connection: MochyPlayerConnection?) {
        if (connection != null && background === connection) background = null
    }

    // while a device is being steered, queues picked anywhere in the app go there instead; true when taken
    @Volatile var queueInterceptor: ((Queue) -> Boolean)? = null
    @Volatile var nextInterceptor: ((List<MediaItem>) -> Boolean)? = null
    @Volatile var queueEndInterceptor: ((List<MediaItem>) -> Boolean)? = null
}

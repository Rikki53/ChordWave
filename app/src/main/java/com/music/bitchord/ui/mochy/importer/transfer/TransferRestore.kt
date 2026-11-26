// Batch 55a: the one piece of Mochy's TransferRestore the backup page calls.
// ChordWave applies a restored backup live (see TransferImporter), so all
// that is left is Mochy's restart-to-finish: stop playback, bring the app
// back fresh, end this process.

package com.music.bitchord.ui.mochy.importer.transfer

import android.content.Context
import android.content.Intent
import com.music.bitchord.MainActivity
import com.music.bitchord.playback.mochy.MochyMusicService
import kotlin.system.exitProcess

object TransferRestore {

    fun restartToFinish(context: Context) {
        context.stopService(Intent(context, MochyMusicService::class.java))
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        exitProcess(0)
    }
}

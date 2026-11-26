// Ported from Mochy Music (GPL-3.0): db/entities/PlaylistSong.kt — verbatim, for the Mochy Room database (two-engine Tahap 2).
package com.mochyy.mochymusic.db.entities

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistSong(
    @Embedded val map: PlaylistSongMap,
    @Relation(
        parentColumn = "songId",
        entityColumn = "id",
        entity = SongEntity::class,
    )
    val song: Song,
)

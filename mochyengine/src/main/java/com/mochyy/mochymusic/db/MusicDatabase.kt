// Ported from Mochy Music (GPL-3.0): db/MusicDatabase.kt — the Room database
// of the Mochy engine, now the shared library database of the two-engine
// architecture (see RENCANA-DUA-MESIN.md, Tahap 2 "Basis data").
//
// ADAPTATIONS for ChordWave:
// - Schema version restarts at 1. Mochy's own database is at version 45 after
//   years of hand-written migrations, but no ChordWave install has ever had a
//   mochy.db, so there is nothing to migrate from: the first schema here is
//   the current Mochy entity set (the library core), created fresh. Future
//   schema changes bump the version and add migrations as usual.
// - No fallbackToDestructiveMigration. Mochy uses it; here a database that
//   cannot be opened must fail loudly so the app falls back to the legacy
//   ChordWave stores instead of silently emptying the shared library.
// - The import-engine entities (ImportSession, ImportedSong, ...) and the
//   recognition history are not ported yet; they belong to a later stage.

package com.mochyy.mochymusic.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.mochyy.mochymusic.db.daos.SpeedDialDao
import com.mochyy.mochymusic.db.entities.AlbumArtistMap
import com.mochyy.mochymusic.db.entities.AlbumEntity
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.PlayCountEntity
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.PlaylistSongMapPreview
import com.mochyy.mochymusic.db.entities.RelatedSongMap
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.db.entities.SetVideoIdEntity
import com.mochyy.mochymusic.db.entities.SongAlbumMap
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.db.entities.SortedSongAlbumMap
import com.mochyy.mochymusic.db.entities.SortedSongArtistMap
import timber.log.Timber

class MusicDatabase(
    private val delegate: InternalDatabase,
) : DatabaseDao by delegate.dao {
    val speedDialDao: SpeedDialDao
        get() = delegate.speedDialDao

    /**
     * Pins [item], evicting the oldest pin first if that would push the count past
     * [com.mochyy.mochymusic.db.daos.SpeedDialDao.Companion.MaxPinned].
     *
     * The one place pins are created. Every menu that offers "Pin to Speed dial" should call this
     * instead of `speedDialDao.insert` directly — the cap only holds if there's a single entry
     * point enforcing it, and scattering the limit check across each menu's onClick would drift
     * the moment one of them was written (or edited) without it.
     */
    suspend fun pinToSpeedDial(item: com.mochyy.mochymusic.db.entities.SpeedDialItem) {
        if (speedDialDao.count() >= SpeedDialDao.MaxPinned) {
            speedDialDao.oldest()?.let { speedDialDao.delete(it.id) }
        }
        speedDialDao.insert(item)
    }

    val openHelper: SupportSQLiteOpenHelper
        get() = delegate.openHelper

    fun query(block: MusicDatabase.() -> Unit) =
        with(delegate) {
            queryExecutor.execute {
                block(this@MusicDatabase)
            }
        }

    fun transaction(block: MusicDatabase.() -> Unit) =
        with(delegate) {
            transactionExecutor.execute {
                runInTransaction {
                    block(this@MusicDatabase)
                }
            }
        }

    suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit) =
        with(delegate) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runInTransaction {
                    kotlinx.coroutines.runBlocking {
                        block(this@MusicDatabase)
                    }
                }
            }
        }

    fun close() = delegate.close()
}

// Bump this with every schema change and add its migration. Version 1 is the
// first ChordWave schema (see the file header for why it is not Mochy's 45).
const val MusicDatabaseVersion = 1

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        SongArtistMap::class,
        SongAlbumMap::class,
        AlbumArtistMap::class,
        PlaylistSongMap::class,
        SearchHistory::class,
        FormatEntity::class,
        LyricsEntity::class,
        Event::class,
        RelatedSongMap::class,
        SetVideoIdEntity::class,
        PlayCountEntity::class,
        SpeedDialItem::class,
    ],
    views = [
        SortedSongArtistMap::class,
        SortedSongAlbumMap::class,
        PlaylistSongMapPreview::class,
    ],
    version = MusicDatabaseVersion,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao
    abstract val speedDialDao: SpeedDialDao

    companion object {
        const val DB_NAME = "mochy.db"

        fun newInstance(context: Context): MusicDatabase =
            MusicDatabase(
                delegate =
                Room
                    .databaseBuilder(context, InternalDatabase::class.java, DB_NAME)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
                    .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.query("PRAGMA busy_timeout = 60000").close()
                                db.query("PRAGMA cache_size = -16000").close()
                                db.query("PRAGMA wal_autocheckpoint = 1000").close()
                                db.query("PRAGMA synchronous = NORMAL").close()
                            } catch (e: Exception) {
                                Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
                            }
                        }
                    })
                    .build(),
            )
    }
}

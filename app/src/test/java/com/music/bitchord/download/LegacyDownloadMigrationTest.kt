package com.music.bitchord.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time move of pre-export downloads into Music/ChordWave, judged on
 * the record alone: which recorded files are still app-private, which of
 * those Android can file at all, and why running it twice moves nothing the
 * second time. The idempotence tests are the point of the class — the move
 * is gated by a settings flag in the app, and these pin that the flag is a
 * shortcut rather than the thing keeping Music free of duplicates.
 */
class LegacyDownloadMigrationTest {

    private val filesDir = "/data/user/0/com.riki.chordwave/files"
    private val privateDir = "$filesDir/downloads"

    private fun fileEntry(id: String, path: String) =
        LegacyDownloadMigration.Entry(id, "file://$path", path)

    private fun contentEntry(id: String, mediaId: Long) = LegacyDownloadMigration.Entry(
        id,
        "content://media/external/audio/media/$mediaId",
        null,
    )

    private fun plan(
        entries: List<LegacyDownloadMigration.Entry>,
        alreadyExported: Set<String> = emptySet(),
    ) = LegacyDownloadMigration.plan(entries, privateDir) { it in alreadyExported }

    @Test
    fun `private downloads Android can file are planned for publishing`() {
        val planned = plan(
            listOf(
                fileEntry("a", "$privateDir/Arijit Singh - Kesariya.m4a"),
                fileEntry("b", "$privateDir/Pink Floyd - Time.flac"),
                fileEntry("c", "$privateDir/Dewa - Kangen.mp3"),
            ),
        )

        assertEquals(3, planned.size)
        assertTrue(planned.all { it.action == LegacyDownloadMigration.Action.PUBLISH })
        assertEquals("audio/mp4", planned[0].mimeType)
        assertEquals("audio/flac", planned[1].mimeType)
        assertEquals("audio/mpeg", planned[2].mimeType)
        assertEquals("Arijit Singh - Kesariya.m4a", planned[0].fileName)
        assertEquals(listOf("a"), planned[0].videoIds)
    }

    @Test
    fun `extensions are matched however the file spells them`() {
        val planned = plan(listOf(fileEntry("a", "$privateDir/Band - Song.FLAC")))
        assertEquals(1, planned.size)
        assertEquals("audio/flac", planned.single().mimeType)
    }

    @Test
    fun `downloads already in Music are not planned again`() {
        // An exported download's record is a content uri on API 29+, and a
        // path in the public Music folder before it. Neither is private.
        val planned = plan(
            listOf(
                contentEntry("a", 42),
                fileEntry("b", "/storage/emulated/0/Music/ChordWave/Band - Song.m4a"),
                fileEntry("c", "/storage/emulated/0/Music/BitChord/Band - Older.m4a"),
            ),
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `offline HLS and DASH packages stay private`() {
        val planned = plan(
            listOf(
                // Where the packages actually live — a directory of segments
                // under offline-hls, named by its playlist.
                fileEntry("a", "$filesDir/offline-hls/1a2b3c/playlist.m3u8"),
                // Belt and braces: a playlist name is never a portable file,
                // even if one ever landed in the downloads folder itself.
                fileEntry("b", "$privateDir/playlist.m3u8"),
            ),
        )
        assertTrue(planned.isEmpty())
        assertTrue(LegacyDownloadMigration.isOfflinePackage("$filesDir/offline-hls/1a2b3c/playlist.m3u8"))
        assertFalse(LegacyDownloadMigration.isOfflinePackage("$privateDir/Band - Song.m4a"))
    }

    @Test
    fun `webm stays private because the media store refuses it`() {
        assertNull(LegacyDownloadMigration.exportMimeType("webm"))
        assertNull(LegacyDownloadMigration.exportMimeType("opus"))
        val planned = plan(listOf(fileEntry("a", "$privateDir/Band - Song.webm")))
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `files with no fileable type are left alone`() {
        val planned = plan(
            listOf(
                fileEntry("a", "$privateDir/Band - Song.xyz"),
                fileEntry("b", "$privateDir/noextension"),
                fileEntry("c", "$privateDir/.hidden.part"),
            ),
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `the two ids of one file are planned once, together`() {
        // A music video and the catalogue track it resolved to are recorded
        // as two ids for one file; the file moves once, under both.
        val path = "$privateDir/Band - Song.m4a"
        val planned = plan(listOf(fileEntry("asked", path), fileEntry("fetched", path)))

        assertEquals(1, planned.size)
        assertEquals(listOf("asked", "fetched"), planned.single().videoIds)
    }

    @Test
    fun `a name already in Music is adopted, never published again`() {
        // The crash window: the file was published but the record still says
        // private. Re-running must land on the copy that is already there.
        val planned = plan(
            listOf(fileEntry("a", "$privateDir/Band - Song.m4a")),
            alreadyExported = setOf("Band - Song.m4a"),
        )
        assertEquals(1, planned.size)
        assertEquals(LegacyDownloadMigration.Action.ADOPT_EXISTING, planned.single().action)
    }

    @Test
    fun `after a move, the same record plans nothing`() {
        val before = listOf(
            fileEntry("a", "$privateDir/Band - Song.m4a"),
            fileEntry("b", "$privateDir/Band - Other.flac"),
        )
        assertEquals(2, plan(before).size)

        // What the migration leaves behind: every moved id repointed at the
        // uri DownloadStore handed back, a content uri on a modern device.
        val after = listOf(contentEntry("a", 101), contentEntry("b", 102))
        assertTrue(plan(after).isEmpty())
        // And with the store consulted as well, still nothing.
        assertTrue(plan(after, alreadyExported = setOf("Band - Song.m4a")).isEmpty())
    }

    @Test
    fun `the private prefix is a directory boundary, not a substring`() {
        val planned = plan(
            listOf(
                fileEntry("a", "$filesDir/downloads-backup/Band - Song.m4a"),
                fileEntry("b", "$filesDir/downloads2/Band - Song.m4a"),
                fileEntry("c", "$filesDir/cache/downloads/Band - Song.m4a"),
            ),
        )
        assertTrue(planned.isEmpty())
        assertTrue(LegacyDownloadMigration.isPrivateDownload("$privateDir/x.m4a", "$privateDir/"))
    }

    @Test
    fun `the mime map covers what downloads are filed as`() {
        assertEquals("audio/flac", LegacyDownloadMigration.exportMimeType("flac"))
        assertEquals("audio/x-wav", LegacyDownloadMigration.exportMimeType("wav"))
        assertEquals("audio/mp4", LegacyDownloadMigration.exportMimeType("m4a"))
        assertEquals("audio/mpeg", LegacyDownloadMigration.exportMimeType("mp3"))
        assertEquals("audio/aac", LegacyDownloadMigration.exportMimeType("aac"))
        assertEquals("audio/ogg", LegacyDownloadMigration.exportMimeType("ogg"))
        assertNull(LegacyDownloadMigration.exportMimeType("m3u8"))
        assertNull(LegacyDownloadMigration.exportMimeType(""))
    }
}

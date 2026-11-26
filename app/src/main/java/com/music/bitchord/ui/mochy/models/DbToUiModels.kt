// Batch 55b: conversions from the Mochy Room entities (:mochyengine) to
// ChordWave's Room-free UI mirrors in this package. The module pages read
// library rows from the database but the menu sheets take the UI models,
// so the two shapes (identical fields) need a bridge.

package com.music.bitchord.ui.mochy.models

fun com.mochyy.mochymusic.db.entities.SongEntity.toUiModel() = SongEntity(
    id = id,
    title = title,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    albumId = albumId,
    albumName = albumName,
    explicit = explicit,
    year = year,
    date = date,
    dateModified = dateModified,
    liked = liked,
    likedDate = likedDate,
    totalPlayTime = totalPlayTime,
    inLibrary = inLibrary,
    dateDownload = dateDownload,
    isLocal = isLocal,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
    lyricsOffset = lyricsOffset,
    romanizeLyrics = romanizeLyrics,
    isDownloaded = isDownloaded,
    isUploaded = isUploaded,
)

fun com.mochyy.mochymusic.db.entities.ArtistEntity.toUiModel() = ArtistEntity(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    channelId = channelId,
    lastUpdateTime = lastUpdateTime,
    bookmarkedAt = bookmarkedAt,
    isLocal = isLocal,
)

fun com.mochyy.mochymusic.db.entities.AlbumEntity.toUiModel() = AlbumEntity(
    id = id,
    playlistId = playlistId,
    title = title,
    year = year,
    thumbnailUrl = thumbnailUrl,
    themeColor = themeColor,
    songCount = songCount,
    duration = duration,
    explicit = explicit,
    lastUpdateTime = lastUpdateTime,
    bookmarkedAt = bookmarkedAt,
    likedDate = likedDate,
    inLibrary = inLibrary,
    description = description,
    isLocal = isLocal,
    isUploaded = isUploaded,
)

fun com.mochyy.mochymusic.db.entities.PlaylistEntity.toUiModel() = PlaylistEntity(
    id = id,
    name = name,
    browseId = browseId,
    createdAt = createdAt,
    lastUpdateTime = lastUpdateTime,
    isEditable = isEditable,
    bookmarkedAt = bookmarkedAt,
    remoteSongCount = remoteSongCount,
    playEndpointParams = playEndpointParams,
    thumbnailUrl = thumbnailUrl,
    shuffleEndpointParams = shuffleEndpointParams,
    radioEndpointParams = radioEndpointParams,
    isLocal = isLocal,
    isAutoSync = isAutoSync,
    description = description,
)

fun com.mochyy.mochymusic.db.entities.Song.toUiModel() = Song(
    song = song.toUiModel(),
    artists = artists.map { it.toUiModel() },
    album = album?.toUiModel(),
)

fun com.mochyy.mochymusic.db.entities.Album.toUiModel() = Album(
    album = album.toUiModel(),
    artists = artists.map { it.toUiModel() },
    songCountListened = songCountListened,
    timeListened = timeListened,
)

fun com.mochyy.mochymusic.db.entities.Playlist.toUiModel() = Playlist(
    playlist = playlist.toUiModel(),
    songCount = songCount,
    songThumbnails = songThumbnails,
)

fun com.mochyy.mochymusic.db.entities.Artist.toUiModel() = Artist(
    artist = artist.toUiModel(),
    songCount = songCount,
    timeListened = timeListened,
)

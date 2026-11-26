// Batch 55a: for the Modern player service and connection, which Hilt
// doesn't build — they reach the Discord presence and the scrobbler
// through here, the way Mochy's MusicService is injected with them.

package com.music.bitchord.ui.mochy.di

import com.music.bitchord.data.mochydata.discord.DiscordPresence
import com.music.bitchord.data.mochydata.scrobble.Scrobbler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerIntegrationsEntryPoint {
    fun discordPresence(): DiscordPresence

    fun scrobbler(): Scrobbler
}

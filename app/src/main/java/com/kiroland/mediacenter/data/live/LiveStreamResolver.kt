package com.kiroland.mediacenter.data.live

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Turns a channel into a stream the built-in player can play. The public build ships none, so channels
 * open in the broadcaster's own app or website; a build may provide one for its owner's personal use.
 */
interface LiveStreamResolver {
    /** Whether this resolver knows the channel at all. */
    fun supports(channel: LiveChannel): Boolean

    /** A playable stream URL (HLS/DASH), or null if the channel cannot be resolved right now. Blocking. */
    suspend fun resolve(channel: LiveChannel): String?
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveStreamResolverModule {
    @BindsOptionalOf
    abstract fun optionalResolver(): LiveStreamResolver
}

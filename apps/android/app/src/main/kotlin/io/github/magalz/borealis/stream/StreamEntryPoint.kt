package io.github.magalz.borealis.stream

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.HostRepository

/**
 * What the stream activity takes from the Hilt graph. `Game` is not an `@AndroidEntryPoint`, so it
 * asks the application component for the repositories its actions need.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamEntryPoint {
    fun appRepository(): AppRepository

    fun hostRepository(): HostRepository

    companion object {
        fun from(context: Context): StreamEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                StreamEntryPoint::class.java,
            )
    }
}

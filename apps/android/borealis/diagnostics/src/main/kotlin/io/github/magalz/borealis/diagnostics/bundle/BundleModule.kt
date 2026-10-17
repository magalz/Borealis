package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.LogSwitches
import io.github.magalz.borealis.logging.PreferencesLogSwitches
import javax.inject.Singleton

/**
 * Declares the set of [DiagnosticSection]s, so the bundle works when no module contributes one.
 * Modules add theirs with `@Binds @IntoSet`. It also provides the [LogSwitches] the bundle manifest
 * reads; the module that stores the switches elsewhere replaces this binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BundleModule {
    @Multibinds abstract fun diagnosticSections(): Set<DiagnosticSection>

    companion object {
        @Provides
        @Singleton
        fun logSwitches(@ApplicationContext context: Context): LogSwitches =
            PreferencesLogSwitches(context)
    }
}

/** Gives classes that Hilt does not create, such as preferences, the contributed sections. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DiagnosticsEntryPoint {
    fun diagnosticSections(): Set<DiagnosticSection>

    fun logSwitches(): LogSwitches
}

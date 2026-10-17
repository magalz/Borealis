package io.github.magalz.borealis.diagnostics.bundle

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.LogSwitches

/**
 * Declares the set of [DiagnosticSection]s, so the bundle works when no module contributes one.
 * Modules add theirs with `@Binds @IntoSet`. The [LogSwitches] that the bundle manifest reads are
 * bound by the settings module (`SettingsStoreModule`, ADR 0055), from the DataStore-backed
 * repository.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BundleModule {
    @Multibinds abstract fun diagnosticSections(): Set<DiagnosticSection>
}

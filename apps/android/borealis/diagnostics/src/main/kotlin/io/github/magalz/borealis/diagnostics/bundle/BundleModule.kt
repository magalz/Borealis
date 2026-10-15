package io.github.magalz.borealis.diagnostics.bundle

import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.magalz.borealis.diagnostics.DiagnosticSection

/**
 * Declares the set of [DiagnosticSection]s, so the bundle works when no module contributes one.
 * Modules add theirs with `@Binds @IntoSet`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BundleModule {
    @Multibinds abstract fun diagnosticSections(): Set<DiagnosticSection>
}

/** Gives classes that Hilt does not create, such as preferences, the contributed sections. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DiagnosticsEntryPoint {
    fun diagnosticSections(): Set<DiagnosticSection>
}

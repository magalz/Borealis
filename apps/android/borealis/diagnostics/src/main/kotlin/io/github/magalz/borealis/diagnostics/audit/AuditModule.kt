package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.AuditSink
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Provides the audit trail (ADR 0050). The set of [DiagnosticSection]s is declared by the bundle;
 * this module only contributes [AuditSection] to it.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuditModule {
    /** In `noBackupFilesDir`, so the trail is excluded from Android backup. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AuditDatabase =
        Room.databaseBuilder(
                context,
                AuditDatabase::class.java,
                File(context.noBackupFilesDir, "audit.db").path,
            )
            .build()

    @Provides
    @Singleton
    fun store(db: AuditDatabase): AuditStore =
        AuditStore(db.dao(), CoroutineScope(SupervisorJob() + Dispatchers.IO), Dispatchers.IO)

    @Provides fun sink(store: AuditStore): AuditSink = store

    @Provides @IntoSet fun section(store: AuditStore): DiagnosticSection = AuditSection(store)
}

/** Lets framework classes that Hilt does not create, such as preferences, reach the store. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuditEntryPoint {
    fun auditStore(): AuditStore
}

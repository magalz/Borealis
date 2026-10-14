package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.AuditSink
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AuditModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AuditDatabase =
        Room.inMemoryDatabaseBuilder(context, AuditDatabase::class.java).build()

    @Provides
    @Singleton
    fun store(db: AuditDatabase): AuditStore =
        AuditStore(db.dao(), CoroutineScope(Dispatchers.IO), Dispatchers.IO)

    @Provides fun sink(store: AuditStore): AuditSink = store

    @Provides @IntoSet fun section(store: AuditStore): DiagnosticSection = AuditSection(store)
}

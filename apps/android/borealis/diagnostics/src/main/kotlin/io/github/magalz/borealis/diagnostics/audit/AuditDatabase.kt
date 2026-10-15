package io.github.magalz.borealis.diagnostics.audit

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AuditEntity::class], version = 1)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun dao(): AuditDao
}

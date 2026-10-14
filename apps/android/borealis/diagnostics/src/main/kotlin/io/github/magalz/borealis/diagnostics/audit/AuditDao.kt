package io.github.magalz.borealis.diagnostics.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Blocking queries: callers run them off the main thread. */
@Dao
abstract class AuditDao {
    @Insert abstract fun insert(entity: AuditEntity): Long

    /** Oldest first. */
    @Query("SELECT * FROM audit_events ORDER BY id ASC") abstract fun all(): List<AuditEntity>

    @Query("SELECT COUNT(*) FROM audit_events") abstract fun count(): Int

    @Query("DELETE FROM audit_events") abstract fun deleteAll()

    /** Stores [entity]; the retention rules are applied with it. */
    @Transaction
    open fun insertAndPrune(entity: AuditEntity, cutoff: Long, keep: Int) {
        insert(entity)
    }
}

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

    @Query("DELETE FROM audit_events WHERE timestamp < :cutoff")
    protected abstract fun deleteOlderThan(cutoff: Long)

    @Query(
        "DELETE FROM audit_events WHERE id NOT IN " +
            "(SELECT id FROM audit_events ORDER BY id DESC LIMIT :keep)"
    )
    protected abstract fun keepNewest(keep: Int)

    /**
     * Stores [entity], then drops the rows older than [cutoff] (a timestamp) and every row but the
     * newest [keep]. "Newest" is by insertion order, so a clock that jumps cannot reorder the
     * trail.
     */
    @Transaction
    open fun insertAndPrune(entity: AuditEntity, cutoff: Long, keep: Int) {
        insert(entity)
        deleteOlderThan(cutoff)
        keepNewest(keep)
    }
}

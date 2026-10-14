package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditSink
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditModuleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the database file is audit db in the no-backup directory`() {
        val db = AuditModule.database(context)
        try {
            db.openHelper.writableDatabase

            assertThat(File(context.noBackupFilesDir, "audit.db").isFile).isTrue()
        } finally {
            db.close()
        }
    }

    @Test
    fun `the sink and the diagnostic section are backed by the store`() {
        val db = AuditModule.database(context)
        try {
            val store = AuditModule.store(db)

            val sink: AuditSink = AuditModule.sink(store)
            val section = AuditModule.section(store)

            assertThat(sink).isSameInstanceAs(store)
            assertThat(section.fileName).isEqualTo("audit.jsonl")
        } finally {
            db.close()
        }
    }
}

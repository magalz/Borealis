package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditDaoTest {
    private lateinit var db: AuditDatabase
    private val dao
        get() = db.dao()

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    AuditDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
    }

    @After fun tearDown() = db.close()

    private fun row(at: Long, detail: String = "d") =
        AuditEntity.of(event(at = at, detail = detail))

    @Test
    fun `rows come back in the order they were inserted`() {
        dao.insertAndPrune(row(30, "c"), cutoff = 0, keep = 10)
        dao.insertAndPrune(row(10, "a"), cutoff = 0, keep = 10)
        dao.insertAndPrune(row(20, "b"), cutoff = 0, keep = 10)

        assertThat(dao.all().map { it.detail }).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `an insert keeps only the newest rows`() {
        for (i in 1..5) dao.insertAndPrune(row(i.toLong(), "d$i"), cutoff = 0, keep = 3)

        assertThat(dao.all().map { it.detail }).containsExactly("d3", "d4", "d5").inOrder()
    }

    @Test
    fun `an insert drops rows older than the cutoff`() {
        dao.insertAndPrune(row(10, "old"), cutoff = 0, keep = 10)
        dao.insertAndPrune(row(100, "new"), cutoff = 50, keep = 10)

        assertThat(dao.all().map { it.detail }).containsExactly("new")
    }

    @Test
    fun `deleting all empties the table`() {
        dao.insertAndPrune(row(1), cutoff = 0, keep = 10)

        dao.deleteAll()

        assertThat(dao.count()).isEqualTo(0)
    }

    @Test
    fun `a row with an unknown type is not an event`() {
        assertThat(row(1).copy(type = "FROM_A_FUTURE_VERSION").toEvent()).isNull()
        assertThat(row(1).copy(outcome = "MAYBE").toEvent()).isNull()
        assertThat(row(2).copy(type = AuditType.SESSION_END.name).toEvent()).isNotNull()
    }
}

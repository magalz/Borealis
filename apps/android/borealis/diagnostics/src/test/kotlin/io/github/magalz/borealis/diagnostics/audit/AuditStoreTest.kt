package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditStoreTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var db: AuditDatabase
    private lateinit var store: AuditStore

    private fun newDatabase(): AuditDatabase =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                AuditDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()

    @Before
    fun setUp() {
        db = newDatabase()
        store = AuditStore(db.dao(), scope, dispatcher)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun settle() = scheduler.advanceUntilIdle()

    @Test
    fun `an event is written off the calling thread`() {
        store.write(event())

        assertThat(db.dao().count()).isEqualTo(0)

        settle()

        assertThat(db.dao().count()).isEqualTo(1)
    }

    @Test
    fun `a stored event keeps every field`() {
        val written =
            event(
                at = 1_700_000_123_456L,
                type = AuditType.LAUNCH_RETRY,
                host = "Den PC (10.0.0.7)",
                detail = "category=PENDING attempt=2/4",
                outcome = AuditOutcome.FAILURE,
            )

        store.write(written)
        settle()

        assertThat(store.entries()).containsExactly(written)
    }

    @Test
    fun `entries come oldest first`() {
        for (i in 1..3) store.write(event(at = i * 20 * MINUTE, detail = "d$i"))
        settle()

        assertThat(store.entries().map { it.detail }).containsExactly("d1", "d2", "d3").inOrder()
    }

    @Test
    fun `a repeating event is stored once per day`() {
        store.write(event(at = 0, type = AuditType.TLS_FAILURE))
        store.write(event(at = 10 * MINUTE, type = AuditType.TLS_FAILURE))
        store.write(event(at = DAY, type = AuditType.TLS_FAILURE))
        settle()

        assertThat(store.entries().map { it.timestampMillis }).containsExactly(0L, DAY)
    }

    @Test
    fun `user-driven events are never deduplicated`() {
        store.write(event(at = 0, type = AuditType.SESSION_START))
        store.write(event(at = MINUTE / 2, type = AuditType.SESSION_START))
        settle()

        assertThat(db.dao().count()).isEqualTo(2)
    }

    @Test
    fun `an event whose insert failed does not suppress its repeat`() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            "CREATE TRIGGER refuse BEFORE INSERT ON audit_events " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        store.write(event(at = 0, type = AuditType.CERTIFICATE_CHANGED))
        settle()
        assertThat(db.dao().count()).isEqualTo(0)

        sqlite.execSQL("DROP TRIGGER refuse")
        store.write(event(at = MINUTE, type = AuditType.CERTIFICATE_CHANGED))
        settle()

        assertThat(db.dao().count()).isEqualTo(1)
    }

    @Test
    fun `a burst larger than the queue neither blocks nor throws and keeps the newest events`() {
        val burst = 1_000
        for (i in 0 until burst) store.write(event(at = i.toLong(), detail = "d$i"))

        settle()

        val details = store.entries().map { it.detail }
        assertThat(details).hasSize(256)
        assertThat(details.last()).isEqualTo("d${burst - 1}")
    }

    @Test
    fun `a clock far in the future does not drop older entries early`() {
        val wallClock = 200 * DAY
        val clamped = AuditStore(db.dao(), scope, dispatcher, clock = { wallClock })
        clamped.write(event(at = wallClock - 89 * DAY, detail = "recent"))
        clamped.write(event(at = wallClock + 1_000 * DAY, detail = "from the future"))
        settle()

        assertThat(clamped.entries().map { it.detail })
            .containsExactly("recent", "from the future")
            .inOrder()
    }

    @Test
    fun `a restart forgets the window and the event is stored again`() {
        store.write(event(at = 0, type = AuditType.TLS_FAILURE))
        settle()

        AuditStore(db.dao(), scope, dispatcher)
            .write(event(at = MINUTE, type = AuditType.TLS_FAILURE))
        settle()

        assertThat(db.dao().count()).isEqualTo(2)
    }

    @Test
    fun `only the newest 2000 entries are kept`() {
        for (i in 0..AuditStore.MAX_ENTRIES) {
            store.write(event(at = i.toLong(), detail = "d$i"))
            if (i % 100 == 0) settle()
        }
        settle()

        val details = store.entries().map { it.detail }
        assertThat(details).hasSize(AuditStore.MAX_ENTRIES)
        assertThat(details.first()).isEqualTo("d1")
        assertThat(details.last()).isEqualTo("d${AuditStore.MAX_ENTRIES}")
    }

    @Test
    fun `entries older than 90 days go when the next event arrives`() {
        val now = 200 * DAY
        store.write(event(at = now - 91 * DAY, detail = "old"))
        store.write(event(at = now - 89 * DAY, detail = "recent"))
        settle()
        assertThat(db.dao().count()).isEqualTo(2)

        store.write(event(at = now, detail = "new"))
        settle()

        assertThat(store.entries().map { it.detail }).containsExactly("recent", "new").inOrder()
    }

    @Test
    fun `erasing deletes everything and forgets the window`() =
        runTest(dispatcher) {
            store.write(event(at = 0, type = AuditType.TLS_FAILURE))
            scheduler.runCurrent()

            store.erase()

            assertThat(db.dao().count()).isEqualTo(0)
            store.write(event(at = MINUTE, type = AuditType.TLS_FAILURE))
            scheduler.runCurrent()
            assertThat(db.dao().count()).isEqualTo(1)
        }

    @Test
    fun `a failing database does not reach the caller and later events are still handled`() {
        val closed = newDatabase().also { it.close() }
        val failing = AuditStore(closed.dao(), scope, dispatcher)

        failing.write(event(at = 0, detail = "lost"))
        failing.write(event(at = 1, detail = "also lost"))
        store.write(event(at = 2, detail = "kept"))
        settle()

        assertThat(store.entries().map { it.detail }).containsExactly("kept")
    }
}

package io.github.magalz.borealis.settings.store

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.settings.catalog.SettingStore
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeBacking
import io.github.magalz.borealis.settings.testing.WorkerDispatcherRule
import io.github.magalz.borealis.settings.testing.eventually
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The ordering, rollback and snapshot rules of the repository, over backings in memory. */
class StoreSettingsRepositoryTest {
    @get:Rule val workers = WorkerDispatcherRule()

    private val prefs = FakeBacking(SettingStore.PREFS)
    private val dataStore = FakeBacking(SettingStore.DATASTORE)
    private val scope = CoroutineScope(workers.dispatcher)
    private val logged = CopyOnWriteArrayList<LogRecord>()
    private val repository = repositoryOf(SettingsSnapshot.DEFAULTS)

    private fun repositoryOf(initial: SettingsSnapshot) =
        StoreSettingsRepository(prefs, dataStore, scope, initial)

    @Before
    fun captureLog() {
        BorealisLog.initialize(
            listOf(
                object : LogSink {
                    override fun write(record: LogRecord) {
                        logged += record
                    }
                }
            ),
            minLevel = LogLevel.DEBUG,
        )
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
        scope.cancel()
    }

    @Test
    fun `set updates the snapshot and the backing of the setting`() =
        runBlocking<Unit> {
            repository.set(Settings.FRAME_RATE, "120")
            repository.set(Settings.VERBOSE_LOGGING, true)

            assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("120")
            assertThat(repository.snapshot.value[Settings.VERBOSE_LOGGING]).isTrue()
            assertThat(prefs.writes).containsExactly(Settings.FRAME_RATE to "120")
            assertThat(dataStore.writes).containsExactly(Settings.VERBOSE_LOGGING to true)
        }

    @Test
    fun `set stores a value that equals the default`() =
        runBlocking<Unit> {
            repository.set(Settings.GAMEPAD_MOTION_SENSORS, Settings.GAMEPAD_MOTION_SENSORS.default)

            assertThat(prefs.writes)
                .containsExactly(
                    Settings.GAMEPAD_MOTION_SENSORS to Settings.GAMEPAD_MOTION_SENSORS.default
                )
            assertThat(prefs.stored[Settings.GAMEPAD_MOTION_SENSORS]).isEqualTo(true)
        }

    @Test
    fun `clear removes the stored key and the setting reads its default again`() =
        runBlocking<Unit> {
            repository.set(Settings.FRAME_RATE, "120")

            repository.clear(Settings.FRAME_RATE, Settings.DEADZONE)

            assertThat(prefs.stored).doesNotContainKey(Settings.FRAME_RATE)
            assertThat(prefs.writes.takeLast(2))
                .containsExactly(Settings.FRAME_RATE to null, Settings.DEADZONE to null)
                .inOrder()
            assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("60")
        }

    @Test
    fun `the bitrate is automatic again after clear, and after set of null`() =
        runBlocking<Unit> {
            repository.set(Settings.BITRATE_KBPS, 25_000)
            assertThat(repository.snapshot.value.isBitrateAutomatic).isFalse()

            repository.clear(Settings.BITRATE_KBPS)

            assertThat(prefs.stored).doesNotContainKey(Settings.BITRATE_KBPS)
            assertThat(repository.snapshot.value.isBitrateAutomatic).isTrue()

            repository.set(Settings.BITRATE_KBPS, 30_000)
            repository.set(Settings.BITRATE_KBPS, null)

            assertThat(prefs.stored).doesNotContainKey(Settings.BITRATE_KBPS)
            assertThat(prefs.writes.last()).isEqualTo(Settings.BITRATE_KBPS to null)
            assertThat(repository.snapshot.value[Settings.BITRATE_KBPS]).isNull()
        }

    @Test
    fun `setNow updates the snapshot before it returns and stores in the background`() {
        val gate = CompletableDeferred<Unit>()
        prefs.gate = gate

        repository.setNow(Settings.FRAME_RATE, "90")

        assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("90")
        assertThat(prefs.writes).isEmpty()

        gate.complete(Unit)
        eventually { prefs.writes.isNotEmpty() }
        assertThat(prefs.writes).containsExactly(Settings.FRAME_RATE to "90")
        assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("90")
    }

    @Test
    fun `setNow stores a value that equals the default`() {
        repository.setNow(Settings.STRETCH_VIDEO, false)

        eventually { prefs.writes.isNotEmpty() }
        assertThat(prefs.writes).containsExactly(Settings.STRETCH_VIDEO to false)
    }

    @Test
    fun `writes are applied in the order of the calls`() =
        runBlocking<Unit> {
            repository.setNow(Settings.FRAME_RATE, "30")
            repository.setNow(Settings.FRAME_RATE, "60")
            repository.setNow(Settings.RESOLUTION, "1920x1080")
            repository.set(Settings.FRAME_RATE, "120")

            assertThat(prefs.writes)
                .containsExactly(
                    Settings.FRAME_RATE to "30",
                    Settings.FRAME_RATE to "60",
                    Settings.RESOLUTION to "1920x1080",
                    Settings.FRAME_RATE to "120",
                )
                .inOrder()
            assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("120")
        }

    @Test
    fun `a failed set keeps the previous value and never throws`() =
        runBlocking<Unit> {
            repository.set(Settings.FRAME_RATE, "120")
            prefs.failWrites = true

            repository.set(Settings.FRAME_RATE, "30")

            assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("120")
            assertThat(prefs.stored[Settings.FRAME_RATE]).isEqualTo("120")
        }

    @Test
    fun `a failed write is logged without the value`() =
        runBlocking<Unit> {
            prefs.failWrites = true

            repository.set(Settings.RESOLUTION, "7680x4320")

            val record = logged.single { it.level == LogLevel.WARN }
            assertThat(record.fields).contains("key" to Settings.RESOLUTION.key)
            assertThat(record.message + record.fields + record.stackTrace.orEmpty())
                .doesNotContain("7680x4320")
        }

    @Test
    fun `a failed setNow rolls the key back to the stored value`() {
        prefs.stored[Settings.FRAME_RATE] = "120"
        val seeded = repositoryOf(SettingsSnapshot.DEFAULTS.with(Settings.FRAME_RATE, "120"))
        val gate = CompletableDeferred<Unit>()
        prefs.gate = gate
        prefs.failWrites = true

        seeded.setNow(Settings.FRAME_RATE, "30")
        assertThat(seeded.snapshot.value[Settings.FRAME_RATE]).isEqualTo("30")
        gate.complete(Unit)

        eventually { seeded.snapshot.value[Settings.FRAME_RATE] == "120" }
        assertThat(logged.count { it.level == LogLevel.WARN }).isEqualTo(1)
    }

    @Test
    fun `a failed write of one key does not disturb another key that is pending`() {
        val gate = CompletableDeferred<Unit>()
        prefs.gate = gate
        prefs.failWrites = true

        repository.setNow(Settings.FRAME_RATE, "30")
        repository.setNow(Settings.RESOLUTION, "1920x1080")
        gate.complete(Unit)

        eventually {
            repository.snapshot.value[Settings.FRAME_RATE] == "60" &&
                repository.snapshot.value[Settings.RESOLUTION] == "1280x720"
        }
    }

    @Test
    fun `a change that bypasses the repository updates the snapshot`() {
        prefs.changeElsewhere(Settings.RESOLUTION, "1920x1080")

        assertThat(repository.snapshot.value[Settings.RESOLUTION]).isEqualTo("1920x1080")

        prefs.changeElsewhere(Settings.RESOLUTION, null)

        assertThat(repository.snapshot.value[Settings.RESOLUTION]).isEqualTo("1280x720")
    }

    @Test
    fun `an outside change does not undo a write that is still pending`() {
        val gate = CompletableDeferred<Unit>()
        prefs.gate = gate

        repository.setNow(Settings.FRAME_RATE, "90")
        prefs.changeElsewhere(Settings.RESOLUTION, "1920x1080")

        assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("90")
        assertThat(repository.snapshot.value[Settings.RESOLUTION]).isEqualTo("1920x1080")
        gate.complete(Unit)
    }

    @Test
    fun `a stored default is the same snapshot as an absent key, but it is stored`() =
        runBlocking<Unit> {
            val before = repository.snapshot.value

            repository.set(Settings.STRETCH_VIDEO, false)

            assertThat(repository.snapshot.value).isEqualTo(before)
            assertThat(prefs.stored[Settings.STRETCH_VIDEO]).isEqualTo(false)
        }

    @Test
    fun `a chosen background is stored in DataStore under its id and read back at the next start`() =
        runBlocking<Unit> {
            repository.set(Settings.BACKGROUND, "starfield")

            assertThat(repository.snapshot.value[Settings.BACKGROUND]).isEqualTo("starfield")
            assertThat(dataStore.writes).containsExactly(Settings.BACKGROUND to "starfield")
            assertThat(prefs.writes).isEmpty()
            val nextStart = StoreSettingsRepository.create(prefs, dataStore, scope)
            assertThat(nextStart.snapshot.value[Settings.BACKGROUND]).isEqualTo("starfield")
        }

    @Test
    fun `an unset background reads as the Aurora curtains`() {
        assertThat(repository.snapshot.value[Settings.BACKGROUND]).isEqualTo("aurora_curtains")
    }

    @Test
    fun `observe gives the value now and each change, without repeats`() {
        val seen = CopyOnWriteArrayList<String>()
        val collector = scope.launch {
            repository.observe(Settings.FRAME_RATE).collect { seen += it }
        }
        eventually { seen.size == 1 }

        repository.setNow(Settings.FRAME_RATE, "60")
        repository.setNow(Settings.FRAME_RATE, "120")
        repository.setNow(Settings.RESOLUTION, "1920x1080")
        eventually { seen.size == 2 }
        repository.setNow(Settings.FRAME_RATE, "30")
        eventually { seen.size == 3 }
        collector.cancel()

        assertThat(seen).containsExactly("60", "120", "30").inOrder()
    }

    @Test
    fun `the snapshot flow emits a new snapshot for a change`() =
        runBlocking<Unit> {
            repository.set(Settings.DEADZONE, 12)

            val changed =
                withTimeout(5_000) { repository.snapshot.first { it[Settings.DEADZONE] == 12 } }

            assertThat(changed[Settings.DEADZONE]).isEqualTo(12)
        }

    @Test
    fun `the publish hook sees each new snapshot before the flow shows it`() =
        runBlocking<Unit> {
            val inHook = CopyOnWriteArrayList<Pair<String, String>>()
            repository.beforePublish = { next ->
                inHook +=
                    next[Settings.FRAME_RATE] to repository.snapshot.value[Settings.FRAME_RATE]
            }

            repository.set(Settings.FRAME_RATE, "90")
            prefs.changeElsewhere(Settings.FRAME_RATE, "120")

            assertThat(inHook).containsExactly("90" to "60", "120" to "90").inOrder()
        }

    @Test
    fun `the publish hook is not called when nothing changes, and sees a rollback`() {
        val inHook = CopyOnWriteArrayList<String>()
        repository.beforePublish = { next -> inHook += next[Settings.FRAME_RATE] }
        val gate = CompletableDeferred<Unit>()
        prefs.gate = gate
        prefs.failWrites = true

        repository.setNow(Settings.FRAME_RATE, "60")
        repository.setNow(Settings.FRAME_RATE, "30")
        gate.complete(Unit)

        eventually { repository.snapshot.value[Settings.FRAME_RATE] == "60" && inHook.size == 2 }
        assertThat(inHook).containsExactly("30", "60").inOrder()
    }

    @Test
    fun `an outside write that lands right after our commit is not masked by our value`() =
        runBlocking<Unit> {
            prefs.onWritten = { prefs.changeElsewhere(Settings.FRAME_RATE, "120") }

            repository.set(Settings.FRAME_RATE, "90")

            assertThat(repository.snapshot.value[Settings.FRAME_RATE]).isEqualTo("120")
        }

    @Test
    fun `an outside write that lands right after a setNow commit wins as well`() {
        prefs.onWritten = { prefs.changeElsewhere(Settings.FRAME_RATE, "120") }

        repository.setNow(Settings.FRAME_RATE, "90")

        eventually { repository.snapshot.value[Settings.FRAME_RATE] == "120" }
    }

    @Test
    fun `a stale emission of the DataStore cannot flip a switch while a write of it is in flight`() {
        val gate = CompletableDeferred<Unit>()
        dataStore.gate = gate
        val published = CopyOnWriteArrayList<Boolean>()
        repository.beforePublish = { published += it[Settings.MASK_ADDRESSES] }

        repository.setNow(Settings.MASK_ADDRESSES, true)
        dataStore.changeElsewhere(Settings.MASK_ADDRESSES, null)
        dataStore.changeElsewhere(Settings.MASK_ADDRESSES, false)

        assertThat(repository.snapshot.value[Settings.MASK_ADDRESSES]).isTrue()
        gate.complete(Unit)
        eventually { dataStore.writes.isNotEmpty() }
        eventually { repository.snapshot.value[Settings.MASK_ADDRESSES] }
        assertThat(published).containsExactly(true)
    }

    @Test
    fun `a store that cannot be read at start counts as empty and is logged by class only`() {
        prefs.failReads = true

        val primed = StoreSettingsRepository.create(prefs, dataStore, scope)

        assertThat(primed.snapshot.value).isEqualTo(SettingsSnapshot.DEFAULTS)
        val record = logged.single { it.level == LogLevel.WARN }
        assertThat(record.fields).contains("error" to "IOException")
        assertThat(record.message + record.fields).doesNotContain("read-detail")
    }
}

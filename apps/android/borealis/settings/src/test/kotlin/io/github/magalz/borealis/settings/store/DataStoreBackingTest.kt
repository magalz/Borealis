package io.github.magalz.borealis.settings.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.testing.WorkerDispatcherRule
import io.github.magalz.borealis.settings.testing.eventually
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The DataStore backing of the logging switches, the background and the look settings, and the
 * migration of the switches from the default SharedPreferences file. Each test gives its file to
 * one DataStore at a time: a second live DataStore on the same file is an error.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class DataStoreBackingTest {
    @get:Rule val folder = TemporaryFolder()
    @get:Rule val workers = WorkerDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    /**
     * Every setting that the DataStore holds: the logging switches, the background and the look.
     */
    private val datastoreSettings =
        listOf<Setting<*>>(
            Settings.VERBOSE_LOGGING,
            Settings.MASK_ADDRESSES,
            Settings.BACKGROUND,
            Settings.GLASS_TINT,
            Settings.GLASS_BLUR,
            Settings.REDUCE_MOTION,
            Settings.TRANSPARENCY,
            Settings.THICK_FOCUS,
            Settings.TEXT_SIZE,
            Settings.HIGH_CONTRAST,
            Settings.COLOUR_VISION,
            Settings.FONT,
            Settings.ACCENT,
            Settings.BACKGROUND_INTENSITY,
            Settings.CARD_DENSITY,
            Settings.TEXT_SPACING,
        )

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        file = File(folder.root, "datastore/${DataStoreBacking.FILE_NAME}.preferences_pb")
    }

    @After
    fun tearDown() {
        scopes.forEach { runBlocking { it.coroutineContext[Job]!!.cancelAndJoin() } }
    }

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + workers.dispatcher).also { scopes += it }

    private fun backing(scope: CoroutineScope = newScope()) =
        DataStoreBacking(DataStoreBacking.create(preferences, scope, file), scope)

    @Test
    fun `an empty store reads as absent for every DataStore setting`() =
        runBlocking<Unit> {
            val read = backing().read()

            assertThat(read.keys).containsExactlyElementsIn(datastoreSettings)
            assertThat(read.values.filterNotNull()).isEmpty()
        }

    @Test
    fun `a switch reads back the value that was written, also when it equals the default`() =
        runBlocking<Unit> {
            val backing = backing()

            backing.write(Settings.VERBOSE_LOGGING, true)
            backing.write(Settings.MASK_ADDRESSES, false)

            assertThat(backing.read())
                .containsExactlyEntriesIn(
                    datastoreSettings.associateWith<Setting<*>, Any?> { null } +
                        mapOf(Settings.VERBOSE_LOGGING to true, Settings.MASK_ADDRESSES to false)
                )
        }

    @Test
    fun `the background reads back the id that was written, also after a new DataStore`() =
        runBlocking<Unit> {
            val first = newScope()
            backing(first).write(Settings.BACKGROUND, "northern_lights")
            first.coroutineContext[Job]!!.cancelAndJoin()

            assertThat(backing().read()[Settings.BACKGROUND]).isEqualTo("northern_lights")
        }

    @Test
    fun `every id of the font, accent, intensity, density and spacing reads back as written`() =
        runBlocking<Unit> {
            val ids =
                mapOf(
                    Settings.FONT to
                        listOf(
                            "plex",
                            "atkinson_next",
                            "luciole",
                            "lexend",
                            "andika",
                            "b612",
                            "system",
                        ),
                    Settings.ACCENT to listOf("violet", "teal", "white"),
                    Settings.BACKGROUND_INTENSITY to listOf("100", "75", "50", "25"),
                    Settings.CARD_DENSITY to listOf("comfortable", "compact", "dense"),
                    Settings.TEXT_SPACING to listOf("normal", "wide", "wider"),
                )
            val backing = backing()

            for ((setting, values) in ids) {
                for (id in values) {
                    backing.write(setting, id)
                    assertThat(backing.read()[setting]).isEqualTo(id)
                }
            }
        }

    @Test
    fun `a null removes the key`() =
        runBlocking<Unit> {
            val backing = backing()
            backing.write(Settings.MASK_ADDRESSES, true)

            backing.write(Settings.MASK_ADDRESSES, null)

            assertThat(backing.read()[Settings.MASK_ADDRESSES]).isNull()
        }

    @Test
    fun `a write survives a new DataStore on the same file`() =
        runBlocking<Unit> {
            val first = newScope()
            backing(first).write(Settings.VERBOSE_LOGGING, true)
            first.coroutineContext[Job]!!.cancelAndJoin()

            assertThat(backing().read()[Settings.VERBOSE_LOGGING]).isEqualTo(true)
        }

    @Test
    fun `the migration keeps the stored switches and removes their old keys`() =
        runBlocking<Unit> {
            preferences
                .edit()
                .putBoolean(Settings.VERBOSE_LOGGING.key, true)
                .putBoolean(Settings.MASK_ADDRESSES.key, false)
                .putString(Settings.FRAME_RATE.key, "120")
                .commit()

            val read = backing().read()

            assertThat(read[Settings.VERBOSE_LOGGING]).isEqualTo(true)
            assertThat(read[Settings.MASK_ADDRESSES]).isEqualTo(false)
            assertThat(preferences.contains(Settings.VERBOSE_LOGGING.key)).isFalse()
            assertThat(preferences.contains(Settings.MASK_ADDRESSES.key)).isFalse()
            assertThat(preferences.getString(Settings.FRAME_RATE.key, null)).isEqualTo("120")
        }

    @Test
    fun `running the migration twice is harmless`() =
        runBlocking<Unit> {
            preferences.edit().putBoolean(Settings.MASK_ADDRESSES.key, true).commit()
            val first = newScope()
            val once = backing(first).read()
            first.coroutineContext[Job]!!.cancelAndJoin()

            val twice = backing().read()

            assertThat(once[Settings.MASK_ADDRESSES]).isEqualTo(true)
            assertThat(twice).isEqualTo(once)
            assertThat(preferences.contains(Settings.MASK_ADDRESSES.key)).isFalse()
        }

    @Test
    fun `a change that the repository did not make is reported`() {
        val scope = newScope()
        val store = DataStoreBacking.create(preferences, scope, file)
        val reported = java.util.concurrent.CopyOnWriteArrayList<Map<Setting<*>, Any?>>()
        DataStoreBacking(store, scope).observe { reported += it }
        eventually { reported.isNotEmpty() }

        runBlocking {
            store.edit { it[booleanPreferencesKey(Settings.VERBOSE_LOGGING.key)] = true }
        }

        eventually { reported.any { it[Settings.VERBOSE_LOGGING] == true } }
    }

    @Test
    fun `a write that the store refuses throws`() {
        val scope = newScope()
        val real = DataStoreBacking.create(preferences, scope, file)
        val refusing =
            object : DataStore<Preferences> by real {
                override suspend fun updateData(
                    transform: suspend (t: Preferences) -> Preferences
                ): Preferences = throw IOException("disk full")
            }

        assertThrows(IOException::class.java) {
            runBlocking { DataStoreBacking(refusing, scope).write(Settings.VERBOSE_LOGGING, true) }
        }
    }

    @Test
    fun `a corrupt file is replaced by an empty one and logged by class only`() =
        runBlocking<Unit> {
            val logged = CopyOnWriteArrayList<LogRecord>()
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
            file.parentFile!!.mkdirs()
            file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x01))

            val read = backing().read()

            BorealisLog.shutdown()
            assertThat(read.values.filterNotNull()).isEmpty()
            val record = logged.single { it.level == LogLevel.WARN }
            assertThat(record.message).contains("replaced")
            assertThat(record.fields.map { it.first }).contains("error")
        }

    @Test
    fun `observing survives a read error and goes on with the next read`() {
        val scope = newScope()
        val real = DataStoreBacking.create(preferences, scope, file)
        var failures = 1
        val flaky =
            object : DataStore<Preferences> by real {
                override val data: Flow<Preferences> = flow {
                    if (failures-- > 0) throw IOException("transient")
                    emitAll(real.data)
                }
            }
        val reported = CopyOnWriteArrayList<Map<Setting<*>, Any?>>()

        DataStoreBacking(flaky, scope, retryDelayMillis = 10).observe { reported += it }

        eventually { reported.isNotEmpty() }
        runBlocking { real.edit { it[booleanPreferencesKey(Settings.MASK_ADDRESSES.key)] = true } }
        eventually { reported.any { it[Settings.MASK_ADDRESSES] == true } }
    }
}

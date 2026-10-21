package io.github.magalz.borealis.designsystem.background

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.Shadows.shadowOf

/** Turns the battery saver on or off, and tells the app the way the system does. */
internal fun setPowerSave(on: Boolean) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(on)
    context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    shadowOf(Looper.getMainLooper()).idle()
}

/** A lifecycle the test moves by hand, resumed to begin with. */
internal class TestLifecycle : LifecycleOwner {
    private val registry =
        LifecycleRegistry(this).also { it.currentState = Lifecycle.State.RESUMED }

    override val lifecycle: Lifecycle
        get() = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

/** Keeps what `BorealisLog` hands to its sinks. */
internal class RecordingLogSink : LogSink {
    val records = CopyOnWriteArrayList<LogRecord>()

    override fun write(record: LogRecord) {
        records += record
    }
}

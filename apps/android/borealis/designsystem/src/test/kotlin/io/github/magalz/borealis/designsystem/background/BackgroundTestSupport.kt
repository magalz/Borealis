package io.github.magalz.borealis.designsystem.background

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList

/** The system setting "Remove animations": the animator scale is zero. */
internal fun removeAnimations() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
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

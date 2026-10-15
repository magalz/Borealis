package io.github.magalz.borealis.library.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.StreamLauncher
import javax.inject.Inject

/**
 * The launcher activity of the Compose Library on phones, handhelds and TVs (ADR 0052). It does the
 * work the upstream `PcView` did at creation and on resume through [LegacyBridge], and hands the
 * streams to the upstream stream activity through [StreamLauncher].
 */
@AndroidEntryPoint(ComponentActivity::class)
class LibraryActivity : Hilt_LibraryActivity() {
    @Inject lateinit var streamLauncher: StreamLauncher
    @Inject lateinit var legacyBridge: LegacyBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibraryNavHost(
                onStartStream = { streamLauncher.start(this, it) },
                onOpenSettings = { legacyBridge.openStreamSettings(this) },
            )
        }
        // After the content: the bridge posts the GL renderer probe, which must not lose its
        // surface.
        legacyBridge.onLauncherCreated(this)
    }

    override fun onResume() {
        super.onResume()
        legacyBridge.onLauncherResumed(this)
    }
}

package io.github.magalz.borealis.library.shell

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.domain.LegacyBridge
import io.github.magalz.borealis.library.domain.StreamLauncher
import io.github.magalz.borealis.settings.appearance.rememberLooks
import io.github.magalz.borealis.settings.repository.SettingsRepository
import javax.inject.Inject

/**
 * The launcher activity of the Compose Library on phones, handhelds and TVs (ADR 0052). It does the
 * work the 12.2 host list did at creation and on resume through [LegacyBridge], and hands the
 * streams to the upstream stream activity through [StreamLauncher].
 */
@AndroidEntryPoint(ComponentActivity::class)
class LibraryActivity : Hilt_LibraryActivity() {
    @Inject lateinit var streamLauncher: StreamLauncher
    @Inject lateinit var legacyBridge: LegacyBridge
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var quitNotice: QuitNotice
    @Inject lateinit var resumeChoice: ResumeChoice

    private var request by mutableStateOf<LibraryLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity has already taken the request that started it: it is not replayed.
        if (savedInstanceState == null) request = LibraryLaunchRequest.fromExtras(intent.extras)
        // The UI is dark only: light icons whatever the system theme, which uiMode changes in
        // configChanges would never re-evaluate.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val looks = rememberLooks(settings)
            LibraryNavHost(
                onStartStream = { streamLauncher.start(this, it) },
                quitNotice = quitNotice,
                request = request,
                onRequestHandled = { request = null },
                backgroundStyle = looks.background,
                look = looks.look,
            )
        }
        // After the content: the bridge posts the GL renderer probe, which must not lose its
        // surface.
        legacyBridge.onLauncherCreated(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = LibraryLaunchRequest.fromExtras(intent.extras)
    }

    override fun onResume() {
        super.onResume()
        legacyBridge.onLauncherResumed(this)
    }

    override fun onDestroy() {
        // The choice of Try again is for the request that came with it: a Library that goes before
        // it handled the request must not leave it for a later shortcut.
        resumeChoice.clear()
        super.onDestroy()
    }
}

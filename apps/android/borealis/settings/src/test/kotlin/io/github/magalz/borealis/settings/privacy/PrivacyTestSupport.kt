package io.github.magalz.borealis.settings.privacy

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.diagnostics.audit.AuditEntry
import io.github.magalz.borealis.diagnostics.audit.AuditTrail
import io.github.magalz.borealis.diagnostics.bundle.ExportResult
import io.github.magalz.borealis.settings.testing.InGlassHost
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

const val PHONE = "w360dp-h640dp-xhdpi"
const val TV = "w960dp-h540dp-land-television-xhdpi"
const val HANDHELD = "w800dp-h480dp-land-hdpi"

/** Runs `Dispatchers.Main` (and so `viewModelScope`) on a test dispatcher, so time is virtual. */
class MainDispatcherRule(private val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** Privacy actions that do nothing, for a screen that is only looked at. */
internal fun noopPrivacyActions() =
    PrivacyActions({}, {}, {}, {}, {}, PrivacyDialogActions({}, {}, {}, { _: File, _: String -> }))

/**
 * Shows the Privacy screen of [formFactor] over a glass host of [style] under [look], with nothing
 * to do and nothing exported, as the shell of the Library draws it.
 */
fun ComposeContentTestRule.showPrivacy(
    formFactor: FormFactor = FormFactor.Phone,
    look: BorealisLook = BorealisLook(),
    style: BackgroundStyle = BackgroundStyle.FlowingLight,
) {
    setContent {
        InGlassHost(formFactor, look, style) {
            PrivacyScreenContent(
                PrivacyUiState(),
                SnackbarHostState(),
                noopPrivacyActions(),
                onBack = {},
            )
        }
    }
    waitForIdle()
}

/** Collects [flow] until the test ends, as a screen does, and runs the work that this starts. */
fun TestScope.subscribe(flow: Flow<*>): Job =
    backgroundScope
        .launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        .also { runCurrent() }

/**
 * Sets [content] and switches the window to the keyboard input mode, the way a D-pad or a gamepad
 * key does on a device. Clickables are focusable in this mode only, and the focus outline shows
 * only in it.
 */
fun ComposeContentTestRule.setContentInKeyboardMode(content: @Composable () -> Unit) {
    var manager: InputModeManager? = null
    setContent {
        manager = LocalInputModeManager.current
        content()
    }
    runOnIdle { checkNotNull(manager).requestInputMode(InputMode.Keyboard) }
    waitForIdle()
}

/** An export that answers [result], after [gate] opens when there is one. */
class FakeDiagnosticsExport(
    var result: ExportResult,
    private val gate: CompletableDeferred<Unit>? = null,
) : DiagnosticsExport {
    var calls = 0
        private set

    override suspend fun export(): ExportResult {
        calls++
        gate?.await()
        return result
    }
}

/** An audit trail that holds no entries and answers [eraseResult] to an erase. */
class FakeAuditTrail(var eraseResult: Boolean = true) : AuditTrail {
    var eraseCalls = 0
        private set

    override fun observe(): Flow<List<AuditEntry>> = flowOf(emptyList())

    override suspend fun erase(): Boolean {
        eraseCalls++
        return eraseResult
    }
}

/** A bundle of [size] bytes in [dir]. */
fun bundleFile(dir: File, size: Int = SAMPLE_SIZE): File =
    File(dir, "borealis-diagnostics-20261016-224700.zip").apply { writeBytes(ByteArray(size)) }

fun sampleShare(bundle: File) =
    ExportResult.Share(
        Intent.createChooser(Intent(Intent.ACTION_SEND), "Share diagnostics"),
        Uri.parse("content://borealis/diagnostics.zip"),
        bundle,
    )

fun sampleSaved(bundle: File) =
    ExportResult.Saved(
        location = "/sdcard/Download/${bundle.name}",
        adbPullCommand = "adb pull /sdcard/Download/${bundle.name}",
        chooser = Intent.createChooser(Intent(Intent.ACTION_SEND), "Share diagnostics"),
        bundle = bundle,
    )

const val SAMPLE_SIZE = 1_536_000

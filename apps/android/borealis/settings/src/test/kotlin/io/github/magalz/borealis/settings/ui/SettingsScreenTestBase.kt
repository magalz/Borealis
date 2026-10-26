package io.github.magalz.borealis.settings.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeDeviceFactsSource
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.robolectric.Shadows.shadowOf

/**
 * What the screen tests share: the real ViewModel over fakes, and the route as the Library will
 * host it, with the placeholders and the recorded callbacks of a test.
 */
open class SettingsScreenTestBase {
    @get:Rule val composeRule = createComposeRule()

    protected var repository = FakeSettingsRepository()
    protected val options = fakeOptions()
    protected val facts = FakeDeviceFactsSource()
    protected var resets = 0
    protected var backs = 0
    protected var privacyOpens = 0
    protected val openedLinks = mutableListOf<String>()
    protected var browserAvailable = true
    protected var licenses: String? = "NOTICE\n\nThe licence texts of the test"

    /** Makes the device a TV, as the leanback feature does on a real one. */
    protected fun asTelevision() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    protected fun stored(vararg values: Pair<Setting<*>, Any?>) {
        repository = FakeSettingsRepository(SettingsSnapshot.fromStored(values.toMap()))
    }

    protected fun testAbout(): AboutSource =
        AboutSource(
            versionName = "9.8.7",
            commit = "abc1234",
            openLink = {
                openedLinks += it
                browserAvailable
            },
            loadLicenses = { licenses },
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    protected open fun viewModel(): SettingsViewModel =
        SettingsViewModel(repository, options, facts, { resets++ }, UnconfinedTestDispatcher())

    /**
     * Shows the screen on [backdrop], which is the page colour the window has in the app: the
     * screen leaves its background to the shell.
     */
    protected fun show(
        about: AboutSource? = testAbout(),
        keyboard: Boolean = false,
        initialGroup: SettingsGroupId = SettingsGroupId.STREAM,
        backdrop: @Composable (content: @Composable () -> Unit) -> Unit = { OnPage(it) },
    ) {
        val viewModel = viewModel()
        val content: @Composable () -> Unit = {
            backdrop {
                SettingsRoute(
                    onBack = { backs++ },
                    onOpenPrivacy = { privacyOpens++ },
                    privacyPane = { Text("Privacy pane placeholder") },
                    initialGroup = initialGroup,
                    about = about ?: rememberAboutSource(),
                    viewModel = viewModel,
                )
            }
        }
        if (keyboard) composeRule.setContentInKeyboardMode(content)
        else composeRule.setContent(content)
        composeRule.waitForIdle()
    }

    /** The row with [title], scrolled into view. */
    protected fun row(title: String): SemanticsNodeInteraction =
        composeRule.onNodeWithText(title).also { it.performScrollTo() }

    /** The entry [label] of the open choice sheet. */
    protected fun sheetOption(label: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasText(label) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )

    /** Moves the slider of the row with [title] to [value], as a screen reader does. */
    protected fun setProgress(title: String, value: Float) {
        row(title).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        composeRule.waitForIdle()
    }

    protected val snapshot: SettingsSnapshot
        get() = repository.snapshot.value
}

package io.github.magalz.borealis.support

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.ShortcutHelper
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.logging.Level
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow

/**
 * The branches of `TvChannelHelper` that `TvChannelCharacterizationTest` leaves open: the request
 * to show the channel on a device that is not a TV, what a host without an id finds, and which
 * program of an app the lookup takes when the provider has several.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [TvChannelCharacterizationTest.RecordingTvInputManager::class])
class TvChannelBranchesCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val tv =
        Robolectric.setupContentProvider(FakeTvProvider::class.java, TvContract.AUTHORITY)

    private val tvInput = Shadow.newInstanceOf(TvInputManager::class.java)
    private val shown: TvChannelCharacterizationTest.RecordingTvInputManager =
        Shadow.extract(tvInput)
    private val helper = ShortcutHelper(ServicesContext(app, Context.TV_INPUT_SERVICE to tvInput))
    private val computer =
        ComputerDetails().also {
            it.uuid = "uuid-1"
            it.name = "Den PC"
        }
    private val hades = NvApp("Hades II", 42, true)

    @Before
    fun theDeviceIsATv() {
        setTv(true)
    }

    private fun setTv(isTv: Boolean) =
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, isTv)

    private fun deletedPrograms() = log.messages(Level.INFO).count { it == PROGRAM_DELETED }

    @Test
    fun `an activity on a device that is not a TV asks for nothing and looks nothing up`() {
        setTv(false)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        ShortcutHelper(activity).createHostShortcut(computer, false, true)

        assertThat(shadowOf(activity).nextStartedActivityForResult).isNull()
        assertThat(tv.queries).isEmpty()
    }

    @Test
    fun `a host that has no id finds no channel, and no error, when the provider has none`() {
        val noId = ComputerDetails().also { it.name = "Den PC" }

        helper.disableComputerShortcut(noId, "PC deleted")
        helper.disableAppShortcut(noId, hades, "App hidden")

        assertThat(tv.queries)
            .containsExactly(TvContract.Channels.CONTENT_URI, TvContract.Channels.CONTENT_URI)
        assertThat(tv.deletes).isEmpty()
    }

    @Test
    fun `a provider that gives no cursor for the programs has no program for the app`() {
        val channel = tv.seedChannel("uuid-1")
        tv.seedProgram(channel, "42", browsable = 1)
        tv.programQueryReturnsNull = true

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.deletes).isEmpty()
        assertThat(tv.updates.map { it.first }).containsExactly(TvContract.buildChannelUri(channel))
        assertThat(tv.inserts.map { it.first })
            .containsExactly(TvContract.PreviewPrograms.CONTENT_URI)
    }

    @Test
    fun `a program that the user removed is deleted and the one of the app after it is updated`() {
        val channel = tv.seedChannel("uuid-1")
        val removed = tv.seedProgram(channel, "42", browsable = 0)
        val onHome = tv.seedProgram(channel, "42", browsable = 1)

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.deletes).containsExactly(TvContract.buildPreviewProgramUri(removed))
        assertThat(tv.updates.map { it.first })
            .containsExactly(
                TvContract.buildChannelUri(channel),
                TvContract.buildPreviewProgramUri(onHome),
            )
            .inOrder()
        assertThat(tv.inserts).isEmpty()
        assertThat(deletedPrograms()).isEqualTo(1)
        assertThat(shown.requested).isEmpty()
    }

    @Test
    fun `the first program of the app that is on the home screen is updated, the ones after it stay`() {
        val channel = tv.seedChannel("uuid-1")
        val onHome = tv.seedProgram(channel, "42", browsable = 1)
        tv.seedProgram(channel, "42", browsable = 0)

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.deletes).isEmpty()
        assertThat(tv.updates.map { it.first })
            .containsExactly(
                TvContract.buildChannelUri(channel),
                TvContract.buildPreviewProgramUri(onHome),
            )
            .inOrder()
        assertThat(tv.inserts).isEmpty()
        assertThat(deletedPrograms()).isEqualTo(0)
    }

    @Test
    fun `every program of the app that the user removed is deleted before the program is added`() {
        val channel = tv.seedChannel("uuid-1")
        val first = tv.seedProgram(channel, "42", browsable = 0)
        val second = tv.seedProgram(channel, "42", browsable = 0)

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.deletes)
            .containsExactly(
                TvContract.buildPreviewProgramUri(first),
                TvContract.buildPreviewProgramUri(second),
            )
            .inOrder()
        assertThat(tv.inserts.map { it.first })
            .containsExactly(TvContract.PreviewPrograms.CONTENT_URI)
        assertThat(deletedPrograms()).isEqualTo(2)
    }

    @Test
    fun `disabling an app deletes the program that was removed from the home screen and then the one on it`() {
        val channel = tv.seedChannel("uuid-1")
        val removed = tv.seedProgram(channel, "42", browsable = 0)
        val onHome = tv.seedProgram(channel, "42", browsable = 1)

        helper.disableAppShortcut(computer, hades, "App hidden")

        assertThat(tv.deletes)
            .containsExactly(
                TvContract.buildPreviewProgramUri(removed),
                TvContract.buildPreviewProgramUri(onHome),
            )
            .inOrder()
    }

    @Test
    fun `a program that the provider gives no URI for is announced all the same`() {
        val channel = tv.seedChannel("uuid-1")
        tv.insertReturnsNull = true

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.inserts.map { it.first })
            .containsExactly(TvContract.PreviewPrograms.CONTENT_URI)
        assertThat(shown.requested).containsExactly(TvContract.buildChannelUri(channel))
    }

    private companion object {
        const val PROGRAM_DELETED = "Preview program has been deleted"
    }
}

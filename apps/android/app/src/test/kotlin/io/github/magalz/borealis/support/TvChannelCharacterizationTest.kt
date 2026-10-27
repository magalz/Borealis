package io.github.magalz.borealis.support

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.PosterContentProvider
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.ServerHelper
import com.limelight.utils.ShortcutHelper
import io.github.magalz.borealis.testing.LimeLogCapture
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.logging.Level
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/**
 * The TV channel of a PC and the preview programs of its apps. `TvChannelHelper` is reached through
 * `ShortcutHelper`, which `Game` and the Library use; the TV provider of the system is a fake that
 * keeps its rows and records what the helper did to them.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [TvChannelCharacterizationTest.RecordingTvInputManager::class])
class TvChannelCharacterizationTest {
    @get:Rule val log = LimeLogCapture()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val tv =
        Robolectric.setupContentProvider(FakeTvProvider::class.java, TvContract.AUTHORITY)

    // The TV input service is not there under Robolectric: the requests for a channel go to this
    // one.
    private val tvInput = Shadow.newInstanceOf(TvInputManager::class.java)
    private val shown: RecordingTvInputManager = Shadow.extract(tvInput)
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

    private fun valuesOf(values: ContentValues): Map<String, Any?> =
        values.valueSet().associate { it.key to it.value }

    /** Receives what the helper writes as the logo of the channel that the next insert makes. */
    private fun logoOf(channelId: Long): ByteArrayOutputStream =
        ByteArrayOutputStream().also {
            shadowOf(app.contentResolver)
                .registerOutputStream(TvContract.buildChannelLogoUri(channelId), it)
        }

    private fun pcIntentUri() =
        ServerHelper.createPcShortcutIntent(app, computer).toUri(Intent.URI_INTENT_SCHEME)

    private fun appIntentUri() =
        ServerHelper.createAppShortcutIntent(app, computer, hades).toUri(Intent.URI_INTENT_SCHEME)

    @Test
    fun `the intent that a channel carries is the URI of the shortcut intent`() {
        assertThat(pcIntentUri())
            .isEqualTo(
                "intent:#Intent;component=${app.packageName}/com.limelight.ShortcutTrampoline;" +
                    "S.Name=Den%20PC;S.UUID=uuid-1;end"
            )
    }

    @Test
    fun `a host that was newly paired gets a preview channel named after it`() {
        helper.createHostShortcut(computer, false, true)

        val (uri, values) = tv.inserts.single()
        assertThat(uri).isEqualTo(TvContract.Channels.CONTENT_URI)
        assertThat(valuesOf(values))
            .containsExactly(
                "type",
                TvContract.Channels.TYPE_PREVIEW,
                "display_name",
                "Den PC",
                "internal_provider_id",
                "uuid-1",
                "app_link_intent_uri",
                pcIntentUri(),
            )
        assertThat(tv.updates).isEmpty()
    }

    @Test
    fun `the channel is looked up in the provider by the id of the host before it is made`() {
        helper.createHostShortcut(computer, false, true)

        assertThat(tv.queries).containsExactly(TvContract.Channels.CONTENT_URI)
    }

    @Test
    fun `a new channel gets the channel icon, as a square of the logo width, in PNG`() {
        val logo = logoOf(FakeTvProvider.FIRST_ID)

        helper.createHostShortcut(computer, false, true)

        // The signature of a PNG file.
        assertThat(logo.toByteArray().take(PNG_SIGNATURE.size)).isEqualTo(PNG_SIGNATURE)
        val bitmap = BitmapFactory.decodeByteArray(logo.toByteArray(), 0, logo.size())
        val width = app.resources.getDimensionPixelSize(R.dimen.tv_channel_logo_width)
        assertThat(width).isGreaterThan(0)
        assertThat(bitmap.width).isEqualTo(width)
        // BOREALIS: the height is read from the width dimension too (found in the Sprint 12 audit).
        assertThat(bitmap.height).isEqualTo(width)
    }

    @Test
    fun `a channel that exists is updated and keeps its icon`() {
        val id = tv.seedChannel("uuid-1")
        val logo = logoOf(id)

        helper.createHostShortcut(computer, false, true)

        val (uri, values) = tv.updates.single()
        assertThat(uri).isEqualTo(TvContract.buildChannelUri(id))
        assertThat(valuesOf(values)["display_name"]).isEqualTo("Den PC")
        assertThat(tv.inserts).isEmpty()
        assertThat(logo.size()).isEqualTo(0)
    }

    @Test
    fun `the channel of another host is not the channel of this one`() {
        tv.seedChannel("uuid-2")

        helper.createHostShortcut(computer, false, true)

        assertThat(tv.updates).isEmpty()
        assertThat(tv.inserts).hasSize(1)
    }

    @Test
    fun `a channel whose host id is not set is not the channel of any host`() {
        tv.seedChannel(null)

        helper.createHostShortcut(computer, false, true)

        assertThat(tv.inserts).hasSize(1)
    }

    @Test
    fun `a cursor that the provider does not give is no channel`() {
        tv.queryReturnsNull = true

        helper.createHostShortcut(computer, false, true)

        assertThat(tv.inserts).hasSize(1)
    }

    @Test
    fun `a host that was not newly paired has no channel made`() {
        helper.createHostShortcut(computer, false, false)
        helper.createHostShortcut(computer, true, false)

        assertThat(tv.queries).isEmpty()
        assertThat(tv.inserts).isEmpty()
    }

    @Test
    fun `a device that is not a TV never touches the TV provider`() {
        setTv(false)

        helper.createHostShortcut(computer, false, true)
        helper.reportGameLaunched(computer, hades)
        helper.disableComputerShortcut(computer, "PC deleted")
        helper.disableAppShortcut(computer, hades, "App hidden")

        assertThat(tv.queries).isEmpty()
        assertThat(tv.inserts).isEmpty()
        assertThat(tv.updates).isEmpty()
        assertThat(tv.deletes).isEmpty()
    }

    @Test
    fun `a provider that does not know channels, as on HarmonyOS, leaves no channel and no icon`() {
        tv.insertFailure = IllegalArgumentException("Unknown URI")
        val logo = logoOf(FakeTvProvider.FIRST_ID)

        helper.createHostShortcut(computer, false, true)

        assertThat(tv.channels).isEmpty()
        assertThat(logo.size()).isEqualTo(0)
    }

    @Test
    fun `an insert that gives no URI leaves no icon`() {
        tv.insertReturnsNull = true
        val logo = logoOf(FakeTvProvider.FIRST_ID)

        helper.createHostShortcut(computer, false, true)

        assertThat(tv.inserts).hasSize(1)
        assertThat(logo.size()).isEqualTo(0)
    }

    @Test
    fun `an icon that cannot be stored is logged as a warning and does not fail the channel`() {
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = Unit

                override fun flush() = throw IOException("no space")
            }
        shadowOf(app.contentResolver)
            .registerOutputStream(TvContract.buildChannelLogoUri(FakeTvProvider.FIRST_ID), failing)

        helper.createHostShortcut(computer, false, true)

        assertThat(tv.channels).hasSize(1)
        assertThat(log.messages(Level.WARNING))
            .containsExactly("Failed to store the logo to the system content provider.")
    }

    @Test
    fun `an activity asks the TV to show the channel of a host that was newly paired`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        ShortcutHelper(activity).createHostShortcut(computer, false, true)

        val request = shadowOf(activity).nextStartedActivityForResult
        assertThat(request.requestCode).isEqualTo(0)
        assertThat(request.intent.action).isEqualTo(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE)
        assertThat(request.intent.extras?.keySet()).containsExactly(TvContract.EXTRA_CHANNEL_ID)
        assertThat(request.intent.extras?.get(TvContract.EXTRA_CHANNEL_ID))
            .isEqualTo(FakeTvProvider.FIRST_ID)
    }

    @Test
    fun `an application context cannot ask for the channel to be shown`() {
        helper.createHostShortcut(computer, false, true)

        assertThat(shadowOf(app).nextStartedActivity).isNull()
        assertThat(tv.channels).hasSize(1)
    }

    @Test
    fun `an activity does not ask for a channel that the provider refused to make`() {
        tv.insertFailure = IllegalArgumentException("Unknown URI")
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        ShortcutHelper(activity).createHostShortcut(computer, false, true)

        assertThat(shadowOf(activity).nextStartedActivityForResult).isNull()
    }

    @Test
    fun `a device that cannot show the channel, or that fails in another way, is not an error`() {
        for (failure in listOf(ActivityNotFoundException(), IllegalStateException("buggy"))) {
            val activity =
                Robolectric.buildActivity(FailingActivity::class.java).create().get().also {
                    it.failure = failure
                }

            ShortcutHelper(activity).createHostShortcut(computer, false, true)

            assertThat(activity.asked).isTrue()
        }
    }

    @Test
    fun `a launched game gets its channel and a preview program`() {
        helper.reportGameLaunched(computer, hades)

        assertThat(tv.inserts.map { it.first })
            .containsExactly(
                TvContract.Channels.CONTENT_URI,
                TvContract.PreviewPrograms.CONTENT_URI,
            )
            .inOrder()
        val (_, program) = tv.inserts[1]
        val values = valuesOf(program).toMutableMap()
        assertThat(values.remove("weight")).isInstanceOf(Int::class.javaObjectType)
        assertThat(values)
            .containsExactly(
                "channel_id",
                FakeTvProvider.FIRST_ID,
                "type",
                12,
                "title",
                "Hades II",
                "poster_art_aspect_ratio",
                5,
                "poster_art_uri",
                "content://${PosterContentProvider.AUTHORITY}/boxart/uuid-1/42",
                "intent_uri",
                appIntentUri(),
                "internal_provider_id",
                "42",
            )
    }

    @Test
    fun `the poster of a program is the box art URI of the poster provider`() {
        helper.reportGameLaunched(computer, hades)

        assertThat(valuesOf(tv.inserts[1].second)["poster_art_uri"])
            .isEqualTo(PosterContentProvider.createBoxArtUri("uuid-1", "42").toString())
    }

    @Test
    fun `a program that was added asks the TV to show the channel`() {
        helper.reportGameLaunched(computer, hades)

        assertThat(shown.requested)
            .containsExactly(TvContract.buildChannelUri(FakeTvProvider.FIRST_ID))
    }

    @Test
    fun `the weight of a program is the seconds since a fixed epoch, so that it grows with each run`() {
        val before = (System.currentTimeMillis() - EPOCH_MS) / 1000

        helper.reportGameLaunched(computer, hades)

        val after = (System.currentTimeMillis() - EPOCH_MS) / 1000
        val weight = valuesOf(tv.inserts[1].second)["weight"] as Int
        assertThat(weight.toLong()).isAtLeast(before)
        assertThat(weight.toLong()).isAtMost(after)
    }

    @Test
    fun `a program that exists is updated and the channel is not asked for again`() {
        val channel = tv.seedChannel("uuid-1")
        val program = tv.seedProgram(channel, "42", browsable = 1)

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.inserts).isEmpty()
        assertThat(tv.updates.map { it.first })
            .containsExactly(
                TvContract.buildChannelUri(channel),
                TvContract.buildPreviewProgramUri(program),
            )
            .inOrder()
        assertThat(valuesOf(tv.updates[1].second)["title"]).isEqualTo("Hades II")
        assertThat(shown.requested).isEmpty()
    }

    @Test
    fun `the programs are looked up in the channel of the host`() {
        val channel = tv.seedChannel("uuid-1")

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.queries)
            .containsExactly(
                TvContract.Channels.CONTENT_URI,
                TvContract.Channels.CONTENT_URI,
                TvContract.buildPreviewProgramsUriForChannel(channel),
            )
            .inOrder()
    }

    @Test
    fun `a program that the user removed from the home screen is deleted and added again`() {
        val channel = tv.seedChannel("uuid-1")
        val program = tv.seedProgram(channel, "42", browsable = 0)

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.deletes).containsExactly(TvContract.buildPreviewProgramUri(program))
        assertThat(tv.inserts.map { it.first })
            .containsExactly(TvContract.PreviewPrograms.CONTENT_URI)
        assertThat(log.messages(Level.INFO)).contains("Preview program has been deleted")
        assertThat(shown.requested).hasSize(1)
    }

    @Test
    fun `a program that cannot be deleted is warned about and added anyway`() {
        val channel = tv.seedChannel("uuid-1")
        tv.seedProgram(channel, "42", browsable = 0)
        tv.deleteReturnsZero = true

        helper.reportGameLaunched(computer, hades)

        assertThat(log.messages(Level.WARNING)).contains("Preview program has not been deleted")
        assertThat(tv.inserts.map { it.first })
            .containsExactly(TvContract.PreviewPrograms.CONTENT_URI)
    }

    @Test
    fun `the program of another app or another channel is not the program of this one`() {
        val channel = tv.seedChannel("uuid-1")
        tv.seedProgram(channel, "43", browsable = 1)
        val other = tv.seedChannel("uuid-2")
        tv.seedProgram(other, "42", browsable = 1)

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.inserts.map { it.first })
            .containsExactly(TvContract.PreviewPrograms.CONTENT_URI)
    }

    @Test
    fun `a program that the provider refuses is not announced`() {
        val channel = tv.seedChannel("uuid-1")
        tv.seedProgram(channel, "99", browsable = 1)
        tv.insertFailure = IllegalArgumentException("Unknown URI")

        helper.reportGameLaunched(computer, hades)

        assertThat(shown.requested).isEmpty()
    }

    @Test
    fun `a launched game has no program when the channel could not be made`() {
        tv.insertReturnsNull = true

        helper.reportGameLaunched(computer, hades)

        assertThat(tv.inserts.map { it.first }).containsExactly(TvContract.Channels.CONTENT_URI)
    }

    @Test
    fun `a host that has no id cannot be matched with the channels that exist`() {
        tv.seedChannel("uuid-2")

        assertThrows(RuntimeException::class.java) {
            helper.reportGameLaunched(ComputerDetails().also { it.name = "Den PC" }, hades)
        }
    }

    @Test
    fun `disabling a host deletes its channel`() {
        val channel = tv.seedChannel("uuid-1")
        tv.seedChannel("uuid-2")

        helper.disableComputerShortcut(computer, "PC deleted")

        assertThat(tv.deletes).containsExactly(TvContract.buildChannelUri(channel))
        assertThat(tv.channels).hasSize(1)
    }

    @Test
    fun `disabling a host that has no channel deletes nothing`() {
        tv.seedChannel("uuid-2")

        helper.disableComputerShortcut(computer, "PC deleted")

        assertThat(tv.deletes).isEmpty()
    }

    @Test
    fun `disabling an app deletes its program`() {
        val channel = tv.seedChannel("uuid-1")
        val program = tv.seedProgram(channel, "42", browsable = 1)
        tv.seedProgram(channel, "43", browsable = 1)

        helper.disableAppShortcut(computer, hades, "App hidden")

        assertThat(tv.deletes).containsExactly(TvContract.buildPreviewProgramUri(program))
    }

    @Test
    fun `disabling an app that has no channel or no program deletes nothing`() {
        helper.disableAppShortcut(computer, hades, "App hidden")
        val channel = tv.seedChannel("uuid-1")
        tv.seedProgram(channel, "43", browsable = 1)

        helper.disableAppShortcut(computer, hades, "App hidden")

        assertThat(tv.deletes).isEmpty()
    }

    @Test
    fun `disabling an app whose program was removed from the home screen deletes it once`() {
        val channel = tv.seedChannel("uuid-1")
        val program = tv.seedProgram(channel, "42", browsable = 0)

        helper.disableAppShortcut(computer, hades, "App hidden")

        assertThat(tv.deletes).containsExactly(TvContract.buildPreviewProgramUri(program))
    }

    /**
     * The TV input manager of the system, which only keeps the channels that it was asked to show.
     */
    @Implements(TvInputManager::class)
    class RecordingTvInputManager {
        val requested = mutableListOf<Uri>()

        @Implementation
        fun requestChannelBrowsable(channelUri: Uri) {
            requested += channelUri
        }
    }

    /** An activity that cannot start the activity that shows the channel. */
    class FailingActivity : Activity() {
        var failure: RuntimeException = ActivityNotFoundException()
        var asked = false

        override fun startActivityForResult(intent: Intent, requestCode: Int) {
            asked = true
            throw failure
        }
    }

    private companion object {
        val PNG_SIGNATURE =
            listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).map { it.toByte() }

        /** The instant that the weight of a program counts from: 2017-07-14 (UTC). */
        const val EPOCH_MS = 1_500_000_000_000L
    }
}

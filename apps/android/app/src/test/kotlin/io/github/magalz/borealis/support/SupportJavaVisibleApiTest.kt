package io.github.magalz.borealis.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.LimeLog
import com.limelight.computers.ComputerManagerService
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.ui.GameGestures
import com.limelight.ui.StreamView
import com.limelight.utils.CacheHelper
import com.limelight.utils.Dialog
import com.limelight.utils.NetHelper
import com.limelight.utils.ServerHelper
import com.limelight.utils.ShortcutHelper
import com.limelight.utils.TvChannelHelper
import com.limelight.utils.UiHelper
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The JVM members that the Java of the app uses (`Game`, `ComputerManagerService`, the 25 files
 * that log, `activity_game.xml` and the manifest): they keep their names, their signatures, their
 * modifiers and their `throws`, whatever language the classes are written in.
 */
class SupportJavaVisibleApiTest {
    private fun Class<*>.publicMethod(name: String, vararg types: Class<*>): Method =
        getMethod(name, *types)

    private fun Method.isPublicStatic() =
        Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)

    @Test
    fun `the log has three static methods that take a string`() {
        for (name in listOf("info", "warning", "severe")) {
            val method = LimeLog::class.java.publicMethod(name, String::class.java)

            assertThat(method.isPublicStatic()).isTrue()
            assertThat(method.returnType).isEqualTo(Void.TYPE)
            assertThat(method.exceptionTypes).isEmpty()
        }
    }

    @Test
    fun `the server helper keeps its constant and its static methods, and the throws of one`() {
        val connectionTest = ServerHelper::class.java.getField("CONNECTION_TEST_SERVER")
        assertThat(Modifier.isStatic(connectionTest.modifiers)).isTrue()
        assertThat(Modifier.isFinal(connectionTest.modifiers)).isTrue()
        assertThat(connectionTest.type).isEqualTo(String::class.java)

        val current =
            ServerHelper::class
                .java
                .publicMethod("getCurrentAddressFromComputer", ComputerDetails::class.java)
        assertThat(current.isPublicStatic()).isTrue()
        assertThat(current.returnType).isEqualTo(ComputerDetails.AddressTuple::class.java)
        assertThat(current.exceptionTypes.toList()).containsExactly(IOException::class.java)

        val pc =
            ServerHelper::class
                .java
                .publicMethod(
                    "createPcShortcutIntent",
                    Context::class.java,
                    ComputerDetails::class.java,
                )
        val app =
            ServerHelper::class
                .java
                .publicMethod(
                    "createAppShortcutIntent",
                    Context::class.java,
                    ComputerDetails::class.java,
                    NvApp::class.java,
                )
        val start =
            ServerHelper::class
                .java
                .publicMethod(
                    "createStartIntent",
                    Activity::class.java,
                    NvApp::class.java,
                    ComputerDetails::class.java,
                    ComputerManagerService.ComputerManagerBinder::class.java,
                )
        val doStart =
            ServerHelper::class
                .java
                .publicMethod(
                    "doStart",
                    Activity::class.java,
                    NvApp::class.java,
                    ComputerDetails::class.java,
                    ComputerManagerService.ComputerManagerBinder::class.java,
                )
        for (method in listOf(pc, app, start, doStart)) assertThat(method.isPublicStatic()).isTrue()
        for (method in listOf(pc, app, start)) {
            assertThat(method.returnType).isEqualTo(Intent::class.java)
        }
        assertThat(doStart.returnType).isEqualTo(Void.TYPE)
    }

    @Test
    fun `the cache helper takes its path as varargs and keeps its throws`() {
        val strings = Array<String>::class.java
        val openPath =
            CacheHelper::class
                .java
                .publicMethod("openPath", Boolean::class.java, File::class.java, strings)
        val exists =
            CacheHelper::class.java.publicMethod("cacheFileExists", File::class.java, strings)
        val output =
            CacheHelper::class
                .java
                .publicMethod("openCacheFileForOutput", File::class.java, strings)
        val copy =
            CacheHelper::class
                .java
                .publicMethod(
                    "writeInputStreamToOutputStream",
                    InputStream::class.java,
                    OutputStream::class.java,
                    Long::class.java,
                )
        val write =
            CacheHelper::class
                .java
                .publicMethod(
                    "writeStringToOutputStream",
                    OutputStream::class.java,
                    String::class.java,
                )

        for (method in listOf(openPath, exists, output, copy, write)) {
            assertThat(method.isPublicStatic()).isTrue()
        }
        // Java passes the components one by one: that is what a varargs method is.
        assertThat(listOf(openPath, exists, output).map { it.isVarArgs })
            .containsExactly(true, true, true)
        assertThat(openPath.returnType).isEqualTo(File::class.java)
        assertThat(exists.returnType).isEqualTo(Boolean::class.java)
        assertThat(output.returnType).isEqualTo(OutputStream::class.java)
        assertThat(output.exceptionTypes.toList())
            .containsExactly(FileNotFoundException::class.java)
        assertThat(copy.exceptionTypes.toList()).containsExactly(IOException::class.java)
        assertThat(write.exceptionTypes.toList()).containsExactly(IOException::class.java)
        assertThat(openPath.exceptionTypes).isEmpty()
    }

    @Test
    fun `the network helper has one static method that takes a context`() {
        val method = NetHelper::class.java.publicMethod("isActiveNetworkVpn", Context::class.java)

        assertThat(method.isPublicStatic()).isTrue()
        assertThat(method.returnType).isEqualTo(Boolean::class.java)
    }

    @Test
    fun `the UI helper keeps the static methods that Game and the Library call`() {
        for (name in
            listOf(
                "notifyStreamConnecting",
                "notifyStreamConnected",
                "notifyStreamEnteringPiP",
                "notifyStreamExitingPiP",
                "notifyStreamEnded",
            )) {
            val method = UiHelper::class.java.publicMethod(name, Context::class.java)

            assertThat(method.isPublicStatic()).isTrue()
            assertThat(method.returnType).isEqualTo(Void.TYPE)
        }
        val crash =
            UiHelper::class.java.publicMethod("showDecoderCrashDialog", Activity::class.java)
        assertThat(crash.isPublicStatic()).isTrue()
        assertThat(crash.returnType).isEqualTo(Void.TYPE)
    }

    @Test
    fun `the shortcut helper keeps its constructor and its methods`() {
        val constructor = ShortcutHelper::class.java.getConstructor(Context::class.java)
        assertThat(Modifier.isPublic(constructor.modifiers)).isTrue()

        val helper = ShortcutHelper::class.java
        val details = ComputerDetails::class.java
        val methods =
            listOf(
                helper.publicMethod("reportComputerShortcutUsed", details) to Void.TYPE,
                helper.publicMethod("reportGameLaunched", details, NvApp::class.java) to Void.TYPE,
                helper.publicMethod(
                    "createHostShortcut",
                    details,
                    Boolean::class.java,
                    Boolean::class.java,
                ) to Void.TYPE,
                helper.publicMethod(
                    "createPinnedGameShortcut",
                    details,
                    NvApp::class.java,
                    Bitmap::class.java,
                ) to Boolean::class.java,
                helper.publicMethod("disableComputerShortcut", details, CharSequence::class.java) to
                    Void.TYPE,
                helper.publicMethod(
                    "disableAppShortcut",
                    details,
                    NvApp::class.java,
                    CharSequence::class.java,
                ) to Void.TYPE,
                helper.publicMethod("enableAppShortcut", details, NvApp::class.java) to Void.TYPE,
            )
        for ((method, returnType) in methods) {
            assertThat(Modifier.isStatic(method.modifiers)).isFalse()
            assertThat(method.returnType).isEqualTo(returnType)
        }
    }

    @Test
    fun `the TV channel helper has a public constructor that takes a context`() {
        val constructor = TvChannelHelper::class.java.getConstructor(Context::class.java)

        assertThat(Modifier.isPublic(constructor.modifiers)).isTrue()
    }

    @Test
    fun `the dialog is shown by a static method that takes the activity, two texts and a runnable`() {
        val method =
            Dialog::class
                .java
                .publicMethod(
                    "displayDialog",
                    Activity::class.java,
                    String::class.java,
                    String::class.java,
                    Runnable::class.java,
                )

        assertThat(method.isPublicStatic()).isTrue()
        assertThat(method.returnType).isEqualTo(Void.TYPE)
    }

    @Test
    fun `the game gestures are an interface with one method`() {
        assertThat(GameGestures::class.java.isInterface).isTrue()
        assertThat(GameGestures::class.java.methods.map { it.name })
            .containsExactly("toggleKeyboard")
        val toggle = GameGestures::class.java.publicMethod("toggleKeyboard")
        assertThat(toggle.returnType).isEqualTo(Void.TYPE)
        assertThat(Modifier.isAbstract(toggle.modifiers)).isTrue()
    }

    @Test
    fun `the stream view keeps its setters, its input callbacks and its overrides`() {
        val ratio = StreamView::class.java.publicMethod("setDesiredAspectRatio", Double::class.java)
        val callbacks =
            StreamView::class
                .java
                .publicMethod("setInputCallbacks", StreamView.InputCallbacks::class.java)
        val measure =
            StreamView::class
                .java
                .getDeclaredMethod(
                    "onMeasure",
                    Int::class.java,
                    Int::class.java,
                )
        val keys =
            StreamView::class
                .java
                .getDeclaredMethod("onKeyPreIme", Int::class.java, KeyEvent::class.java)

        assertThat(listOf(ratio, callbacks).map { it.returnType })
            .containsExactly(Void.TYPE, Void.TYPE)
        assertThat(Modifier.isProtected(measure.modifiers)).isTrue()
        assertThat(Modifier.isPublic(keys.modifiers)).isTrue()
        assertThat(keys.returnType).isEqualTo(Boolean::class.java)

        assertThat(StreamView.InputCallbacks::class.java.isInterface).isTrue()
        assertThat(StreamView.InputCallbacks::class.java.methods.map { it.name })
            .containsExactly("handleKeyUp", "handleKeyDown")
        for (name in listOf("handleKeyUp", "handleKeyDown")) {
            val method =
                StreamView.InputCallbacks::class.java.publicMethod(name, KeyEvent::class.java)
            assertThat(method.returnType).isEqualTo(Boolean::class.java)
            assertThat(Modifier.isAbstract(method.modifiers)).isTrue()
        }
    }

    @Test
    fun `the disk asset loader keeps its constructor, its methods and its tuple`() {
        assertThat(
                Modifier.isPublic(
                    DiskAssetLoader::class.java.getConstructor(Context::class.java).modifiers
                )
            )
            .isTrue()
        val tuple = DiskAssetLoader.LoaderTuple::class.java
        val sampleSize =
            DiskAssetLoader::class
                .java
                .publicMethod(
                    "calculateInSampleSize",
                    BitmapFactory.Options::class.java,
                    Int::class.java,
                    Int::class.java,
                )

        assertThat(sampleSize.isPublicStatic()).isTrue()
        assertThat(sampleSize.returnType).isEqualTo(Int::class.java)
        assertThat(DiskAssetLoader::class.java.publicMethod("checkCacheExists", tuple).returnType)
            .isEqualTo(Boolean::class.java)
        assertThat(
                DiskAssetLoader::class
                    .java
                    .publicMethod("getFile", String::class.java, Int::class.java)
                    .returnType
            )
            .isEqualTo(File::class.java)
        assertThat(
                DiskAssetLoader::class
                    .java
                    .publicMethod("deleteAssetsForComputer", String::class.java)
                    .returnType
            )
            .isEqualTo(Void.TYPE)
        assertThat(Modifier.isStatic(tuple.modifiers)).isTrue()
        assertThat(
                Modifier.isPublic(
                    tuple.getConstructor(ComputerDetails::class.java, NvApp::class.java).modifiers
                )
            )
            .isTrue()
        for ((name, type) in
            listOf("computer" to ComputerDetails::class.java, "app" to NvApp::class.java)) {
            val field = tuple.getField(name)
            assertThat(Modifier.isFinal(field.modifiers)).isTrue()
            assertThat(field.type).isEqualTo(type)
        }
    }
}

package io.github.magalz.borealis.settings.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl

/**
 * Lets every window of the test settle to its final size before a screenshot.
 *
 * Robolectric lays a window that draws under the system bars (a `BorealisDialog`) out one pixel
 * taller than the screen at first, and only a resize of the window corrects it. Whether that resize
 * happens by itself depends on what ran earlier in the same JVM, so a centred dialog could land on
 * a different pixel row from one run to the next. Dispatching the resize here makes the picture the
 * same in every order.
 */
fun ComposeTestRule.settleWindows() {
    val global =
        Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
    val roots =
        global.javaClass.getDeclaredField("mRoots").apply { isAccessible = true }.get(global)
            as List<*>
    runOnUiThread { roots.forEach { Shadow.extract<ShadowViewRootImpl>(it).callDispatchResized() } }
    waitForIdle()
}

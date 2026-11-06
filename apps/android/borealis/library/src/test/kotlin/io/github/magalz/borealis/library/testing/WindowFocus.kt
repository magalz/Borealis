package io.github.magalz.borealis.library.testing

import android.view.View
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * What a window that opens over the screen can do to the one under it (Sprint 14 UAT, U11): Android
 * takes the focus from its view and gives it back, and Compose then puts the focus on the first
 * element and not on the one that had it. The dialogs of Robolectric leave the focus alone, so the
 * test does what the device did. [window] is the view the screen is composed in
 * (`LocalView.current`).
 */
fun ComposeTestRule.resetWindowFocus(window: View?) {
    runOnUiThread { checkNotNull(window).clearFocus() }
    waitForIdle()
}

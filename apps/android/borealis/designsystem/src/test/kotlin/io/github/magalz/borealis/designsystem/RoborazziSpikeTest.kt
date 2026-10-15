package io.github.magalz.borealis.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Temporary proof that Roborazzi renders on Robolectric; T1 deletes it with the theme screenshots.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class RoborazziSpikeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersTextOnDarkBox() {
        composeRule.setContent {
            Box(Modifier.background(Color(DARK_BACKGROUND)).padding(16.dp)) {
                Text("Borealis 0123", color = Color.White)
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    private companion object {
        const val DARK_BACKGROUND = 0xFF10131A
    }
}

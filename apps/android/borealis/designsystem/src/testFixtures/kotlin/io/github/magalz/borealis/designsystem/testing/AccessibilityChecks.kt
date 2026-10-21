package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker.CheckLevel
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesElements
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import org.hamcrest.BaseMatcher
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.anything
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Description
import org.hamcrest.Matcher

/**
 * A finding of Google's Accessibility Test Framework that a test accepts, and why. The [reason] is
 * the documentation of the suppression and is required: a check is never switched off without it.
 * [check] is the class of the check ([TouchTargetSizeCheck], [TextContrastCheck]); [on] limits the
 * suppression to the elements that match, by default every element.
 */
class AcceptedFinding(
    val check: Class<out AccessibilityHierarchyCheck>,
    val reason: String,
    val on: Matcher<in ViewHierarchyElement> = anything(),
) {
    init {
        require(reason.isNotBlank()) { "A suppressed check needs a reason" }
    }
}

/** The elements whose test tag starts with [prefix]. */
fun taggedWith(prefix: String): Matcher<ViewHierarchyElement> =
    elementMatching("an element tagged $prefix…") { it.testTag?.startsWith(prefix) == true }

/** The elements that show exactly [text]. */
fun showing(text: String): Matcher<ViewHierarchyElement> =
    elementMatching("an element that shows $text") { it.text?.toString() == text }

private fun elementMatching(
    label: String,
    test: (ViewHierarchyElement) -> Boolean,
): Matcher<ViewHierarchyElement> =
    object : BaseMatcher<ViewHierarchyElement>() {
        override fun matches(item: Any?) = item is ViewHierarchyElement && test(item)

        override fun describeTo(description: Description) {
            description.appendText(label)
        }
    }

/**
 * A TV has no touch screen: a target is reached by the D-pad or a gamepad, and the focus outline
 * shows it, so the 48 dp that a finger needs does not apply.
 */
val NoTouchOnTv =
    AcceptedFinding(
        TouchTargetSizeCheck::class.java,
        "a TV has no touch screen; its targets are reached by the D-pad and a focus outline",
    )

/**
 * Runs Google's Accessibility Test Framework over every window of the test, the screen and a dialog
 * or sheet over it. It fails on an error or a warning, for example a touch target below 48 dp, a
 * control with no name, two controls that a screen reader says the same, or a traversal that is out
 * of order. A finding that the screen cannot fix is listed in [accepted], each with its reason.
 */
fun ComposeTestRule.assertAccessible(vararg accepted: AcceptedFinding) {
    val windows = onAllNodes(isRoot()).fetchSemanticsNodes().size
    repeat(windows) { onAllNodes(isRoot())[it].assertAccessible(*accepted) }
}

/** [assertAccessible] for the window of the node that [this] finds. */
fun SemanticsNodeInteraction.assertAccessible(vararg accepted: AcceptedFinding) {
    val accepting: Matcher<in AccessibilityViewCheckResult> =
        if (accepted.isEmpty()) {
            not(anything())
        } else {
            anyOf(
                accepted.map {
                    allOf<AccessibilityViewCheckResult>(
                        matchesCheck(it.check),
                        matchesElements(it.on),
                    )
                }
            )
        }
    checkRoboAccessibility(
        RoborazziATFAccessibilityCheckOptions(
            RoborazziATFAccessibilityChecker(AccessibilityCheckPreset.LATEST, accepting),
            CheckLevel.Warning,
        )
    )
}

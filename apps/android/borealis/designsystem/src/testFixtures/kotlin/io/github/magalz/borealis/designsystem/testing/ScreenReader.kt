package io.github.magalz.borealis.designsystem.testing

import android.annotation.SuppressLint
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot

/**
 * What a screen reader says, in the order that it moves through the screen: the content description
 * of each node, or else its text, as the accessibility node provider of the window gives the nodes
 * to a screen reader. A node that says nothing, a container, is left out. It reads the window of
 * [onRoot], so a dialog or a sheet over a screen is read apart from it.
 */
// Robolectric only, never a device: the test fixtures are not packaged into an APK.
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun ComposeTestRule.readingOrder(): List<String> {
    val view = onRoot().fetchSemanticsNode().root as View
    val provider = checkNotNull(view.accessibilityNodeProvider) { "the window has no provider" }
    val childId = AccessibilityNodeInfo::class.java.getMethod("getChildId", Int::class.java)
    val said = mutableListOf<String>()

    fun read(virtualId: Int) {
        val info = provider.createAccessibilityNodeInfo(virtualId) ?: return
        val words = (info.contentDescription ?: info.text)?.toString().orEmpty()
        if (words.isNotEmpty()) said += words
        for (index in 0 until info.childCount) {
            // The id of a virtual child is in the upper half of the long that the node gives.
            read(((childId.invoke(info, index) as Long) shr VIRTUAL_ID_SHIFT).toInt())
        }
    }
    read(AccessibilityNodeProvider.HOST_VIEW_ID)
    return said
}

private const val VIRTUAL_ID_SHIFT = 32

/**
 * The headings of the screen, in the order of the tree: what a screen reader jumps between with its
 * heading navigation. Each is the text of the node.
 */
fun ComposeTestRule.headings(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        .fetchSemanticsNodes()
        .map { it.said() }

/**
 * The titles of the panes: what a screen reader announces when a dialog, a sheet or a panel opens.
 */
fun ComposeTestRule.paneTitles(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.PaneTitle] }

private fun SemanticsNode.said(): String =
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(", ") { it.text }.orEmpty()

package io.github.magalz.borealis.designsystem.components

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BorealisIconsTest {
    private val icons =
        mapOf(
            "Back" to BorealisIcons.Back,
            "ChevronRight" to BorealisIcons.ChevronRight,
            "ChevronDown" to BorealisIcons.ChevronDown,
            "Check" to BorealisIcons.Check,
            "Gear" to BorealisIcons.Gear,
            "Hosts" to BorealisIcons.Hosts,
            "Add" to BorealisIcons.Add,
            "Visibility" to BorealisIcons.Visibility,
            "VisibilityOff" to BorealisIcons.VisibilityOff,
        )

    @Test
    fun `every icon is on the 24 unit grid at 24dp`() {
        icons.forEach { (name, icon) ->
            assertThat(icon.defaultWidth).isEqualTo(24.dp)
            assertThat(icon.defaultHeight).isEqualTo(24.dp)
            assertThat(icon.viewportWidth).isEqualTo(24f)
            assertThat(icon.viewportHeight).isEqualTo(24f)
            assertThat(icon.name).isEqualTo(name)
        }
    }

    @Test
    fun `every icon is drawn with strokes of 2 with round caps and joins`() {
        icons.values.forEach { icon ->
            val paths = icon.paths()
            assertThat(paths).isNotEmpty()
            paths.forEach {
                assertThat(it.stroke).isNotNull()
                assertThat(it.strokeLineWidth).isEqualTo(2f)
                assertThat(it.strokeLineCap).isEqualTo(StrokeCap.Round)
                assertThat(it.strokeLineJoin).isEqualTo(StrokeJoin.Round)
                assertThat(it.fill).isNull()
            }
        }
    }

    @Test
    fun `the icons draw different shapes`() {
        val shapes = icons.values.map { icon -> icon.paths().map { it.pathData } }
        assertThat(shapes.toSet()).hasSize(icons.size)
    }

    private fun ImageVector.paths(): List<VectorPath> {
        fun collect(node: VectorNode): List<VectorPath> =
            when (node) {
                is VectorPath -> listOf(node)
                is VectorGroup -> node.flatMap(::collect)
            }
        return collect(root)
    }
}

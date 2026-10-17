package io.github.magalz.borealis.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Borealis icon set. Every icon is drawn in code on the 24-unit grid with strokes of 2, round
 * caps and round joins, and no fill; the screens tint it. The design system has no icon library, so
 * it does not pull in the Material icons.
 *
 * The gear is also in `app/src/main/res/drawable/ic_settings.xml`, which draws the same path for
 * the on-screen controls of the stream.
 */
object BorealisIcons {
    /** An arrow pointing left, for going back. */
    val Back: ImageVector by lazy { icon("Back", "M19 12H5", "M12 19l-7-7 7-7") }

    /** A chevron pointing right, for a row that opens something. */
    val ChevronRight: ImageVector by lazy { icon("ChevronRight", "M9 6l6 6-6 6") }

    /** A chevron pointing down, for something that expands. */
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "M6 9l6 6 6-6") }

    /** A check mark. */
    val Check: ImageVector by lazy { icon("Check", CHECK) }

    /** A gear with eight teeth, for Settings. */
    val Gear: ImageVector by lazy { icon("Gear", GEAR, "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0") }

    /** A monitor on a stand, for hosts. */
    val Hosts: ImageVector by lazy { icon("Hosts", "M3.5 4.5h17v11h-17z", "M8 20h8M12 15.5V20") }

    /** A plus sign. */
    val Add: ImageVector by lazy { icon("Add", "M12 5v14M5 12h14") }

    /** An eye: the content is shown. */
    val Visibility: ImageVector by lazy { icon("Visibility", EYE, PUPIL) }

    /** An eye with a slash: the content is hidden. */
    val VisibilityOff: ImageVector by lazy { icon("VisibilityOff", EYE, PUPIL, "M4 4l16 16") }

    /** The path of the check mark, which [AnimatedCheck] draws too. */
    internal const val CHECK = "M5 12.5l4.5 4.5L19 7.5"

    private const val GEAR =
        "M10.38 4.98L10.75 2.48L13.25 2.48L13.62 4.98A7.2 7.2 0 0 1 15.82 5.89L17.84 4.38" +
            "L19.62 6.16L18.11 8.18A7.2 7.2 0 0 1 19.02 10.38L21.52 10.75L21.52 13.25" +
            "L19.02 13.62A7.2 7.2 0 0 1 18.11 15.82L19.62 17.84L17.84 19.62L15.82 18.11" +
            "A7.2 7.2 0 0 1 13.62 19.02L13.25 21.52L10.75 21.52L10.38 19.02" +
            "A7.2 7.2 0 0 1 8.18 18.11L6.16 19.62L4.38 17.84L5.89 15.82" +
            "A7.2 7.2 0 0 1 4.98 13.62L2.48 13.25L2.48 10.75L4.98 10.38" +
            "A7.2 7.2 0 0 1 5.89 8.18L4.38 6.16L6.16 4.38L8.18 5.89A7.2 7.2 0 0 1 10.38 4.98Z"
    private const val EYE = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"
    private const val PUPIL = "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
    private const val STROKE = 2f

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .apply {
                paths.forEach {
                    addPath(
                        pathData = PathParser().parsePathString(it).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = STROKE,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}

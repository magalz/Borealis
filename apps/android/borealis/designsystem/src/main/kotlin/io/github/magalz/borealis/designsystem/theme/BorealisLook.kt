package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** How see-through the glass panels are (ADR 0057). [Off] draws them solid, as before the glass. */
enum class Transparency {
    Standard,
    Reduced,
    Off,
}

/** Whether motion follows the system's animation scale or is forced on or off. */
enum class ReduceMotion {
    FollowSystem,
    On,
    Off,
}

/** The app's own text size, multiplied by the system font scale. */
enum class TextSize(val scale: Float) {
    Default(1f),
    Larger(1.15f),
    Largest(1.3f),
}

/** The palette for status colours. Every status also has a shape or a text, not colour alone. */
enum class ColourVision {
    Off,
    RedGreen,
    BlueYellow,
}

/** The font of the app. [System] is the font of the device; the others are bundled. */
enum class FontChoice {
    Plex,
    AtkinsonNext,
    Luciole,
    Lexend,
    Andika,
    B612,
    System,
}

/** The accent colour of the selected and the focused. Every accent has a high contrast variant. */
enum class AccentChoice {
    Violet,
    Teal,
    White,
}

/**
 * How strongly the animated background shows: [Full] is 100 %, [ThreeQuarters] 75 %, [Half] 50 %
 * and [Quarter] 25 %. The rest is a [veil]: the alpha of the page colour that lies over the
 * background, so that the glass panels, whose floors assume the background at full strength, stay
 * legible. A veil pulls each pixel toward the page colour: a pixel brighter than the page gets
 * darker, and one darker than the page, as some pixels of a clip are, gets lighter. So a veil never
 * raises the brightest pixel of the background, which is what the floors of the glass assume.
 */
enum class BackgroundIntensity(val veil: Float) {
    Full(0f),
    ThreeQuarters(0.25f),
    Half(0.5f),
    Quarter(0.75f),
}

/**
 * The space between the letters, words and lines of the text (WCAG 1.4.12; ADR 0058, decision 4).
 * Each value is a minimum in em of the font size of the style that it is applied to: a style keeps
 * what it has when that is more. [Normal] has no minimum, so it leaves the design as it is.
 *
 * @property letterEm the least letter spacing.
 * @property wordEm the spacing that each space adds to the letter spacing.
 * @property lineHeightEm the least line height, as a multiple of the font size.
 */
enum class TextSpacing(val letterEm: Float, val wordEm: Float, val lineHeightEm: Float) {
    Normal(0f, 0f, 0f),
    Wide(0.12f, 0.16f, 1.5f),
    Wider(0.18f, 0.5f, 1.5f),
}

/** How close the cards of a screen sit. [Comfortable] is the layout that the app has today. */
enum class CardDensity {
    Comfortable,
    Compact,
    Dense,
}

/**
 * The viewer's appearance and accessibility choices, read from Settings by each activity and
 * provided through [LocalBorealisLook]. The defaults are the app's look with nothing changed.
 *
 * @property font the font of the text, and of the codes through its monospaced companion.
 * @property accent the accent colour.
 * @property backgroundIntensity how strongly the animated background shows. With [highContrast] on,
 *   the stronger of its veil and the dim of high contrast wins.
 * @property cardDensity how close the cards sit.
 * @property textSpacing the space between letters, words and lines. The theme draws the level that
 *   the font scale allows, which is a smaller one at a very large font ([effectiveTextSpacing]).
 * @property glassTintPercent the tint of a [Transparency.Standard] glass panel, before the floor of
 *   the background raises it.
 * @property glassBlurDp the blur radius of a [Transparency.Standard] glass panel.
 * @property highContrast when on, it overrides [transparency], [thickFocus] and the background.
 */
@Immutable
data class BorealisLook(
    val transparency: Transparency = Transparency.Standard,
    val glassTintPercent: Int = 40,
    val glassBlurDp: Int = 8,
    val reduceMotion: ReduceMotion = ReduceMotion.FollowSystem,
    val thickFocus: Boolean = false,
    val textSize: TextSize = TextSize.Default,
    val highContrast: Boolean = false,
    val colourVision: ColourVision = ColourVision.Off,
    val font: FontChoice = FontChoice.Plex,
    val accent: AccentChoice = AccentChoice.Violet,
    val backgroundIntensity: BackgroundIntensity = BackgroundIntensity.Full,
    val cardDensity: CardDensity = CardDensity.Comfortable,
    val textSpacing: TextSpacing = TextSpacing.Normal,
)

/**
 * The [BorealisLook] in effect: what [BorealisTheme] was given, with the text spacing that its font
 * scale allows. A screen that nobody gives a look to gets the defaults.
 */
val LocalBorealisLook = staticCompositionLocalOf { BorealisLook() }

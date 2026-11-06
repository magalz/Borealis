package io.github.magalz.borealis.stream.ui.keyboard

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.KeyStyle
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.keys.VK_0
import io.github.magalz.borealis.stream.keys.VK_A
import io.github.magalz.borealis.stream.keys.VK_BACKSPACE
import io.github.magalz.borealis.stream.keys.VK_DELETE
import io.github.magalz.borealis.stream.keys.VK_DOWN
import io.github.magalz.borealis.stream.keys.VK_END
import io.github.magalz.borealis.stream.keys.VK_ENTER
import io.github.magalz.borealis.stream.keys.VK_F1
import io.github.magalz.borealis.stream.keys.VK_HOME
import io.github.magalz.borealis.stream.keys.VK_LEFT
import io.github.magalz.borealis.stream.keys.VK_PAGE_DOWN
import io.github.magalz.borealis.stream.keys.VK_PAGE_UP
import io.github.magalz.borealis.stream.keys.VK_RIGHT
import io.github.magalz.borealis.stream.keys.VK_SPACE
import io.github.magalz.borealis.stream.keys.VK_TAB
import io.github.magalz.borealis.stream.keys.VK_UP

/**
 * What a key shows: a character that needs no translation, a named key from the resources, or a
 * [Symbol] for a key whose name would not fit its cap and so is read out as its [Symbol.name]. A
 * [Letter] is named by its capital and shows it only while Shift is latched (see [onCap]).
 */
internal sealed interface KeyLabel {
    data class Text(val value: String) : KeyLabel

    data class Named(@StringRes val id: Int) : KeyLabel

    /** A letter of the alphabet, kept as the capital that names the key. */
    data class Letter(val capital: Char) : KeyLabel

    data class Symbol(val symbol: String, @StringRes val name: Int) : KeyLabel

    /** The text on the cap. */
    @Composable
    fun resolve(): String =
        when (this) {
            is Text -> value
            is Letter -> capital.toString()
            is Named -> stringResource(id)
            is Symbol -> symbol
        }

    fun resolve(context: Context): String =
        when (this) {
            is Text -> value
            is Letter -> capital.toString()
            is Named -> context.getString(id)
            is Symbol -> symbol
        }

    /** The [cap] as the user sees it: a letter is lowercase unless [shifted]. */
    fun onCap(cap: String, shifted: Boolean): String =
        if (this is Letter && !shifted) cap.lowercase() else cap

    /** What the key is called: the cap, or the name of a [Symbol]. */
    @Composable fun name(): String = if (this is Symbol) stringResource(name) else resolve()
}

/** One key of the Borealis keyboard. */
internal sealed interface KeySpec {
    val label: KeyLabel
    val style: KeyStyle
}

/** A key that is sent as a chord: [vk] is a Windows virtual-key code, as `KeyboardTranslator`. */
internal data class CharKey(
    override val label: KeyLabel,
    val vk: Int,
    override val style: KeyStyle = KeyStyle.Normal,
) : KeySpec

/** A key that latches [modifier] until the next key. */
internal data class ModifierKey(
    override val label: KeyLabel,
    val modifier: KeyModifier,
    override val style: KeyStyle = KeyStyle.Wide,
) : KeySpec

/** The key that switches the letter rows to the digits and function keys, and back. */
internal data object FnKey : KeySpec {
    override val label = KeyLabel.Named(R.string.stream_keyboard_fn)
    override val style = KeyStyle.Wide
}

/**
 * The rows of the keyboard under the shortcut row. The keys send US virtual keys; text in other
 * languages goes through the system keyboard.
 */
internal object KeyboardLayout {
    private fun letters(chars: String) = chars.map {
        CharKey(KeyLabel.Letter(it), VK_A + (it - 'A'))
    }

    private fun named(@StringRes id: Int, vk: Int, style: KeyStyle = KeyStyle.Wide) =
        CharKey(KeyLabel.Named(id), vk, style)

    private fun text(label: String, vk: Int, style: KeyStyle = KeyStyle.Normal) =
        CharKey(KeyLabel.Text(label), vk, style)

    private fun modifier(@StringRes id: Int, modifier: KeyModifier) =
        ModifierKey(KeyLabel.Named(id), modifier)

    // The word does not fit the 72dp cap when the row shrinks or the font grows.
    private fun backspace() =
        CharKey(
            KeyLabel.Symbol("⌫", R.string.stream_keyboard_backspace),
            VK_BACKSPACE,
            KeyStyle.Wide,
        )

    /** Q to P; A to L and Backspace; Shift, Z to M and Enter. */
    val letterRows: List<List<KeySpec>> =
        listOf(
            letters("QWERTYUIOP"),
            letters("ASDFGHJKL") + backspace(),
            listOf(modifier(R.string.stream_keyboard_shift, KeyModifier.SHIFT)) +
                letters("ZXCVBNM") +
                named(R.string.stream_keyboard_enter, VK_ENTER),
        )

    /** The digits; F1 to F10; the arrows and the navigation keys. */
    val functionRows: List<List<KeySpec>> =
        listOf(
            "1234567890".map { text("$it", VK_0 + (it - '0')) },
            (1..10).map { text("F$it", VK_F1 + it - 1) },
            listOf(
                text("←", VK_LEFT),
                text("↑", VK_UP),
                text("↓", VK_DOWN),
                text("→", VK_RIGHT),
                text("Home", VK_HOME, KeyStyle.Wide),
                text("End", VK_END, KeyStyle.Wide),
                text("PgUp", VK_PAGE_UP, KeyStyle.Wide),
                text("PgDn", VK_PAGE_DOWN, KeyStyle.Wide),
                text("Del", VK_DELETE, KeyStyle.Wide),
            ),
        )

    val bottomRow: List<KeySpec> =
        listOf(
            modifier(R.string.stream_keyboard_ctrl, KeyModifier.CTRL),
            modifier(R.string.stream_keyboard_win, KeyModifier.WIN),
            modifier(R.string.stream_keyboard_alt, KeyModifier.ALT),
            named(R.string.stream_keyboard_space, VK_SPACE, KeyStyle.Space),
            named(R.string.stream_keyboard_tab, VK_TAB),
            FnKey,
        )
}

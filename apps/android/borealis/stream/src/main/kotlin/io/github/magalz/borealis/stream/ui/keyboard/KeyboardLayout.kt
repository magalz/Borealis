package io.github.magalz.borealis.stream.ui.keyboard

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.KeyStyle
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.keys.KeyModifier

/**
 * What a key shows: a character that needs no translation, a named key from the resources, or a
 * [Symbol] for a key whose name would not fit its cap and so is read out as its [Symbol.name].
 */
internal sealed interface KeyLabel {
    data class Text(val value: String) : KeyLabel

    data class Named(@StringRes val id: Int) : KeyLabel

    data class Symbol(val symbol: String, @StringRes val name: Int) : KeyLabel

    /** The text on the cap. */
    @Composable
    fun resolve(): String =
        when (this) {
            is Text -> value
            is Named -> stringResource(id)
            is Symbol -> symbol
        }

    fun resolve(context: Context): String =
        when (this) {
            is Text -> value
            is Named -> context.getString(id)
            is Symbol -> symbol
        }

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
    private const val VK_BACKSPACE = 0x08
    private const val VK_TAB = 0x09
    private const val VK_ENTER = 0x0D
    private const val VK_SPACE = 0x20
    private const val VK_PAGE_UP = 0x21
    private const val VK_PAGE_DOWN = 0x22
    private const val VK_END = 0x23
    private const val VK_HOME = 0x24
    private const val VK_LEFT = 0x25
    private const val VK_UP = 0x26
    private const val VK_RIGHT = 0x27
    private const val VK_DOWN = 0x28
    private const val VK_DELETE = 0x2E
    private const val VK_0 = 0x30
    private const val VK_A = 0x41
    private const val VK_F1 = 0x70

    private fun letters(chars: String) = chars.map {
        CharKey(KeyLabel.Text(it.toString()), VK_A + (it - 'A'))
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

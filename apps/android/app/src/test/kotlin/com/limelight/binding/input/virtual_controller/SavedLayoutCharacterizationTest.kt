package com.limelight.binding.input.virtual_controller

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Canvas
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.ShadowMoonBridge
import java.io.File
import org.json.JSONException
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The layout a user saved: the `OSC` preference file, one JSON object per element id, as the Java
 * build wrote it and the Kotlin build must keep reading.
 *
 * `OSC.xml` next to these tests is a file that the Java `saveProfile` wrote through Android's own
 * `SharedPreferences`, in a Robolectric run on commit d0b0dfd03: it holds the ids 1 to 16, each at
 * a place that no default layout uses ([userLayout]). The tests read it, save what was read, and
 * compare the parsed values, because the order of the keys inside a JSON text is not part of the
 * format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMoonBridge::class])
class SavedLayoutCharacterizationTest {
    private val rig = OscRig()

    /** What the fixture holds for [id]: left, top, width and height, which grow with the id. */
    private fun userLayout(id: Int) = Placed(id, 100 + 7 * id, 200 + 3 * id, 150 + id, 90 + 2 * id)

    private fun installFixture() {
        val folder = File(rig.activity.dataDir, "shared_prefs").apply { mkdirs() }
        val fixture = checkNotNull(OscRig::class.java.getResourceAsStream("OSC.xml")) { "OSC.xml is missing" }
        fixture.use { input -> File(folder, "OSC.xml").outputStream().use { input.copyTo(it) } }
    }

    private fun refresh(onlyL3R3: Boolean = false) {
        rig.settings(onlyL3R3 = onlyL3R3)
        rig.controller.refreshLayout()
    }

    @Test
    fun `a layout that the Java build saved moves every element of the full layout`() {
        installFixture()

        refresh()

        assertThat(rig.layout())
            .containsExactlyElementsIn((GoldenLayouts.FULL_ORDER + GoldenLayouts.GUIDE).map(::userLayout))
            .inOrder()
    }

    @Test
    fun `a layout that the Java build saved moves the elements of the layout with L3 and R3`() {
        installFixture()

        refresh(onlyL3R3 = true)

        assertThat(rig.layout())
            .containsExactlyElementsIn((GoldenLayouts.L3_R3_ORDER + GoldenLayouts.GUIDE).map(::userLayout))
            .inOrder()
    }

    @Test
    fun `loading puts the saved layout over the default one and nothing else`() {
        installFixture()
        rig.settings()
        VirtualControllerConfigurationLoader.createDefaultLayout(rig.controller, rig.activity)

        VirtualControllerConfigurationLoader.loadFromPreferences(rig.controller, rig.activity)

        assertThat(rig.layout().map { it.id })
            .containsExactlyElementsIn(GoldenLayouts.FULL_ORDER + GoldenLayouts.GUIDE)
            .inOrder()
        assertThat(rig.layout())
            .containsExactlyElementsIn((GoldenLayouts.FULL_ORDER + GoldenLayouts.GUIDE).map(::userLayout))
            .inOrder()
        assertThat(rig.savedLayout().keys).containsExactlyElementsIn((1..16).map { it.toString() })
    }

    @Test
    fun `an element that the saved layout does not name keeps its default place`() {
        rig.saved.edit().putString("6", """{"LEFT":11,"TOP":22,"WIDTH":33,"HEIGHT":44}""").commit()

        refresh()

        val expected =
            GoldenLayouts.of(OscRig.PHONE, onlyL3R3 = false, guide = true).map {
                if (it.id == 6) Placed(6, 11, 22, 33, 44) else it
            }
        assertThat(rig.layout()).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `an empty saved layout leaves the default one`() {
        refresh()

        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true)).inOrder()
    }

    @Test
    fun `what was read is saved as it was`() {
        installFixture()
        refresh()

        VirtualControllerConfigurationLoader.saveProfile(rig.controller, rig.activity)

        assertThat(rig.savedLayout()).containsExactlyEntriesIn((1..16).associate { it.toString() to userLayout(it) })
    }

    @Test
    fun `saving the default layout writes the four numbers of each element under its id`() {
        rig.settings()
        VirtualControllerConfigurationLoader.createDefaultLayout(rig.controller, rig.activity)

        VirtualControllerConfigurationLoader.saveProfile(rig.controller, rig.activity)

        val expected = GoldenLayouts.of(OscRig.PHONE, onlyL3R3 = false, guide = true)
        assertThat(rig.savedLayout()).containsExactlyEntriesIn(expected.associateBy { it.id.toString() })
        rig.saved.all.values.forEach { json ->
            val keys = JSONObject(json as String).keys().asSequence().toSet()
            assertThat(keys).containsExactly("LEFT", "TOP", "WIDTH", "HEIGHT")
        }
    }

    @Test
    fun `saving a moved element keeps the new place, and the layout of the next refresh starts from it`() {
        refresh()
        rig.element(6).loadConfiguration(JSONObject("""{"LEFT":5,"TOP":6,"WIDTH":70,"HEIGHT":80}"""))

        VirtualControllerConfigurationLoader.saveProfile(rig.controller, rig.activity)
        rig.controller.refreshLayout()

        assertThat(rig.savedLayout().getValue("6")).isEqualTo(Placed(6, 5, 6, 70, 80))
        assertThat(rig.layout().first { it.id == 6 }).isEqualTo(Placed(6, 5, 6, 70, 80))
    }

    @Test
    fun `an element that cannot write its place is skipped and the others are saved`() {
        refresh()
        val unwritable =
            object : VirtualControllerElement(rig.controller, rig.activity, UNWRITABLE_ID) {
                override fun onElementDraw(canvas: Canvas) = Unit

                override fun onElementTouchEvent(event: MotionEvent) = true

                override fun getConfiguration(): JSONObject = throw JSONException("no place")
            }
        rig.controller.addElement(unwritable, 1, 2, 3, 4)

        VirtualControllerConfigurationLoader.saveProfile(rig.controller, rig.activity)

        assertThat(rig.saved.contains(UNWRITABLE_ID.toString())).isFalse()
        assertThat(rig.savedLayout().values)
            .containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true))
    }

    @Test
    fun `saving a layout adds to the file and does not clear the ids that this layout does not hold`() {
        refresh()
        VirtualControllerConfigurationLoader.saveProfile(rig.controller, rig.activity)
        rig.controller.removeElements()
        refresh(onlyL3R3 = true)

        VirtualControllerConfigurationLoader.saveProfile(rig.controller, rig.activity)

        assertThat(rig.savedLayout().keys).containsExactlyElementsIn((1..16).map { it.toString() })
    }

    @Test
    fun `saving and loading use the preference file OSC, private to the app`() {
        refresh()
        val opened = mutableListOf<Pair<String, Int>>()
        val spying =
            object : ContextWrapper(rig.activity) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                    opened += name to mode
                    return super.getSharedPreferences(name, mode)
                }
            }

        VirtualControllerConfigurationLoader.saveProfile(rig.controller, spying)
        VirtualControllerConfigurationLoader.loadFromPreferences(rig.controller, spying)

        assertThat(opened).containsExactly("OSC" to Context.MODE_PRIVATE, "OSC" to Context.MODE_PRIVATE)
        assertThat(VirtualControllerConfigurationLoader.OSC_PREFERENCE).isEqualTo("OSC")
    }

    @Test
    fun `an entry that is not JSON is removed and the other elements load`() {
        installFixture()
        rig.saved.edit().putString("6", "not json").commit()

        refresh()

        val defaults = GoldenLayouts.of(OscRig.PHONE, onlyL3R3 = false, guide = true)
        val expected = defaults.map { if (it.id == 6) it else userLayout(it.id) }
        assertThat(rig.layout()).containsExactlyElementsIn(expected).inOrder()
        assertThat(rig.saved.contains("6")).isFalse()
        assertThat(rig.saved.all.keys).containsExactlyElementsIn((1..16).filter { it != 6 }.map { it.toString() })
    }

    @Test
    fun `entries that hold no JSON object are removed`() {
        rig.saved.edit().putString("1", "").putString("2", "5").putString("3", "[1,2,3]").commit()

        refresh()

        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true)).inOrder()
        assertThat(rig.saved.all).isEmpty()
    }

    @Test
    fun `an entry that misses a number is removed after the numbers before it were applied`() {
        rig.saved.edit().putString("6", """{"LEFT":1,"TOP":2,"WIDTH":3}""").commit()

        refresh()

        // LEFT and TOP were read before the missing HEIGHT was found
        val defaults = GoldenLayouts.of(OscRig.PHONE, onlyL3R3 = false, guide = true)
        val a = defaults.first { it.id == 6 }
        val expected = defaults.map { if (it.id == 6) Placed(6, 1, 2, 3, a.height) else it }
        assertThat(rig.layout()).containsExactlyElementsIn(expected).inOrder()
        assertThat(rig.saved.contains("6")).isFalse()
    }

    @Test
    fun `an entry whose numbers are not numbers is removed and moves nothing`() {
        rig.saved.edit().putString("6", """{"LEFT":"left","TOP":2,"WIDTH":3,"HEIGHT":4}""").commit()

        refresh()

        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true)).inOrder()
        assertThat(rig.saved.contains("6")).isFalse()
    }

    @Test
    fun `an entry for an id that the layout does not hold stays in the file`() {
        rig.saved.edit().putString("14", """{"LEFT":1,"TOP":2,"WIDTH":3,"HEIGHT":4}""").putString("99", "junk").commit()

        refresh()

        assertThat(rig.layout()).containsExactlyElementsIn(GoldenLayouts.of(OscRig.PHONE, false, true)).inOrder()
        assertThat(rig.saved.all.keys).containsExactly("14", "99")
    }

    @Test
    fun `numbers written as text and extra keys are read the way org json reads them`() {
        rig.saved.edit().putString("6", """{"LEFT":"7","TOP":8.9,"WIDTH":"10","HEIGHT":11,"EXTRA":12}""").commit()

        refresh()

        assertThat(rig.layout().first { it.id == 6 }).isEqualTo(Placed(6, 7, 8, 10, 11))
        assertThat(rig.saved.contains("6")).isTrue()
    }

    private companion object {
        const val UNWRITABLE_ID = 99
    }
}

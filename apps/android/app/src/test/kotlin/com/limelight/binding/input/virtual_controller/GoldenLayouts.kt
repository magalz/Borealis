package com.limelight.binding.input.virtual_controller

/**
 * The default layout of the on-screen controller for each test screen, as the Java loader placed
 * it at the commit that made these tests: the elements in the order the loader adds them, with the
 * Guide button last. Every row also equals an independent computation of the grid arithmetic (a
 * float32 script): a grid of 128 by 72 cells at 16:9, `(int) ((float) height / 72f * units)`, and
 * the controls on the right moved by `width - height * 16 / 9` (integer arithmetic, negative on a
 * screen narrower than 16:9).
 *
 * The ids are the element ids: 1 pad, 6 to 9 the face buttons A, B, X and Y, 2 and 3 the triggers,
 * 4 and 5 the bumpers, 12 and 13 the sticks, 10 Back, 11 Start, 14 and 15 the stick clicks L3 and
 * R3, 16 Guide.
 */
object GoldenLayouts {
    /** The ids of the full layout, in the order the loader adds them. */
    val FULL_ORDER = listOf(1, 6, 7, 8, 9, 2, 3, 4, 5, 12, 13, 10, 11)

    /** The ids of the layout with only L3 and R3, in the order the loader adds them. */
    val L3_R3_ORDER = listOf(14, 15)

    const val GUIDE = 16

    /** The layout for [screen]: only L3 and R3 or all the controls, with or without Guide. */
    fun of(screen: Screen, onlyL3R3: Boolean, guide: Boolean): List<Placed> {
        val table = if (onlyL3R3) L3_R3 else FULL
        val placed = table.getValue(screen)
        return if (guide) placed else placed.filter { it.id != GUIDE }
    }

    private fun placed(vararg rows: IntArray) = rows.map { Placed(it[0], it[1], it[2], it[3], it[4]) }

    private fun row(vararg values: Int) = values

    private val FULL =
        mapOf(
            // Displacement 420.
            OscRig.PHONE to
                placed(
                    row(1, 60, 615, 450, 450),
                    row(6, 2010, 315, 150, 150),
                    row(7, 2160, 165, 150, 150),
                    row(8, 1860, 165, 150, 150),
                    row(9, 2010, 15, 150, 150),
                    row(2, 15, 465, 180, 135),
                    row(3, 2145, 465, 180, 135),
                    row(4, 360, 465, 180, 135),
                    row(5, 1800, 465, 180, 135),
                    row(12, 90, 60, 390, 390),
                    row(13, 1890, 630, 390, 390),
                    row(10, 510, 960, 180, 105),
                    row(11, 1665, 960, 180, 105),
                    row(16, 1155, 960, 180, 105),
                ),
            // Displacement 0.
            OscRig.TV to
                placed(
                    row(1, 60, 615, 450, 450),
                    row(6, 1590, 315, 150, 150),
                    row(7, 1740, 165, 150, 150),
                    row(8, 1440, 165, 150, 150),
                    row(9, 1590, 15, 150, 150),
                    row(2, 15, 465, 180, 135),
                    row(3, 1725, 465, 180, 135),
                    row(4, 360, 465, 180, 135),
                    row(5, 1380, 465, 180, 135),
                    row(12, 90, 60, 390, 390),
                    row(13, 1470, 630, 390, 390),
                    row(10, 510, 960, 180, 105),
                    row(11, 1245, 960, 180, 105),
                    row(16, 735, 960, 180, 105),
                ),
            // Displacement -682.
            OscRig.TABLET_4_3 to
                placed(
                    row(1, 85, 874, 640, 640),
                    row(6, 1579, 448, 213, 213),
                    row(7, 1792, 234, 213, 213),
                    row(8, 1366, 234, 213, 213),
                    row(9, 1579, 21, 213, 213),
                    row(2, 21, 661, 256, 192),
                    row(3, 1771, 661, 256, 192),
                    row(4, 512, 661, 256, 192),
                    row(5, 1280, 661, 256, 192),
                    row(12, 128, 85, 554, 554),
                    row(13, 1408, 896, 554, 554),
                    row(10, 725, 1365, 256, 149),
                    row(11, 1088, 1365, 256, 149),
                    row(16, 363, 1365, 256, 149),
                ),
            // Displacement -284.
            OscRig.TABLET_16_10 to
                placed(
                    row(1, 88, 911, 666, 666),
                    row(6, 2071, 466, 222, 222),
                    row(7, 2293, 244, 222, 222),
                    row(8, 1849, 244, 222, 222),
                    row(9, 2071, 22, 222, 222),
                    row(2, 22, 688, 266, 200),
                    row(3, 2271, 688, 266, 200),
                    row(4, 533, 688, 266, 200),
                    row(5, 1760, 688, 266, 200),
                    row(12, 133, 88, 577, 577),
                    row(13, 1893, 933, 577, 577),
                    row(10, 755, 1422, 266, 155),
                    row(11, 1560, 1422, 266, 155),
                    row(16, 804, 1422, 266, 155),
                ),
            // Displacement -213. Pad 499 and L3/R3 at 999: the order of the float operations shows.
            OscRig.WUXGA to
                placed(
                    row(1, 66, 683, 499, 499),
                    row(6, 1553, 350, 166, 166),
                    row(7, 1720, 183, 166, 166),
                    row(8, 1387, 183, 166, 166),
                    row(9, 1553, 16, 166, 166),
                    row(2, 16, 516, 200, 150),
                    row(3, 1703, 516, 200, 150),
                    row(4, 400, 516, 200, 150),
                    row(5, 1320, 516, 200, 150),
                    row(12, 100, 66, 433, 433),
                    row(13, 1420, 700, 433, 433),
                    row(10, 566, 1066, 200, 116),
                    row(11, 1170, 1066, 200, 116),
                    row(16, 603, 1066, 200, 116),
                ),
        )

    private val L3_R3 =
        mapOf(
            OscRig.PHONE to
                placed(
                    row(14, 15, 900, 180, 135),
                    row(15, 2145, 900, 180, 135),
                    row(16, 1155, 960, 180, 105),
                ),
            OscRig.TV to
                placed(
                    row(14, 15, 900, 180, 135),
                    row(15, 1725, 900, 180, 135),
                    row(16, 735, 960, 180, 105),
                ),
            OscRig.TABLET_4_3 to
                placed(
                    row(14, 21, 1280, 256, 192),
                    row(15, 1771, 1280, 256, 192),
                    row(16, 363, 1365, 256, 149),
                ),
            OscRig.TABLET_16_10 to
                placed(
                    row(14, 22, 1333, 266, 200),
                    row(15, 2271, 1333, 266, 200),
                    row(16, 804, 1422, 266, 155),
                ),
            OscRig.WUXGA to
                placed(
                    row(14, 16, 999, 200, 150),
                    row(15, 1703, 999, 200, 150),
                    row(16, 603, 1066, 200, 116),
                ),
        )
}

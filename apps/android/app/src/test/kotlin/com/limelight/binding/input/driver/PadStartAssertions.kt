package com.limelight.binding.input.driver

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.testing.LimeLogCapture

/** Checks that the pad does not start: it writes nothing, and logs [warning] and nothing else. */
internal fun Pad<*>.assertStartFails(log: LimeLogCapture, warning: String) {
    assertThat(start()).isFalse()
    assertThat(usb.writes).isEmpty()
    assertThat(log.warnings()).containsExactly(warning)
}

package io.github.magalz.borealis.stream.testing

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import org.junit.Test

class FixturesTest {
    @Test
    fun `a fixture session starts with no background, which would animate`() {
        assertThat(aDefaults.visuals.backgroundStyle).isEqualTo(BackgroundStyle.Off)
        assertThat(aSession().state.value.visuals.backgroundStyle).isEqualTo(BackgroundStyle.Off)
    }
}

package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.Assert.assertThrows
import org.junit.Test

class BorealisNativeBuildTypeTest {
    @Test
    fun `a debug configure task maps to the debug native build`() {
        assertThat(nativeBuildTypeForConfigureTask("configureNdkBuildDebug")).isEqualTo("Debug")
        assertThat(nativeBuildTypeForConfigureTask("configureNdkBuildDebug[arm64-v8a]"))
            .isEqualTo("Debug")
    }

    @Test
    fun `a release configure task maps to the release native build`() {
        assertThat(nativeBuildTypeForConfigureTask("configureNdkBuildRelease")).isEqualTo("Release")
        assertThat(nativeBuildTypeForConfigureTask("configureNdkBuildRelease[x86_64]"))
            .isEqualTo("Release")
    }

    @Test
    fun `an unknown build type fails loudly instead of defaulting to release`() {
        val error =
            assertThrows(GradleException::class.java) {
                nativeBuildTypeForConfigureTask("configureNdkBuildProfile")
            }

        assertThat(error).hasMessageThat().contains("configureNdkBuildProfile")
    }
}

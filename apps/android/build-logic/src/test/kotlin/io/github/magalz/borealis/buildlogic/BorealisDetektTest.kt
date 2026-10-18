package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BorealisDetektTest {
    @get:Rule val folder = TemporaryFolder()

    /** `:borealis:host` of a Gradle root, without the Android plugin (it needs an SDK). */
    private fun module(): Project {
        val root = ProjectBuilder.builder().withProjectDir(folder.newFolder("android")).build()
        val group = ProjectBuilder.builder().withName("borealis").withParent(root).build()
        val host = ProjectBuilder.builder().withName("host").withParent(group).build()
        with(BorealisDetekt) { host.configureDetekt() }
        return host
    }

    @Test
    fun `the detekt task of a module waits for the typed analysis of its sources and tests`() {
        val detekt = module().tasks.getByName("detekt")

        assertThat(detekt.dependsOn).containsAtLeast("detektMain", "detektTest")
    }

    @Test
    fun `the typed analysis skips the release build, which has the same sources`() {
        val extension = module().extensions.getByType(DetektExtension::class.java)

        assertThat(extension.ignoredBuildTypes.get()).contains("release")
    }

    @Test
    fun `every module reads the one config`() {
        val project = module()
        val extension = project.extensions.getByType(DetektExtension::class.java)

        assertThat(extension.config.files)
            .containsExactly(project.rootDir.resolve("config/detekt/detekt.yml"))
        assertThat(extension.buildUponDefaultConfig.get()).isTrue()
    }

    @Test
    fun `no module hides findings behind a baseline`() {
        val extension = module().extensions.getByType(DetektExtension::class.java)

        assertThat(extension.baseline.orNull?.asFile?.exists() ?: false).isFalse()
        assertThat(extension.baseline.orNull?.asFile?.path.orEmpty()).doesNotContain("config")
    }
}

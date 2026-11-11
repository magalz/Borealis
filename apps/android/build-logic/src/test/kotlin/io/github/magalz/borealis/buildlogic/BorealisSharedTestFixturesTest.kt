package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BorealisSharedTestFixturesTest {
    @get:Rule val folder = TemporaryFolder()

    /** The `:app` of a Gradle root with the modules whose fixtures it uses, without Android. */
    private fun app(): Project {
        val root = ProjectBuilder.builder().withProjectDir(folder.newFolder("android")).build()
        val group = ProjectBuilder.builder().withName("borealis").withParent(root).build()
        for (name in listOf("designsystem", "logging")) {
            ProjectBuilder.builder().withName(name).withParent(group).build()
        }
        val app = ProjectBuilder.builder().withName("app").withParent(root).build()
        app.configurations.create("testImplementation")
        app.addSharedTestFixtures()
        return app
    }

    /** The modules whose test fixtures a configuration asks for. */
    private fun Project.fixturesOf(configuration: String): List<String> =
        configurations
            .getByName(configuration)
            .dependencies
            .filterIsInstance<ProjectDependency>()
            .filter { dependency ->
                dependency.requestedCapabilities.any { it.name.endsWith("-test-fixtures") }
            }
            .map { it.name }

    @Test
    fun `the unit tests of the app get the fixtures of the design system and of the logging module`() {
        assertThat(app().fixturesOf("testImplementation"))
            .containsExactly("designsystem", "logging")
    }
}

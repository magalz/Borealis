package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BorealisUnitTestHeapTest {
    @get:Rule val folder = TemporaryFolder()

    /** The heap of the unit-test task of the module at [path] under a Gradle root, e.g. `app`. */
    private fun heapOf(path: String): String? {
        var project = ProjectBuilder.builder().withProjectDir(folder.newFolder("android")).build()
        for (name in path.split(":")) {
            project = ProjectBuilder.builder().withName(name).withParent(project).build()
        }
        val test = project.tasks.register("testDebugUnitTest", Test::class.java)

        project.configureUnitTestHeap()

        return test.get().maxHeapSize
    }

    @org.junit.Test
    fun `the app unit tests run with a heap that holds the robolectric sandboxes`() {
        assertThat(heapOf("app")).isEqualTo("2g")
    }

    @org.junit.Test
    fun `the library unit tests run with a heap that holds their measured peak`() {
        assertThat(heapOf("borealis:library")).isEqualTo("1g")
    }

    @org.junit.Test
    fun `the settings unit tests run with a heap that holds their measured peak`() {
        assertThat(heapOf("borealis:settings")).isEqualTo("1536m")
    }

    @org.junit.Test
    fun `a module without a measured peak keeps the default heap of Gradle`() {
        assertThat(heapOf("borealis:host")).isNull()
    }
}

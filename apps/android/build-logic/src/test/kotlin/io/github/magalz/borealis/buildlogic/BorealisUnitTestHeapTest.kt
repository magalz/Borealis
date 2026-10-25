package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BorealisUnitTestHeapTest {
    @get:Rule val folder = TemporaryFolder()

    @org.junit.Test
    fun `the app unit tests run with a heap that holds the robolectric sandboxes`() {
        val project = ProjectBuilder.builder().withProjectDir(folder.newFolder("app")).build()
        val test = project.tasks.register("testDebugUnitTest", Test::class.java)

        project.configureUnitTestHeap()

        assertThat(test.get().maxHeapSize).isEqualTo("2g")
    }
}

package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BorealisTestTempDirTest {
    @get:Rule val folder = TemporaryFolder()

    private fun module(): Pair<Project, Test> {
        val project = ProjectBuilder.builder().withProjectDir(folder.newFolder("module")).build()
        val test = project.tasks.register("testDebugUnitTest", Test::class.java)
        project.configureTestTempDir()
        return project to test.get()
    }

    private fun Project.tempDir() =
        layout.buildDirectory.get().asFile.resolve("tmp/testDebugUnitTest/jvm-tmp")

    /** Gradle names a task action "Execute <name>". */
    private fun Test.action(name: String): Action<in Task>? = actions.firstOrNull {
        (it as? Describable)?.displayName == "Execute $name"
    }

    @org.junit.Test
    fun `the test jvm writes its temporary files under the task's own build folder`() {
        val (project, test) = module()

        // Gradle passes java.io.tmpdir as a JVM argument, not with the other system properties.
        assertThat(test.allJvmArgs).contains("-Djava.io.tmpdir=${project.tempDir().absolutePath}")
    }

    @org.junit.Test
    fun `a run starts by deleting what an earlier run left`() {
        val (project, test) = module()
        val stale = project.tempDir().resolve("robolectric-Old123/libandroid_runtime.dll")
        stale.parentFile.mkdirs()
        stale.writeText("left by a run that was killed")

        val clear = test.action(CLEAR_TEST_TEMP_DIR_BEFORE)
        assertThat(clear).isNotNull()
        assertThat(test.actions.first()).isSameInstanceAs(clear)
        checkNotNull(clear).execute(test)

        assertThat(stale.exists()).isFalse()
        assertThat(project.tempDir().isDirectory).isTrue()
    }

    @org.junit.Test
    fun `a run ends by deleting its own temporary files`() {
        val (project, test) = module()
        val used = project.tempDir().resolve("robolectric-Done456/sandbox.db")
        used.parentFile.mkdirs()
        used.writeText("written by this run")

        val delete = test.action(DELETE_TEST_TEMP_DIR_AFTER)
        assertThat(delete).isNotNull()
        assertThat(test.actions.last()).isSameInstanceAs(delete)
        checkNotNull(delete).execute(test)

        assertThat(project.tempDir().exists()).isFalse()
    }
}

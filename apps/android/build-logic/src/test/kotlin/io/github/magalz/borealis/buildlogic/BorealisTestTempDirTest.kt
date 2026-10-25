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

    private fun project(name: String = "module"): Project =
        ProjectBuilder.builder().withProjectDir(folder.newFolder(name)).build()

    private fun module(windows: Boolean): Pair<Project, Test> {
        val project = project()
        val test = project.tasks.register("testDebugUnitTest", Test::class.java)
        project.configureTestTempDir(windows)
        return project to test.get()
    }

    private fun Project.buildTempDir() =
        layout.buildDirectory.get().asFile.resolve("tmp/testDebugUnitTest/jvm-tmp")

    /** Gradle names a task action "Execute <name>". */
    private fun Test.action(name: String): Action<in Task>? = actions.firstOrNull {
        (it as? Describable)?.displayName == "Execute $name"
    }

    @org.junit.Test
    fun `outside windows the test jvm writes its temporary files under the task's build folder`() {
        val (project, test) = module(windows = false)

        // Gradle passes java.io.tmpdir as a JVM argument, not with the other system properties.
        assertThat(test.allJvmArgs)
            .contains("-Djava.io.tmpdir=${project.buildTempDir().absolutePath}")
    }

    @org.junit.Test
    fun `on windows the folder is short, at the root of the checkout's drive`() {
        val project = project()
        val dir = project.testTempDir("testDebugUnitTest", windows = true)

        // Robolectric's SQLite paths under a deep build folder pass the 260 characters of Windows.
        assertThat(dir.parentFile).isEqualTo(project.rootDir.toPath().root.toFile().resolve(".bt"))
        assertThat(dir.name).matches("[0-9a-f]{8}")
    }

    @org.junit.Test
    fun `on windows each checkout, module and task gets a folder of its own`() {
        val first = project("first")
        val second = project("second")

        val dirs =
            setOf(
                first.testTempDir("testDebugUnitTest", windows = true),
                first.testTempDir("testReleaseUnitTest", windows = true),
                second.testTempDir("testDebugUnitTest", windows = true),
            )

        assertThat(dirs).hasSize(3)
        assertThat(first.testTempDir("testDebugUnitTest", windows = true))
            .isEqualTo(first.testTempDir("testDebugUnitTest", windows = true))
    }

    @org.junit.Test
    fun `on windows the test jvm uses the short folder`() {
        val (project, test) = module(windows = true)
        val dir = project.testTempDir("testDebugUnitTest", windows = true)

        assertThat(dir.parentFile.name).isEqualTo(".bt")
        assertThat(test.allJvmArgs).contains("-Djava.io.tmpdir=${dir.absolutePath}")
    }

    @org.junit.Test
    fun `a run starts by deleting what an earlier run left`() {
        val (project, test) = module(windows = false)
        val stale = project.buildTempDir().resolve("robolectric-Old123/libandroid_runtime.dll")
        stale.parentFile.mkdirs()
        stale.writeText("left by a run that was killed")

        val clear = test.action(CLEAR_TEST_TEMP_DIR_BEFORE)
        assertThat(clear).isNotNull()
        assertThat(test.actions.first()).isSameInstanceAs(clear)
        checkNotNull(clear).execute(test)

        assertThat(stale.exists()).isFalse()
        assertThat(project.buildTempDir().isDirectory).isTrue()
    }

    @org.junit.Test
    fun `a run ends by deleting its own temporary files`() {
        val (project, test) = module(windows = false)
        val used = project.buildTempDir().resolve("robolectric-Done456/sandbox.db")
        used.parentFile.mkdirs()
        used.writeText("written by this run")

        val delete = test.action(DELETE_TEST_TEMP_DIR_AFTER)
        assertThat(delete).isNotNull()
        assertThat(test.actions.last()).isSameInstanceAs(delete)
        checkNotNull(delete).execute(test)

        assertThat(project.buildTempDir().exists()).isFalse()
    }
}

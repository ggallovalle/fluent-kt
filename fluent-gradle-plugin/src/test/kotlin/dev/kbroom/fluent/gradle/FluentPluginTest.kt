package dev.kbroom.fluent.gradle

import de.infix.testBalloon.framework.core.testSuite
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

val FluentPluginTest by testSuite {
    test("applies tasks and extension") {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.ggallovalle.fluent")

        assertNotNull(project.extensions.findByName("fluent"))
        assertNotNull(project.tasks.findByName("fluentValidate"))
        assertNotNull(project.tasks.findByName("fluentGenerate"))
        assertNotNull(project.tasks.findByName("fluentScaffoldLocale"))

        val generate = project.tasks.getByName("fluentGenerate")
        val deps = generate.taskDependencies.getDependencies(generate)
        assertTrue(deps.any { it.name == "fluentValidate" })
    }
}

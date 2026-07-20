package dev.kbroom.fluent.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Android-only source wiring. Kept in a separate class so [FluentPlugin] does not
 * hard-depend on AGP at class-load time (AGP is `compileOnly`).
 */
internal object AndroidFluentSourceWiring {
    @Suppress("UNCHECKED_CAST")
    fun wire(project: Project, generate: TaskProvider<FluentGenerateTask>) {
        val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
            as AndroidComponentsExtension<*, *, *>
        components.onVariants { variant: Variant ->
            variant.sources.kotlin?.addGeneratedSourceDirectory(
                generate,
                FluentGenerateTask::outputDir,
            )
        }
    }
}

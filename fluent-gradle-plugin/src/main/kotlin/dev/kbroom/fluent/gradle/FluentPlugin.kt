package dev.kbroom.fluent.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies Fluent FTL validate / generate / scaffold tasks and wires generated
 * sources into Kotlin JVM or Multiplatform compilations when present.
 */
class FluentPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("fluent", FluentExtension::class.java)
        extension.outputDir.convention(
            project.layout.buildDirectory.dir("generated/fluent/kotlin"),
        )
        val generate = registerTasks(project, extension)
        project.plugins.withId("com.android.application") {
            wireAndroidGeneratedSources(project, generate)
        }
        project.plugins.withId("com.android.library") {
            wireAndroidGeneratedSources(project, generate)
        }
        project.afterEvaluate {
            if (extension.sourceDirs.isEmpty) {
                extension.sourceDirs.from(
                    project.layout.projectDirectory.dir("src/main/resources/i18n"),
                )
            }
            wireNonAndroidGeneratedSources(project, extension, generate)
        }
    }

    private fun registerTasks(
        project: Project,
        extension: FluentExtension,
    ): TaskProvider<FluentGenerateTask> {
        val validate = project.tasks.register(
            "fluentValidate",
            FluentValidateTask::class.java,
        ) { task ->
            task.group = "fluent"
            task.description = "Validate Fluent FTL trees (Junk + cross-locale parity)"
            task.sourceDirs.from(extension.sourceDirs)
            task.layoutName.set(extension.layout.map { it.name })
            task.defaultLocale.set(extension.defaultLocale)
            task.strictJunk.set(extension.strictJunk)
            task.checkMissingTranslations.set(extension.checkMissingTranslations)
            task.checkArgParity.set(extension.checkArgParity)
        }

        val generate = project.tasks.register(
            "fluentGenerate",
            FluentGenerateTask::class.java,
        ) { task ->
            task.group = "fluent"
            task.description = "Generate typed Fluent Kotlin accessors from the default locale"
            task.sourceDirs.from(extension.sourceDirs)
            task.outputDir.set(extension.outputDir)
            task.layoutName.set(extension.layout.map { it.name })
            task.defaultLocale.set(extension.defaultLocale)
            task.packageName.set(extension.packageName)
            task.generateTypedAccessors.set(extension.generateTypedAccessors)
            task.generateIdConstants.set(extension.generateIdConstants)
            task.generateResourceIds.set(extension.generateResourceIds)
            task.generateL10n.set(extension.generateL10n)
            task.generateKdoc.set(extension.generateKdoc)
            task.generateComposeAccessors.set(extension.generateComposeAccessors)
            task.strictJunk.set(extension.strictJunk)
            task.dependsOn(validate)
        }

        project.tasks.register(
            "fluentScaffoldLocale",
            FluentScaffoldLocaleTask::class.java,
        ) { task ->
            task.group = "fluent"
            task.description = "Scaffold a new locale tree from the default locale"
            task.sourceDirs.from(extension.sourceDirs)
            task.layoutName.set(extension.layout.map { it.name })
            task.fromLocale.set(extension.defaultLocale)
            task.modeName.set(extension.scaffold.mode.map { it.name })
            task.overwrite.set(extension.scaffold.overwrite)
            task.toLocale.convention(extension.scaffold.to)
        }
        return generate
    }

    private fun wireNonAndroidGeneratedSources(
        project: Project,
        extension: FluentExtension,
        generate: TaskProvider<FluentGenerateTask>,
    ) {
        val generated = extension.outputDir

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = project.extensions.findByType(KotlinJvmProjectExtension::class.java)
            kotlin?.sourceSets?.getByName("main")?.kotlin?.srcDir(generated)
            project.tasks.named("compileKotlin").configure { task ->
                task.dependsOn(generate)
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            kotlin?.sourceSets?.findByName("commonMain")?.kotlin?.srcDir(generated)
            kotlin?.sourceSets?.findByName("jvmMain")?.kotlin?.srcDir(generated)
            project.tasks.matching { task -> task.name.startsWith("compileKotlin") }.configureEach { task ->
                task.dependsOn(generate)
            }
        }

        project.plugins.withId("java") {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            sourceSets.named("main") { main ->
                main.java.srcDir(generated)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun wireAndroidGeneratedSources(
        project: Project,
        generate: TaskProvider<FluentGenerateTask>,
    ) {
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

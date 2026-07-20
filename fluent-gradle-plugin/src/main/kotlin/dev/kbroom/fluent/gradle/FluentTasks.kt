package dev.kbroom.fluent.gradle

import dev.kbroom.fluent.codegen.FluentCodegen
import dev.kbroom.fluent.codegen.model.GenerateOptions
import dev.kbroom.fluent.codegen.model.ScaffoldOptions
import dev.kbroom.fluent.codegen.model.ValidateOptions
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.nio.file.Path

abstract class FluentValidateTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sourceDirs: ConfigurableFileCollection

    @get:Input
    abstract val layoutName: Property<String>

    @get:Input
    abstract val defaultLocale: Property<String>

    @get:Input
    abstract val strictJunk: Property<Boolean>

    @get:Input
    abstract val checkMissingTranslations: Property<Boolean>

    @get:Input
    abstract val checkArgParity: Property<Boolean>

    @TaskAction
    fun validate() {
        val report = FluentCodegen.validate(
            sourceDirs = sourceDirs.files.map { it.toPath() },
            layout = enumValueOf(layoutName.get()),
            defaultLocale = defaultLocale.get(),
            options = ValidateOptions(
                strictJunk = strictJunk.get(),
                checkMissingTranslations = checkMissingTranslations.get(),
                checkArgParity = checkArgParity.get(),
            ),
        )
        report.issues.forEach { issue ->
            val prefix = "[${issue.severity}]"
            logger.lifecycle("$prefix ${issue.message}")
        }
        if (!report.isOk) {
            throw GradleException(
                "fluentValidate failed with ${report.errors.size} error(s)",
            )
        }
    }
}

abstract class FluentGenerateTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sourceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val layoutName: Property<String>

    @get:Input
    abstract val defaultLocale: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val generateTypedAccessors: Property<Boolean>

    @get:Input
    abstract val generateIdConstants: Property<Boolean>

    @get:Input
    abstract val generateResourceIds: Property<Boolean>

    @get:Input
    abstract val generateL10n: Property<Boolean>

    @get:Input
    abstract val generateKdoc: Property<Boolean>

    @get:Input
    abstract val strictJunk: Property<Boolean>

    @TaskAction
    fun generate() {
        val out: Path = outputDir.get().asFile.toPath()
        val written = FluentCodegen.generate(
            sourceDirs = sourceDirs.files.map { it.toPath() },
            layout = enumValueOf(layoutName.get()),
            defaultLocale = defaultLocale.get(),
            outputDir = out,
            options = GenerateOptions(
                packageName = packageName.get(),
                generateTypedAccessors = generateTypedAccessors.get(),
                generateIdConstants = generateIdConstants.get(),
                generateResourceIds = generateResourceIds.get(),
                generateKdoc = generateKdoc.get(),
                generateL10n = generateL10n.get(),
            ),
            validateOptions = ValidateOptions(
                strictJunk = strictJunk.get(),
                checkMissingTranslations = false,
                checkArgParity = false,
            ),
        )
        logger.lifecycle("fluentGenerate wrote ${written.size} file(s) to $out")
    }
}

abstract class FluentScaffoldLocaleTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirs: ConfigurableFileCollection

    @get:Input
    abstract val layoutName: Property<String>

    @get:Input
    abstract val fromLocale: Property<String>

    @get:Input
    @get:Option(option = "locale", description = "Target locale to scaffold (e.g. es-MX)")
    abstract val toLocale: Property<String>

    @get:Input
    abstract val modeName: Property<String>

    @get:Input
    abstract val overwrite: Property<Boolean>

    @TaskAction
    fun scaffold() {
        val to = toLocale.orNull
            ?: project.findProperty("fluent.scaffold.to")?.toString()
            ?: throw GradleException(
                "Specify target locale: ./gradlew fluentScaffoldLocale --locale=es-MX " +
                    "or -Pfluent.scaffold.to=es-MX",
            )
        val report = FluentCodegen.scaffoldLocale(
            sourceDirs = sourceDirs.files.map { it.toPath() },
            layout = enumValueOf(layoutName.get()),
            fromLocale = fromLocale.get(),
            toLocale = to,
            options = ScaffoldOptions(
                mode = enumValueOf(modeName.get()),
                overwrite = overwrite.get(),
            ),
        )
        logger.lifecycle(
            "fluentScaffoldLocale: created=${report.created.size} " +
                "skipped=${report.skipped.size} failed=${report.failed.size}",
        )
        report.created.forEach { logger.lifecycle("  created $it") }
        report.skipped.forEach { logger.lifecycle("  skipped $it") }
        report.failed.forEach { (path, err) -> logger.error("  failed $path: $err") }
        if (report.failed.isNotEmpty()) {
            throw GradleException("fluentScaffoldLocale failed for ${report.failed.size} file(s)")
        }
    }
}

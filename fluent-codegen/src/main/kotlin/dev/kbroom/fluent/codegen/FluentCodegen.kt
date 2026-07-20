package dev.kbroom.fluent.codegen

import dev.kbroom.fluent.codegen.discovery.LayoutDiscovery
import dev.kbroom.fluent.codegen.emit.KotlinEmitter
import dev.kbroom.fluent.codegen.model.FluentLayout
import dev.kbroom.fluent.codegen.model.GenerateOptions
import dev.kbroom.fluent.codegen.model.ScaffoldOptions
import dev.kbroom.fluent.codegen.model.ScaffoldReport
import dev.kbroom.fluent.codegen.model.ValidateOptions
import dev.kbroom.fluent.codegen.model.ValidationReport
import dev.kbroom.fluent.codegen.scaffold.LocaleScaffolder
import dev.kbroom.fluent.codegen.validate.LocaleValidator
import java.nio.file.Files
import java.nio.file.Path

/**
 * High-level entry points used by the Gradle plugin (and a future CLI).
 */
object FluentCodegen {
    fun validate(
        sourceDirs: List<Path>,
        layout: FluentLayout,
        defaultLocale: String,
        options: ValidateOptions = ValidateOptions(),
    ): ValidationReport {
        val issues = sourceDirs.flatMap { dir ->
            val tree = LayoutDiscovery.discover(dir, layout)
            LocaleValidator.validate(tree, defaultLocale, options).issues
        }
        return ValidationReport(issues)
    }

    /**
     * Generate Kotlin sources into [outputDir].
     *
     * @return relative paths written
     */
    fun generate(
        sourceDirs: List<Path>,
        layout: FluentLayout,
        defaultLocale: String,
        outputDir: Path,
        options: GenerateOptions,
        validateOptions: ValidateOptions = ValidateOptions(),
    ): List<String> {
        val allBundles = sourceDirs.flatMap { dir ->
            val tree = LayoutDiscovery.discover(dir, layout)
            val (models, report) = LocaleValidator.loadReferenceModels(tree, defaultLocale, validateOptions)
            check(report.isOk) {
                report.errors.joinToString("\n") { it.message }
            }
            models
        }
        // Merge duplicate bundle names across sourceDirs (later wins by message id union check).
        val merged = allBundles.groupBy { it.name }.map { (name, group) ->
            if (group.size == 1) {
                group.first()
            } else {
                val messages = group.flatMap { it.messages }
                val ids = messages.map { it.id }
                check(ids.size == ids.toSet().size) {
                    "Duplicate message ids when merging bundle '$name' across source dirs"
                }
                group.first().copy(
                    messages = messages,
                    resourcePaths = group.flatMap { it.resourcePaths }.distinct(),
                )
            }
        }

        val files = KotlinEmitter.emit(merged, options)
        Files.createDirectories(outputDir)
        val written = mutableListOf<String>()
        for ((relative, content) in files) {
            val target = outputDir.resolve(relative)
            Files.writeString(target, content)
            written += relative
        }
        return written
    }

    fun scaffoldLocale(
        sourceDirs: List<Path>,
        layout: FluentLayout,
        fromLocale: String,
        toLocale: String,
        options: ScaffoldOptions = ScaffoldOptions(),
    ): ScaffoldReport {
        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        for (dir in sourceDirs) {
            val report = LocaleScaffolder.scaffold(dir, layout, fromLocale, toLocale, options)
            created += report.created
            skipped += report.skipped
            failed += report.failed
        }
        return ScaffoldReport(created, skipped, failed)
    }
}

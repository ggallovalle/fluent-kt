package dev.kbroom.fluent.codegen

import dev.kbroom.fluent.codegen.model.FluentLayout
import dev.kbroom.fluent.codegen.model.GenerateOptions
import java.nio.file.Path

/**
 * CLI entry used by in-repo samples (and a future fluent-cli) to run codegen
 * without requiring the Gradle plugin to be published.
 *
 * Usage:
 *   FluentCodegenMain <sourceDir> <outputDir> <packageName> [generateCompose=true|false]
 */
object FluentCodegenMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) {
            "Usage: FluentCodegenMain <sourceDir> <outputDir> <packageName> [compose]"
        }
        val sourceDir = Path.of(args[0])
        val outputDir = Path.of(args[1])
        val packageName = args[2]
        val compose = args.getOrNull(3)?.toBooleanStrictOrNull() ?: false
        val written = FluentCodegen.generate(
            sourceDirs = listOf(sourceDir),
            layout = FluentLayout.LocaleBundle,
            defaultLocale = "en-US",
            outputDir = outputDir,
            options = GenerateOptions(
                packageName = packageName,
                generateComposeAccessors = compose,
                generateL10n = false,
            ),
        )
        println("fluentGenerate wrote ${written.size} file(s) to $outputDir")
    }
}

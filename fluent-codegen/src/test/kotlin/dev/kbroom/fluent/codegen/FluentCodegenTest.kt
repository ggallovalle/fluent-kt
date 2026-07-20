package dev.kbroom.fluent.codegen

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.codegen.discovery.LayoutDiscovery
import dev.kbroom.fluent.codegen.emit.KotlinEmitter
import dev.kbroom.fluent.codegen.model.FluentLayout
import dev.kbroom.fluent.codegen.model.GenerateOptions
import dev.kbroom.fluent.codegen.model.ScaffoldMode
import dev.kbroom.fluent.codegen.model.ScaffoldOptions
import dev.kbroom.fluent.codegen.model.ValidateOptions
import dev.kbroom.fluent.codegen.scaffold.LocaleScaffolder
import dev.kbroom.fluent.codegen.validate.LocaleValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun fixtureRoot(): Path =
    Path.of(
        checkNotNull(
            Thread.currentThread().contextClassLoader.getResource("fixtures/i18n"),
        ).toURI(),
    )

private fun copyTree(from: Path, to: Path) {
    Files.walk(from).use { stream ->
        stream.forEach { source ->
            val relative = from.relativize(source)
            val target = to.resolve(relative.toString())
            if (Files.isDirectory(source)) {
                Files.createDirectories(target)
            } else {
                Files.createDirectories(target.parent)
                Files.copy(source, target)
            }
        }
    }
}

val FluentCodegenTest by testSuite {
    test("discovers LocaleBundle layout") {
        val root = fixtureRoot()
        val tree = LayoutDiscovery.discover(root, FluentLayout.LocaleBundle)
        assertEquals(listOf("en-US", "pl"), LayoutDiscovery.locales(tree))
        assertEquals(listOf("app", "errors"), LayoutDiscovery.bundles(tree, "en-US"))
        assertEquals(2, LayoutDiscovery.filesFor(tree, "en-US", "app").size)
    }

    test("validates matching locales") {
        val tree = LayoutDiscovery.discover(fixtureRoot(), FluentLayout.LocaleBundle)
        val report = LocaleValidator.validate(tree, "en-US")
        assertTrue(report.isOk, report.issues.joinToString { it.message })
    }

    test("detects missing translation") {
        val tmp = Files.createTempDirectory("fluent-codegen-missing")
        copyTree(fixtureRoot(), tmp)
        Files.delete(tmp.resolve("pl/errors/messages.ftl"))
        Files.delete(tmp.resolve("pl/errors"))
        val tree = LayoutDiscovery.discover(tmp, FluentLayout.LocaleBundle)
        val report = LocaleValidator.validate(tree, "en-US")
        assertFalse(report.isOk)
        assertTrue(report.errors.any { it.message.contains("missing bundle 'errors'") })
    }

    test("emits messages with KDoc and args") {
        val tree = LayoutDiscovery.discover(fixtureRoot(), FluentLayout.LocaleBundle)
        val (models, report) = LocaleValidator.loadReferenceModels(tree, "en-US")
        assertTrue(report.isOk, report.issues.joinToString { it.message })
        val files = KotlinEmitter.emit(
            models,
            GenerateOptions(packageName = "com.example.i18n"),
        )
        val appMessages = files.getValue("AppMessages.kt")
        assertContains(appMessages, "package com.example.i18n")
        assertContains(appMessages, "Greeting shown on the home screen.")
        assertContains(appMessages, "@param name (String) user display name")
        assertContains(appMessages, "fun greeting(name: String): String")
        assertContains(appMessages, "fluentArgsOf(\"name\" to name)")
        assertContains(appMessages, "fun loginBtnAriaLabel(): String")
        assertContains(appMessages, "formatAttribute(\"login-btn\", \"aria-label\")")

        val appL10n = files.getValue("AppL10n.kt")
        assertContains(appL10n, "class AppL10n")
        assertContains(appL10n, "l10n.format(\"greeting\"")

        val resources = files.getValue("AppResources.kt")
        assertContains(resources, "ResourceId(\"app/messages\"")
        assertContains(resources, "ResourceId(\"app/buttons\"")

        val ids = files.getValue("FtlIds.kt")
        assertContains(ids, "const val GREETING = \"greeting\"")
        assertContains(ids, "object Errors")
    }

    test("emits Compose remember accessors when enabled") {
        val tree = LayoutDiscovery.discover(fixtureRoot(), FluentLayout.LocaleBundle)
        val (models, report) = LocaleValidator.loadReferenceModels(tree, "en-US")
        assertTrue(report.isOk, report.issues.joinToString { it.message })
        val files = KotlinEmitter.emit(
            models,
            GenerateOptions(
                packageName = "com.example.i18n",
                generateComposeAccessors = true,
            ),
        )
        val compose = files.getValue("AppComposeAccessors.kt")
        assertContains(compose, "fun rememberAppMessages(): AppMessages")
        assertContains(compose, "LocalFluentBundles.current.get(\"app\")")
        assertContains(files.getValue("ErrorsComposeAccessors.kt"), "rememberErrorsMessages")
    }

    test("generate writes files") {
        val out = Files.createTempDirectory("fluent-codegen-out")
        val written = FluentCodegen.generate(
            sourceDirs = listOf(fixtureRoot()),
            layout = FluentLayout.LocaleBundle,
            defaultLocale = "en-US",
            outputDir = out,
            options = GenerateOptions(packageName = "com.example.i18n"),
        )
        assertTrue(written.contains("AppMessages.kt"))
        assertTrue(Files.exists(out.resolve("AppMessages.kt")))
        assertTrue(Files.exists(out.resolve("ErrorsL10n.kt")))
    }

    test("scaffolds locale CopyAsPlaceholder") {
        val tmp = Files.createTempDirectory("fluent-codegen-scaffold")
        copyTree(fixtureRoot(), tmp)
        val report = LocaleScaffolder.scaffold(
            root = tmp,
            layout = FluentLayout.LocaleBundle,
            fromLocale = "en-US",
            toLocale = "es-MX",
            options = ScaffoldOptions(mode = ScaffoldMode.CopyAsPlaceholder),
        )
        assertTrue(report.failed.isEmpty(), report.failed.toString())
        assertTrue(report.created.any { it.startsWith("es-MX/") })
        val greeting = Files.readString(tmp.resolve("es-MX/app/messages.ftl"))
        assertContains(greeting, "greeting")
        assertContains(greeting, "Hello")
    }

    test("scaffolds StructureOnly") {
        val rewritten = LocaleScaffolder.rewrite(
            source = $$"hello = Hello, { $name }!\n",
            toLocale = "es-MX",
            mode = ScaffoldMode.StructureOnly,
        )
        assertContains(rewritten, "TODO")
        assertContains(rewritten, $$"$name")
        assertFalse(rewritten.contains("Hello"))
    }

    test("scaffolds PseudoPrefix") {
        val rewritten = LocaleScaffolder.rewrite(
            source = "hello = Hello\n",
            toLocale = "es-MX",
            mode = ScaffoldMode.PseudoPrefix,
        )
        assertContains(rewritten, "[es-MX] Hello")
    }

    test("scaffold skips existing unless overwrite") {
        val tmp = Files.createTempDirectory("fluent-codegen-skip")
        copyTree(fixtureRoot(), tmp)
        LocaleScaffolder.scaffold(tmp, FluentLayout.LocaleBundle, "en-US", "es-MX")
        val second = LocaleScaffolder.scaffold(tmp, FluentLayout.LocaleBundle, "en-US", "es-MX")
        assertTrue(second.created.isEmpty())
        assertTrue(second.skipped.isNotEmpty())
    }

    test("kotlin names") {
        assertEquals("loginBtn", KotlinNames.toCamelCase("login-btn"))
        assertEquals("LOGIN_BTN", KotlinNames.toConstCase("login-btn"))
        assertEquals("App", KotlinNames.toPascalCase("app"))
        assertEquals("String", KotlinNames.kotlinType("String"))
        assertEquals("Any?", KotlinNames.kotlinType(""))
    }

    test("duplicate message ids fail") {
        val tmp = Files.createTempDirectory("fluent-codegen-dup")
        val dir = tmp.resolve("en-US/app")
        dir.createDirectories()
        dir.resolve("a.ftl").writeText("hello = A\n")
        dir.resolve("b.ftl").writeText("hello = B\n")
        val tree = LayoutDiscovery.discover(tmp, FluentLayout.LocaleBundle)
        val (_, report) = LocaleValidator.loadReferenceModels(
            tree,
            "en-US",
            ValidateOptions(strictJunk = true),
        )
        assertFalse(report.isOk)
        assertTrue(report.errors.any { it.message.contains("Duplicate message id") })
    }
}

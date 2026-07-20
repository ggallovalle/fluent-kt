package dev.kbroom.fluent.codegen.discovery

import dev.kbroom.fluent.codegen.model.FluentLayout
import dev.kbroom.fluent.codegen.model.FtlFile
import dev.kbroom.fluent.codegen.model.LocaleTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * Discovers FTL files under a source root according to [FluentLayout].
 */
object LayoutDiscovery {
    fun discover(root: Path, layout: FluentLayout): LocaleTree {
        if (!root.isDirectory()) {
            return LocaleTree(root, emptyList())
        }
        val files = when (layout) {
            FluentLayout.LocaleBundle -> discoverLocaleBundle(root)
            FluentLayout.BundleLocale -> discoverBundleLocale(root)
            FluentLayout.FlatLocale -> discoverFlatLocale(root)
        }
        return LocaleTree(root, files.sortedBy { it.absolutePath.toString() })
    }

    fun locales(tree: LocaleTree): List<String> =
        tree.files.map { it.locale }.distinct().sorted()

    fun bundles(tree: LocaleTree, locale: String): List<String> =
        tree.files.filter { it.locale == locale }.map { it.bundle }.distinct().sorted()

    fun filesFor(tree: LocaleTree, locale: String, bundle: String): List<FtlFile> =
        tree.files.filter { it.locale == locale && it.bundle == bundle }

    private fun discoverLocaleBundle(root: Path): List<FtlFile> {
        val out = mutableListOf<FtlFile>()
        Files.list(root).use { locales ->
            locales.filter { it.isDirectory() }.forEach { localeDir ->
                val locale = localeDir.name
                walkFtl(localeDir).forEach { file ->
                    val relative = file.relativeTo(localeDir).toString().replace('\\', '/')
                    val bundle = relative.substringBefore('/', missingDelimiterValue = "default")
                    out.add(
                        FtlFile(
                            absolutePath = file,
                            locale = locale,
                            bundle = bundle,
                            relativePath = relative,
                        ),
                    )
                }
            }
        }
        return out
    }

    private fun discoverBundleLocale(root: Path): List<FtlFile> {
        val out = mutableListOf<FtlFile>()
        listDirectories(root).forEach { bundleDir ->
            val bundle = bundleDir.name
            listDirectories(bundleDir).forEach { localeDir ->
                collectLocaleFiles(localeDir, localeDir.name, bundle, out) { underLocale ->
                    "$bundle/$underLocale"
                }
            }
        }
        return out
    }

    private fun listDirectories(root: Path): List<Path> {
        if (!root.isDirectory()) return emptyList()
        val result = mutableListOf<Path>()
        Files.list(root).use { stream ->
            stream.filter { it.isDirectory() }.forEach { result.add(it) }
        }
        return result
    }

    private fun collectLocaleFiles(
        localeDir: Path,
        locale: String,
        bundle: String,
        out: MutableList<FtlFile>,
        relativePath: (String) -> String,
    ) {
        walkFtl(localeDir).forEach { file ->
            val underLocale = file.relativeTo(localeDir).toString().replace('\\', '/')
            out.add(
                FtlFile(
                    absolutePath = file,
                    locale = locale,
                    bundle = bundle,
                    relativePath = relativePath(underLocale),
                ),
            )
        }
    }

    private fun discoverFlatLocale(root: Path): List<FtlFile> {
        val out = mutableListOf<FtlFile>()
        Files.list(root).use { locales ->
            locales.filter { it.isDirectory() }.forEach { localeDir ->
                val locale = localeDir.name
                walkFtl(localeDir).forEach { file ->
                    val relative = file.relativeTo(localeDir).toString().replace('\\', '/')
                    out.add(
                        FtlFile(
                            absolutePath = file,
                            locale = locale,
                            bundle = "default",
                            relativePath = relative,
                        ),
                    )
                }
            }
        }
        return out
    }

    private fun walkFtl(dir: Path): List<Path> {
        val out = mutableListOf<Path>()
        Files.walk(dir).use { stream ->
            stream.filter { it.isRegularFile() && it.extension.equals("ftl", ignoreCase = true) }
                .forEach { out.add(it) }
        }
        return out
    }
}

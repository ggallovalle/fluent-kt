package dev.kbroom.fluent.codegen.model

import java.nio.file.Path

/**
 * How FTL files are laid out under each source directory.
 */
enum class FluentLayout {
    /** `{locale}/{bundle}/**/*.ftl` */
    LocaleBundle,

    /** `{bundle}/{locale}/**/*.ftl` */
    BundleLocale,

    /** `{locale}/**/*.ftl` — single implicit bundle named `default` */
    FlatLocale,
}

/** Documentation for a generated message argument. */
data class ArgModel(val name: String, val typeHint: String = "", val description: String = "")

/** A single Fluent attribute on a message. */
data class AttributeModel(val name: String, val args: List<ArgModel> = emptyList(), val description: String = "")

/** A Fluent message (value and/or attributes) ready for codegen. */
data class MessageModel(
    val id: String,
    val description: String = "",
    val args: List<ArgModel> = emptyList(),
    val attributes: List<AttributeModel> = emptyList(),
    val hasValue: Boolean = true,
)

/**
 * One logical bundle after merging all `*.ftl` resources for a locale.
 *
 * @property name Bundle directory name (e.g. `app`)
 * @property resourcePaths Relative paths under the locale dir without `.ftl`
 *   (e.g. `app/messages`, `app/buttons`)
 */
data class BundleModel(val name: String, val messages: List<MessageModel>, val resourcePaths: List<String>)

/** A discovered FTL file on disk. */
data class FtlFile(val absolutePath: Path, val locale: String, val bundle: String, val relativePath: String)

/** Full discovery result for one source root. */
data class LocaleTree(val root: Path, val files: List<FtlFile>)

/** Options controlling Kotlin generation. */
data class GenerateOptions(
    val packageName: String,
    val generateTypedAccessors: Boolean = true,
    val generateIdConstants: Boolean = true,
    val generateResourceIds: Boolean = true,
    val generateKdoc: Boolean = true,
    val generateL10n: Boolean = true,
)

/** Options controlling validation. */
data class ValidateOptions(
    val strictJunk: Boolean = true,
    val checkMissingTranslations: Boolean = true,
    val checkArgParity: Boolean = true,
)

/** How to rewrite FTL when scaffolding a new locale. */
enum class ScaffoldMode {
    /** Round-trip the default locale AST (English text kept as placeholder). */
    CopyAsPlaceholder,

    /** Keep structure / placeables; replace text runs with `TODO`. */
    StructureOnly,

    /** Copy and prefix literal text with `[locale] `. */
    PseudoPrefix,
}

/** Options for [dev.kbroom.fluent.codegen.scaffold.LocaleScaffolder]. */
data class ScaffoldOptions(val mode: ScaffoldMode = ScaffoldMode.CopyAsPlaceholder, val overwrite: Boolean = false)

/** A single validation finding. */
data class ValidationIssue(val severity: Severity, val message: String, val path: String? = null) {
    enum class Severity { Error, Warning }
}

/** Result of validating a locale tree. */
data class ValidationReport(val issues: List<ValidationIssue>) {
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == ValidationIssue.Severity.Error }
    val isOk: Boolean get() = errors.isEmpty()
}

/** Result of scaffolding a locale. */
data class ScaffoldReport(val created: List<String>, val skipped: List<String>, val failed: List<Pair<String, String>>)

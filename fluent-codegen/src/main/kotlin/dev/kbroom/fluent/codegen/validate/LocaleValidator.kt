package dev.kbroom.fluent.codegen.validate

import dev.kbroom.fluent.codegen.discovery.LayoutDiscovery
import dev.kbroom.fluent.codegen.model.BundleModel
import dev.kbroom.fluent.codegen.model.LocaleTree
import dev.kbroom.fluent.codegen.model.ValidateOptions
import dev.kbroom.fluent.codegen.model.ValidationIssue
import dev.kbroom.fluent.codegen.model.ValidationReport
import dev.kbroom.fluent.codegen.walk.MessageModelBuilder
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.parser.FluentParser
import java.nio.file.Files

/**
 * Parses and validates a discovered locale tree.
 */
object LocaleValidator {
    fun validate(
        tree: LocaleTree,
        defaultLocale: String,
        options: ValidateOptions = ValidateOptions(),
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        val locales = LayoutDiscovery.locales(tree)
        if (locales.isEmpty()) {
            return ValidationReport(issues)
        }
        if (defaultLocale !in locales) {
            issues.add(
                ValidationIssue(
                    ValidationIssue.Severity.Error,
                    "Default locale '$defaultLocale' not found under ${tree.root}",
                ),
            )
            return ValidationReport(issues)
        }

        val modelsByLocale = loadAllModels(tree, locales, options, issues)
        if (options.checkMissingTranslations || options.checkArgParity) {
            compareLocalesToDefault(defaultLocale, modelsByLocale, options, issues)
        }
        return ValidationReport(issues)
    }

    /**
     * Build [BundleModel]s for [defaultLocale] (used by codegen).
     */
    fun loadReferenceModels(
        tree: LocaleTree,
        defaultLocale: String,
        options: ValidateOptions = ValidateOptions(),
    ): Pair<List<BundleModel>, ValidationReport> {
        val issues = mutableListOf<ValidationIssue>()
        val models = mutableListOf<BundleModel>()
        for (bundle in LayoutDiscovery.bundles(tree, defaultLocale)) {
            val model = parseBundle(tree, defaultLocale, bundle, options, issues)
            if (model != null) models.add(model)
        }
        return models to ValidationReport(issues)
    }

    private fun loadAllModels(
        tree: LocaleTree,
        locales: List<String>,
        options: ValidateOptions,
        issues: MutableList<ValidationIssue>,
    ): Map<String, Map<String, BundleModel>> {
        val modelsByLocale = linkedMapOf<String, Map<String, BundleModel>>()
        for (locale in locales) {
            val byBundle = linkedMapOf<String, BundleModel>()
            for (bundle in LayoutDiscovery.bundles(tree, locale)) {
                val parsed = parseBundle(tree, locale, bundle, options, issues) ?: continue
                byBundle[bundle] = parsed
            }
            modelsByLocale[locale] = byBundle
        }
        return modelsByLocale
    }

    private fun compareLocalesToDefault(
        defaultLocale: String,
        modelsByLocale: Map<String, Map<String, BundleModel>>,
        options: ValidateOptions,
        issues: MutableList<ValidationIssue>,
    ) {
        val reference = modelsByLocale[defaultLocale].orEmpty()
        for ((locale, bundles) in modelsByLocale) {
            if (locale == defaultLocale) continue
            compareLocaleBundles(defaultLocale, locale, reference, bundles, options, issues)
        }
    }

    private fun compareLocaleBundles(
        defaultLocale: String,
        locale: String,
        reference: Map<String, BundleModel>,
        bundles: Map<String, BundleModel>,
        options: ValidateOptions,
        issues: MutableList<ValidationIssue>,
    ) {
        for ((bundleName, refModel) in reference) {
            val other = bundles[bundleName]
            if (other == null) {
                if (options.checkMissingTranslations) {
                    issues.add(
                        ValidationIssue(
                            ValidationIssue.Severity.Error,
                            "Locale '$locale' is missing bundle '$bundleName'",
                        ),
                    )
                }
                continue
            }
            compareBundles(
                BundleCompare(
                    defaultLocale = defaultLocale,
                    locale = locale,
                    bundleName = bundleName,
                    reference = refModel,
                    other = other,
                    options = options,
                ),
                issues,
            )
        }
        for (bundleName in bundles.keys) {
            if (bundleName !in reference && options.checkMissingTranslations) {
                issues.add(
                    ValidationIssue(
                        ValidationIssue.Severity.Warning,
                        "Locale '$locale' has extra bundle '$bundleName' " +
                            "not present in default locale '$defaultLocale'",
                    ),
                )
            }
        }
    }

    private fun parseBundle(
        tree: LocaleTree,
        locale: String,
        bundle: String,
        options: ValidateOptions,
        issues: MutableList<ValidationIssue>,
    ): BundleModel? {
        val files = LayoutDiscovery.filesFor(tree, locale, bundle)
        val resources = mutableListOf<Pair<String, dev.kbroom.fluent.syntax.Resource>>()
        for (file in files) {
            val source = Files.readString(file.absolutePath)
            val parser = FluentParser()
            val resource = parser.parse(source)
            if (options.strictJunk) {
                for (entry in resource.body) {
                    if (entry is Entry.Junk) {
                        issues.add(
                            ValidationIssue(
                                ValidationIssue.Severity.Error,
                                "Junk entry in ${file.relativePath}: ${entry.content.trim().take(80)}",
                                path = file.absolutePath.toString(),
                            ),
                        )
                    }
                }
            }
            resources.add(file.relativePath to resource)
        }
        return try {
            MessageModelBuilder.fromResources(bundle, resources)
        } catch (ex: IllegalStateException) {
            issues.add(
                ValidationIssue(
                    ValidationIssue.Severity.Error,
                    ex.message ?: "Failed to build bundle model",
                ),
            )
            null
        }
    }

    private data class BundleCompare(
        val defaultLocale: String,
        val locale: String,
        val bundleName: String,
        val reference: BundleModel,
        val other: BundleModel,
        val options: ValidateOptions,
    )

    private fun compareBundles(
        ctx: BundleCompare,
        issues: MutableList<ValidationIssue>,
    ) {
        val refIds = ctx.reference.messages.associateBy { it.id }
        val otherIds = ctx.other.messages.associateBy { it.id }
        if (ctx.options.checkMissingTranslations) {
            for (id in refIds.keys) {
                if (id !in otherIds) {
                    issues.add(
                        ValidationIssue(
                            ValidationIssue.Severity.Error,
                            "Locale '${ctx.locale}' bundle '${ctx.bundleName}' missing message '$id' " +
                                "(present in '${ctx.defaultLocale}')",
                        ),
                    )
                }
            }
            for (id in otherIds.keys) {
                if (id !in refIds) {
                    issues.add(
                        ValidationIssue(
                            ValidationIssue.Severity.Warning,
                            "Locale '${ctx.locale}' bundle '${ctx.bundleName}' has extra message '$id'",
                        ),
                    )
                }
            }
        }
        if (ctx.options.checkArgParity) {
            compareArgParity(ctx.locale, ctx.bundleName, refIds, otherIds, issues)
        }
    }

    private fun compareArgParity(
        locale: String,
        bundleName: String,
        refIds: Map<String, dev.kbroom.fluent.codegen.model.MessageModel>,
        otherIds: Map<String, dev.kbroom.fluent.codegen.model.MessageModel>,
        issues: MutableList<ValidationIssue>,
    ) {
        for ((id, refMsg) in refIds) {
            val otherMsg = otherIds[id] ?: continue
            val refArgs = refMsg.args.map { it.name }.toSet()
            val otherArgs = otherMsg.args.map { it.name }.toSet()
            if (refArgs != otherArgs) {
                issues.add(
                    ValidationIssue(
                        ValidationIssue.Severity.Error,
                        "Locale '$locale' bundle '$bundleName' message '$id' args " +
                            "$otherArgs != default $refArgs",
                    ),
                )
            }
            val refAttrs = refMsg.attributes.map { it.name }.toSet()
            val otherAttrs = otherMsg.attributes.map { it.name }.toSet()
            if (refAttrs != otherAttrs) {
                issues.add(
                    ValidationIssue(
                        ValidationIssue.Severity.Error,
                        "Locale '$locale' bundle '$bundleName' message '$id' attributes " +
                            "$otherAttrs != default $refAttrs",
                    ),
                )
            }
        }
    }
}

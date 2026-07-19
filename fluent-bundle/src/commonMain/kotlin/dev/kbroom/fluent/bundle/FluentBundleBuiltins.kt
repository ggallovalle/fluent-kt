package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.bundle.types.FluentNumber
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.IntlHelpers
import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Register the built-in Fluent functions (NUMBER, PLURAL, CONCAT, SUM, IDENTITY)
 * on a [FluentBundle]. Kept outside [FluentBundle] so the main bundle class
 * doesn't grow beyond a manageable number of members.
 */
internal fun FluentBundle.addBuiltins() {
    val bundleLocales: List<LanguageIdentifier> = locales
    val bundleMemoizer: IntlLangMemoizer = memoizer

    addFunction("NUMBER") { args, _ ->
        val value = args.firstOrNull()?.asAny() as? Double
            ?: (args.firstOrNull()?.asAny() as? Int)?.toDouble()
            ?: return@addFunction FluentValue.Error("NUMBER requires a number argument")

        var style: String? = null
        var currency: String? = null
        var currencyDisplay: String? = null
        var minimumFractionDigits: Int? = null
        var maximumFractionDigits: Int? = null
        var useGrouping: Boolean? = null

        // Simple argument parsing for NUMBER
        if (args.size > 1) {
            val second = args[1].asAny() as? Map<*, *>
            if (second != null) {
                style = second["style"] as? String
                currency = second["currency"] as? String
                currencyDisplay = second["currencyDisplay"] as? String
                minimumFractionDigits = second["minimumFractionDigits"] as? Int
                maximumFractionDigits = second["maximumFractionDigits"] as? Int
                useGrouping = second["useGrouping"] as? Boolean
            }
        }

        val result = IntlHelpers.formatNumber(
            value,
            bundleLocales.first(),
            bundleMemoizer,
            style,
            currency,
            currencyDisplay,
            minimumFractionDigits,
            maximumFractionDigits,
            useGrouping,
        )
        if (result != null) FluentValue.Str(result) else FluentValue.Error("NUMBER formatting failed")
    }

    addFunction("PLURAL") { args, _ ->
        val value = args.firstOrNull()?.asAny() as? Double
            ?: (args.firstOrNull()?.asAny() as? Int)?.toDouble()
            ?: return@addFunction FluentValue.Str("other")

        val category = IntlHelpers.getPluralCategory(value, bundleLocales.first(), bundleMemoizer)
        FluentValue.Str(category)
    }

    addFunction("CONCAT") { args, _ ->
        val result = args.joinToString("") { it.asString() }
        FluentValue.Str(result)
    }

    addFunction("SUM") { args, _ ->
        val sum = args.mapNotNull {
            (it.asAny() as? Double) ?: ((it.asAny() as? Int)?.toDouble())
        }.sum()
        FluentValue.Number(FluentNumber(sum))
    }

    addFunction("IDENTITY") { args, _ ->
        args.firstOrNull() ?: FluentValue.None
    }
}

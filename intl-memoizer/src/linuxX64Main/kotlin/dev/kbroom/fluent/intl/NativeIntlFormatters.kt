package dev.kbroom.fluent.intl

import kotlin.math.pow
import kotlin.math.round as kround

/**
 * LinuxX64 (Kotlin/Native) implementation of Intl formatters.
 * Uses a fallback approach for basic formatting.
 * For full Intl support, consider using ICU4X or platform-specific bindings.
 */

/**
 * Format numbers - basic fallback implementation for LinuxX64.
 */
class LinuxX64NumberFormatter(
    private val locale: LanguageIdentifier,
    private val options: NumberFormatOptions = NumberFormatOptions()
) : IntlFormatter<Double> {

    override fun format(value: Double): String {
        return when (options.style) {
            NumberFormatStyle.Decimal -> formatDecimal(value)
            NumberFormatStyle.Percent -> formatPercent(value)
            NumberFormatStyle.Currency -> formatCurrency(value)
            NumberFormatStyle.Unit -> formatUnit(value)
            NumberFormatStyle.Compact -> formatCompact(value)
        }
    }

    private fun formatDecimal(value: Double): String {
        val fractionDigits = options.maximumFractionDigits ?: 2
        return formatWithDigits(value, fractionDigits)
    }

    private fun formatPercent(value: Double): String {
        val percent = value * 100
        val fractionDigits = options.maximumFractionDigits ?: 0
        return formatWithDigits(percent, fractionDigits) + "%"
    }

    private fun formatCurrency(value: Double): String {
        val currency = options.currency ?: "USD"
        val display = options.currencyDisplay
        val symbol = when (display) {
            CurrencyDisplayMode.Code -> currency
            CurrencyDisplayMode.Name -> currency
            else -> getCurrencySymbol(currency)
        }
        return symbol + formatWithDigits(value, 2)
    }

    private fun formatUnit(value: Double): String {
        val unit = options.unit ?: ""
        return formatWithDigits(value, 2) + " " + unit
    }

    private fun formatCompact(value: Double): String {
        return when {
            value >= 1_000_000_000 -> formatWithDigits(value / 1_000_000_000, 1) + "B"
            value >= 1_000_000 -> formatWithDigits(value / 1_000_000, 1) + "M"
            value >= 1_000 -> formatWithDigits(value / 1_000, 1) + "K"
            else -> value.toString()
        }
    }

    private fun formatWithDigits(value: Double, digits: Int): String {
        val factor = 10.0.pow(digits.toDouble())
        val scaled = kround(value * factor) / factor
        val str = scaled.toString()
        return if ("." in str) {
            val parts = str.split(".")
            val intPart = parts[0]
            val fracPart = parts[1].padEnd(digits, '0').take(digits)
            "$intPart.$fracPart"
        } else {
            if (digits > 0) "$str." + "0".repeat(digits) else str
        }
    }

    private fun getCurrencySymbol(code: String): String {
        return when (code) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "CNY" -> "¥"
            "RUB" -> "₽"
            "INR" -> "₹"
            else -> code
        }
    }
}

/**
 * Format dates - basic fallback implementation for LinuxX64.
 */
class LinuxX64DateTimeFormatter(
    private val locale: LanguageIdentifier,
    private val options: DateTimeFormatOptions = DateTimeFormatOptions()
) : IntlFormatter<Long> {

    override fun format(value: Long): String {
        val secs = value / 1000
        val days = secs / 86400
        val year = 1970 + (days / 365).toInt()
        val month = 1 + ((days % 365) / 30).toInt()
        val day = 1 + ((days % 365) % 30).toInt()
        return formatDate(year, month, day)
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        val y = year.toString().padStart(4, '0')
        val m = month.toString().padStart(2, '0')
        val d = day.toString().padStart(2, '0')
        return "$y-$m-$d"
    }
}

/**
 * Format lists - basic fallback implementation for LinuxX64.
 */
class LinuxX64ListFormatter(
    private val locale: LanguageIdentifier,
    private val options: ListFormatOptions = ListFormatOptions()
) : IntlFormatter<List<String>> {

    override fun format(value: List<String>): String {
        if (value.isEmpty()) return ""
        if (value.size == 1) return value[0]
        if (value.size == 2) {
            val conjunction = if (options.type == ListType.Disjunction) " or " else " and "
            return value.joinToString(conjunction)
        }

        val separator = ", "
        val finalSeparator = when (options.type) {
            ListType.Conjunction -> ", and "
            ListType.Disjunction -> ", or "
            ListType.Unit -> ", "
        }

        return value.dropLast(1).joinToString(separator) + finalSeparator + value.last()
    }
}

/**
 * Plural rules for LinuxX64.
 */
class LinuxX64PluralRules(private val locale: LanguageIdentifier) {

    fun pluralCategory(value: Double): String {
        val lang = locale.language
        return when (lang) {
            "en" -> if (value == 1.0) "one" else "other"
            "ar" -> arabicPlurals(value)
            "fr", "de", "es", "it", "pt" -> if (value == 1.0) "one" else "other"
            "ru", "pl", "uk", "cs", "sk" -> slavicPlurals(value)
            "ja", "ko", "zh", "th", "vi" -> "other"
            else -> if (value == 1.0) "one" else "other"
        }
    }

    private fun arabicPlurals(value: Double): String {
        val n = value.toLong()
        return when (n) {
            0L -> "zero"
            1L -> "one"
            2L -> "two"
            in 3L..10L -> "few"
            in 11L..99L -> "many"
            else -> "other"
        }
    }

    private fun slavicPlurals(value: Double): String {
        val n = value.toLong()
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1L && mod100 != 11L -> "one"
            mod10 in 2L..4L && (mod100 < 12L || mod100 > 14L) -> "few"
            mod10 == 0L || mod10 in 5L..9L || mod100 in 11L..14L -> "many"
            else -> "other"
        }
    }
}

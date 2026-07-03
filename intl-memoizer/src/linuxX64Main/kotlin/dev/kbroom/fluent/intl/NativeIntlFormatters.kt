package dev.kbroom.fluent.intl

import kotlin.math.floor

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
        // Basic formatting - for full Intl, would need ICU4X or platform bindings
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
        return "%.${fractionDigits}f".format(value)
    }
    
    private fun formatPercent(value: Double): String {
        val percent = value * 100
        val fractionDigits = options.maximumFractionDigits ?: 0
        return "%.${fractionDigits}f%%".format(percent)
    }
    
    private fun formatCurrency(value: Double): String {
        val currency = options.currency ?: "USD"
        val display = options.currencyDisplay
        val symbol = when (display) {
            CurrencyDisplayMode.Code -> currency
            CurrencyDisplayMode.Name -> currency  // Would need currency names
            else -> getCurrencySymbol(currency)
        }
        return "$symbol%.2f".format(value)
    }
    
    private fun formatUnit(value: Double): String {
        val unit = options.unit ?: ""
        return "%.2f %s".format(value, unit)
    }
    
    private fun formatCompact(value: Double): String {
        return when {
            value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000)
            value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
            value >= 1_000 -> "%.1fK".format(value / 1_000)
            else -> value.toString()
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
) : IntlFormatter<Long> {  // Epoch milliseconds
    
    // Simplified - for full Intl would need platform bindings
    override fun format(value: Long): String {
        // Basic ISO-like format as fallback
        val instant = java.time.Instant.ofEpochMilli(value)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        return zoned.toString()
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
            in 3..10 -> "few"
            in 11..99 -> "many"
            else -> "other"
        }
    }
    
    private fun slavicPlurals(value: Double): String {
        val n = value.toLong()
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "one"
            mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "few"
            mod10 == 0 || mod10 in 5..9 || mod100 in 11..14 -> "many"
            else -> "other"
        }
    }
}

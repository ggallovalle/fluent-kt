package dev.kbroom.fluent.intl

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.Locale
import java.text.NumberFormat as JNumberFormat

/**
 * JVM implementation of Intl formatters.
 * Uses java.text and java.time for locale-aware formatting.
 *
 * Format numbers using locale-aware formatting.
 */
class JvmNumberFormatter(private val locale: Locale, private val options: NumberFormatOptions = NumberFormatOptions()) :
    IntlFormatter<Double> {

    override fun format(value: Double): String {
        val jFormat = when (options.style) {
            NumberFormatStyle.Decimal -> JNumberFormat.getNumberInstance(locale)
            NumberFormatStyle.Percent -> JNumberFormat.getPercentInstance(locale)
            NumberFormatStyle.Currency -> JNumberFormat.getCurrencyInstance(locale)
            NumberFormatStyle.Unit -> JNumberFormat.getNumberInstance(locale)
            NumberFormatStyle.Compact -> JNumberFormat.getNumberInstance(locale)
        }

        // Apply options
        options.minimumIntegerDigits?.let { jFormat.minimumIntegerDigits = it }
        options.minimumFractionDigits?.let { jFormat.minimumFractionDigits = it }
        options.maximumFractionDigits?.let { jFormat.maximumFractionDigits = it }
        options.useGrouping?.let { jFormat.isGroupingUsed = it }
        // Handle compact notation
        if (options.style == NumberFormatStyle.Compact) {
            return formatCompact(value, locale)
        }

        return jFormat.format(value)
    }
    private fun formatCompact(value: Double, locale: Locale): String = when {
        value >= 1_000_000_000 -> String.format(locale, "%.1fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format(locale, "%.1fM", value / 1_000_000)
        value >= 1_000 -> String.format(locale, "%.1fK", value / 1_000)
        else -> value.toString()
    }
}

/**
 * Format dates and times using locale-aware formatting.
 */
class JvmDateTimeFormatter(
    private val locale: Locale,
    private val options: DateTimeFormatOptions = DateTimeFormatOptions(),
) : IntlFormatter<Temporal> {

    private val formatter: DateTimeFormatter by lazy {
        buildFormatter()
    }

    private fun buildFormatter(): DateTimeFormatter {
        if (options.dateStyle != null || options.timeStyle != null) {
            return localizedFormatter(options)
        }
        return DateTimeFormatter.ofPattern(buildPattern(options)).withLocale(locale)
    }

    private fun localizedFormatter(options: DateTimeFormatOptions): DateTimeFormatter {
        val dateStyle = options.dateStyle?.toFormatStyle() ?: FormatStyle.MEDIUM
        val timeStyle = options.timeStyle?.toFormatStyle() ?: FormatStyle.MEDIUM
        val formatter = when {
            options.dateStyle != null && options.timeStyle != null ->
                DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle)

            options.dateStyle != null -> DateTimeFormatter.ofLocalizedDate(dateStyle)

            options.timeStyle != null -> DateTimeFormatter.ofLocalizedTime(timeStyle)

            else -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
        }
        return formatter.withLocale(locale)
    }

    private fun buildPattern(options: DateTimeFormatOptions): String = buildString {
        options.weekday?.let { append("EEEE") }
        options.era?.let { append(", ") }
        options.year?.let { append(if (it == TextStyle.Long) "yyyy" else "yy") }
        options.month?.let { append(monthPattern(it)) }
        options.day?.let { append(if (it == TextStyle.Long) "dd" else "d") }
        if (options.hour != null || options.minute != null || options.second != null) {
            appendTimeOfDay(this, options)
        }
        options.timeZoneName?.let {
            append(" ")
            append(timeZonePattern(it))
        }
    }

    private fun monthPattern(style: TextStyle): String = when (style) {
        TextStyle.Long -> "MMMM"
        TextStyle.Short -> "MMM"
        else -> "M"
    }

    private fun timeZonePattern(style: TextStyle): String = when (style) {
        TextStyle.Long -> "zzzz"
        TextStyle.Short -> "z"
        else -> "Z"
    }

    private fun appendTimeOfDay(sb: StringBuilder, options: DateTimeFormatOptions) {
        sb.append(" ")
        options.hour?.let { sb.append(if (options.hour12 == true) "h" else "H") }
        sb.append(":")
        options.minute?.let { sb.append("mm") }
        if (options.second != null) {
            sb.append(":")
            sb.append("ss")
        }
        options.hour12?.let { if (it) sb.append(" a") }
    }

    override fun format(value: Temporal): String = formatter.format(value)

    private fun DateTimeStyle.toFormatStyle(): FormatStyle = when (this) {
        DateTimeStyle.Full -> FormatStyle.FULL
        DateTimeStyle.Long -> FormatStyle.LONG
        DateTimeStyle.Medium -> FormatStyle.MEDIUM
        DateTimeStyle.Short -> FormatStyle.SHORT
    }
}

/**
 * Format lists with locale-aware conjunction/disjunction.
 */
@Suppress("UnusedPrivateProperty")
class JvmListFormatter(private val locale: Locale, private val options: ListFormatOptions = ListFormatOptions()) :
    IntlFormatter<List<String>> {

    override fun format(value: List<String>): String {
        if (value.isEmpty()) return ""
        if (value.size == 1) return value[0]
        if (value.size == 2) return value.joinToString(separatorForTwo())

        val last = value.last()
        val others = value.dropLast(1)
        return others.joinToString(LIST_SEPARATOR) + finalSeparator() + last
    }

    private fun separatorForTwo(): String = if (options.type == ListType.Disjunction) " or " else " and "

    private fun finalSeparator(): String = when (options.type) {
        ListType.Conjunction -> when (options.style) {
            ListStyle.Long -> ", and "
            else -> ", "
        }

        ListType.Disjunction -> when (options.style) {
            ListStyle.Long -> ", or "
            else -> ", "
        }

        ListType.Unit -> ", "
    }

    companion object {
        const val LIST_SEPARATOR = ", "
    }
}

/**
 * Get plural category for a number in a given locale.
 * Uses Java's PluralRules under the hood.
 */
class JvmPluralRules(private val locale: Locale) {
    // Simple plural category implementation
    // For full CLDR support, would need to embed plural rules data

    fun pluralCategory(value: Double): String {
        val lang = locale.language
        val formatted = when {
            value == value.toLong().toDouble() && value >= 0 -> value.toLong()
            else -> value
        }

        return when (lang) {
            "en" -> englishPlurals(formatted)
            "ar" -> arabicPlurals(formatted)
            "fr" -> frenchPlurals(formatted)
            "de" -> germanPlurals(formatted)
            "ru" -> russianPlurals(formatted)
            "pl" -> polishPlurals(formatted)
            "ja", "ko", "zh" -> "other"
            else -> englishPlurals(formatted)
        }
    }

    private fun englishPlurals(value: Number): String = when (value) {
        1.0 -> "one"
        else -> "other"
    }

    private fun arabicPlurals(value: Number): String {
        val n = value.toDouble()
        return when {
            n == 0.0 -> "zero"
            n == 1.0 -> "one"
            n == 2.0 -> "two"
            n in 3.0..10.0 -> "few"
            n in 11.0..99.0 -> "many"
            else -> "other"
        }
    }

    private fun frenchPlurals(value: Number): String = when (value) {
        0.0, 1.0 -> "one"
        else -> "other"
    }

    private fun germanPlurals(value: Number): String = when (value) {
        1.0 -> "one"
        else -> "other"
    }

    private fun russianPlurals(value: Number): String {
        val n = value.toLong()
        val mod10 = n % 10
        val mod100 = n % 100
        val mod10Int = mod10.toInt()
        val mod100Int = mod100.toInt()
        return when {
            mod10Int == 1 && mod100Int != 11 -> "one"
            mod10Int in 2..4 && (mod100Int < 12 || mod100Int > 14) -> "few"
            mod10Int == 0 || mod10Int in 5..9 || mod100Int in 11..14 -> "many"
            else -> "other"
        }
    }

    private fun polishPlurals(value: Number): String {
        val n = value.toLong()
        val mod10 = n % 10
        val mod100 = n % 100
        val mod10Int = mod10.toInt()
        val mod100Int = mod100.toInt()
        return when {
            n == 1L -> "one"
            mod10Int in 2..4 && (mod100Int < 12 || mod100Int > 14) -> "few"
            (n != 0L && mod10Int == 0) || mod10Int in 5..9 || mod100Int in 11..14 -> "many"
            else -> "other"
        }
    }
}

/**
 * Convert LanguageIdentifier to Java Locale.
 */
fun LanguageIdentifier.toJvmLocale(): Locale {
    val tag = toTag()
    return Locale.forLanguageTag(tag)
}

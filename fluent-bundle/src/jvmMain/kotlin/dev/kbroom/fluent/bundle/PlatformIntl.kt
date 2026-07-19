package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.intl.toJvmLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat as JNumberFormat

/**
 * JVM implementation of PlatformIntl.
 */
@Suppress("LongParameterList")
actual object PlatformIntl {

    actual fun formatNumber(
        value: Double,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String?,
        currency: String?,
        currencyDisplay: String?,
        minimumFractionDigits: Int?,
        maximumFractionDigits: Int?,
        useGrouping: Boolean?,
    ): String? {
        val jLocale = locale.toJvmLocale()

        val jFormat = when (style) {
            "percent" -> JNumberFormat.getPercentInstance(jLocale)
            "currency" -> JNumberFormat.getCurrencyInstance(jLocale)
            else -> JNumberFormat.getNumberInstance(jLocale)
        }

        minimumFractionDigits?.let { jFormat.minimumFractionDigits = it }
        maximumFractionDigits?.let { jFormat.maximumFractionDigits = it }
        useGrouping?.let { jFormat.isGroupingUsed = it }

        return jFormat.format(value)
    }

    actual fun formatDateTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        dateStyle: String?,
        timeStyle: String?,
        hour12: Boolean?,
        timeZone: String?,
    ): String? {
        val jLocale = locale.toJvmLocale()
        val instant = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())

        val dStyle = dateStyle?.let { parseFormatStyle(it) }
        val tStyle = timeStyle?.let { parseFormatStyle(it) }

        val formatter = when {
            dStyle != null && tStyle != null ->
                DateTimeFormatter.ofLocalizedDateTime(dStyle, tStyle).withLocale(jLocale)

            dStyle != null -> DateTimeFormatter.ofLocalizedDate(dStyle).withLocale(jLocale)

            tStyle != null -> DateTimeFormatter.ofLocalizedTime(tStyle).withLocale(jLocale)

            else -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM).withLocale(jLocale)
        }

        return instant.format(formatter)
    }

    private fun parseFormatStyle(style: String): FormatStyle = when (style.lowercase()) {
        "full" -> FormatStyle.FULL
        "long" -> FormatStyle.LONG
        "medium" -> FormatStyle.MEDIUM
        "short" -> FormatStyle.SHORT
        else -> FormatStyle.MEDIUM
    }

    actual fun formatDate(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String,
        timeZone: String?,
    ): String? = formatDateTime(
        value,
        locale,
        memoizer,
        dateStyle = style,
        timeStyle = null,
        hour12 = null,
        timeZone = timeZone,
    )

    actual fun formatTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String,
        hour12: Boolean?,
        timeZone: String?,
    ): String? = formatDateTime(
        value,
        locale,
        memoizer,
        dateStyle = null,
        timeStyle = style,
        hour12 = hour12,
        timeZone = timeZone,
    )

    actual fun formatList(
        values: List<String>,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        type: String,
        style: String,
    ): String? {
        if (values.isEmpty()) return ""
        if (values.size == 1) return values[0]
        if (values.size == 2) {
            val conjunction = if (type == "disjunction") " or " else " and "
            return values.joinToString(conjunction)
        }

        val separator = ", "
        val finalSeparator = when (type) {
            "disjunction" -> ", or "
            else -> ", and "
        }

        return values.dropLast(1).joinToString(separator) + finalSeparator + values.last()
    }

    actual fun getPluralCategory(value: Double, locale: LanguageIdentifier, memoizer: IntlLangMemoizer): String {
        val lang = locale.language
        return when (lang) {
            "en" -> pluralCategoryOneOther(value)
            "fr", "de", "es", "it", "pt" -> pluralCategoryOneOther(value)
            "ru", "pl", "uk" -> pluralCategorySlavic(value)
            "ar" -> pluralCategoryArabic(value)
            else -> pluralCategoryOneOther(value)
        }
    }

    private fun pluralCategoryOneOther(value: Double): String = if (value == 1.0) "one" else "other"

    private fun pluralCategorySlavic(value: Double): String {
        val n = value.toLong()
        val mod10 = (n % 10).toInt()
        val mod100 = (n % 100).toInt()
        return when {
            mod10 == 1 && mod100 != 11 -> "one"
            mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "few"
            mod10 == 0 || mod10 in 5..9 || mod100 in 11..14 -> "many"
            else -> "other"
        }
    }

    private fun pluralCategoryArabic(value: Double): String {
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
}

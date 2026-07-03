package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.intl.IntlLangMemoizer
import kotlin.math.floor

/**
 * LinuxX64 implementation of PlatformIntl.
 * Uses fallback basic formatting since full Intl isn't available on native.
 */
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
        useGrouping: Boolean?
    ): String? {
        val fractionDigits = maximumFractionDigits ?: 2
        return when (style) {
            "percent" -> "%.${fractionDigits}f%%".format(value * 100)
            "currency" -> {
                val sym = when (currency) {
                    "EUR" -> "€"
                    "GBP" -> "£"
                    "JPY" -> "¥"
                    else -> currency ?: "$"
                }
                "$sym%.${fractionDigits}f".format(value)
            }
            else -> "%.${fractionDigits}f".format(value)
        }
    }
    
    actual fun formatDateTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        dateStyle: String?,
        timeStyle: String?,
        hour12: Boolean?,
        timeZone: String?
    ): String? {
        val instant = java.time.Instant.ofEpochMilli(value)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        return zoned.toString()
    }
    
    actual fun formatDate(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String,
        timeZone: String?
    ): String? {
        return formatDateTime(value, locale, memoizer, dateStyle = style, timeStyle = null, hour12 = null, timeZone = timeZone)
    }
    
    actual fun formatTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String,
        hour12: Boolean?,
        timeZone: String?
    ): String? {
        return formatDateTime(value, locale, memoizer, dateStyle = null, timeStyle = style, hour12 = hour12, timeZone = timeZone)
    }
    
    actual fun formatList(
        values: List<String>,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        type: String,
        style: String
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
    
    actual fun getPluralCategory(
        value: Double,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer
    ): String {
        val lang = locale.language
        return when (lang) {
            "en" -> if (value == 1.0) "one" else "other"
            "fr", "de", "es", "it", "pt" -> if (value == 1.0) "one" else "other"
            "ru", "pl", "uk" -> {
                val n = value.toLong()
                val mod10 = n % 10
                val mod100 = n % 100
                when {
                    mod10 == 1L && mod100 != 11L -> "one"
                    mod10 in 2L..4L && (mod100 < 12L || mod100 > 14L) -> "few"
                    mod10 == 0L || mod10 in 5L..9L || mod100 in 11L..14L -> "many"
                    else -> "other"
                }
            }
            "ar" -> {
                val n = value.toLong()
                when (n) {
                    0L -> "zero"
                    1L -> "one"
                    2L -> "two"
                    in 3L..10L -> "few"
                    in 11L..99L -> "many"
                    else -> "other"
                }
            }
            else -> if (value == 1.0) "one" else "other"
        }
    }
}

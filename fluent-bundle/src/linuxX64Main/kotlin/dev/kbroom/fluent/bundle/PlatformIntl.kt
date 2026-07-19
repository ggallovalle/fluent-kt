package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.math.pow

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
        useGrouping: Boolean?,
    ): String? {
        val fractionDigits = maximumFractionDigits ?: 2
        return when (style) {
            "percent" -> formatWithDigits(value * 100, fractionDigits) + "%"

            "currency" -> {
                val sym = when (currency) {
                    "EUR" -> "€"
                    "GBP" -> "£"
                    "JPY" -> "¥"
                    else -> currency ?: "$"
                }
                sym + formatWithDigits(value, 2)
            }

            else -> formatWithDigits(value, fractionDigits)
        }
    }

    private fun formatWithDigits(value: Double, digits: Int): String {
        val factor = 10.0.pow(digits.toDouble())
        val scaled = (value * factor).toLong().toDouble() / factor
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

    actual fun formatDateTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        dateStyle: String?,
        timeStyle: String?,
        hour12: Boolean?,
        timeZone: String?,
    ): String? {
        // Basic epoch millis to date string as fallback
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
    ): String? {
        val (hh, minute, second) = hmsParts(value)
        val hhStr = pad2(to12Hour(hh, hour12))
        val mmStr = pad2(minute)
        val ssStr = pad2(second)
        val ampm = ampmSuffix(hh, hour12)
        return "$hhStr:$mmStr:$ssStr$ampm"
    }

    private fun hmsParts(value: Long): Triple<Int, Int, Int> {
        val totalSeconds = value / 1000
        val secondsOfDay = ((totalSeconds % 86400) + 86400) % 86400
        val hour = (secondsOfDay / 3600).toInt()
        val minute = ((secondsOfDay % 3600) / 60).toInt()
        val second = (secondsOfDay % 60).toInt()
        return Triple(hour, minute, second)
    }

    private fun to12Hour(hour: Int, hour12: Boolean?): Int {
        if (hour12 != true) return hour
        return when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }
    }

    private fun ampmSuffix(hour: Int, hour12: Boolean?): String {
        if (hour12 != true) return ""
        return if (hour < 12) " AM" else " PM"
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

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

    actual fun getOrdinalPluralCategory(value: Double, locale: LanguageIdentifier, memoizer: IntlLangMemoizer): String {
        // English ordinals: 1st -> "one", 2nd -> "two", 3rd -> "few", everything else -> "other".
        // Other languages: fall back to "other" until we wire CLDR per-locale rules.
        return when (locale.language) {
            "en" -> when (value.toLong()) {
                1L -> "one"
                2L -> "two"
                3L -> "few"
                else -> "other"
            }
            else -> "other"
        }
    }

    private fun pluralCategoryOneOther(value: Double): String = if (value == 1.0) "one" else "other"

    private fun pluralCategorySlavic(value: Double): String {
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

    private fun pluralCategoryArabic(value: Double): String {
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
}

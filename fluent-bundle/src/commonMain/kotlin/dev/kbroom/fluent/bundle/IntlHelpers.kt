package dev.kbroom.fluent.bundle

import dev.kbroom.fluent.intl.IntlLangMemoizer
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * Platform-specific Intl helper functions.
 * These are implemented via expect/actual in platform-specific sources.
 */
object IntlHelpers {

    @Suppress("LongParameterList")
    fun formatNumber(
        value: Double,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String? = null,
        currency: String? = null,
        currencyDisplay: String? = null,
        minimumFractionDigits: Int? = null,
        maximumFractionDigits: Int? = null,
        useGrouping: Boolean? = null,
    ): String? = PlatformIntl.formatNumber(
        value, locale, memoizer, style, currency, currencyDisplay,
        minimumFractionDigits, maximumFractionDigits, useGrouping,
    )

    @Suppress("LongParameterList")
    fun formatDateTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        dateStyle: String? = null,
        timeStyle: String? = null,
        hour12: Boolean? = null,
        timeZone: String? = null,
    ): String? = PlatformIntl.formatDateTime(
        value,
        locale,
        memoizer,
        dateStyle,
        timeStyle,
        hour12,
        timeZone,
    )

    /**
     * Format a date only.
     */
    fun formatDate(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String = "medium",
        timeZone: String? = null,
    ): String? = PlatformIntl.formatDate(value, locale, memoizer, style, timeZone)

    /**
     * Format a time only.
     */
    fun formatTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String = "medium",
        hour12: Boolean? = null,
        timeZone: String? = null,
    ): String? = PlatformIntl.formatTime(value, locale, memoizer, style, hour12, timeZone)

    /**
     * Format a list with locale-aware conjunction/disjunction.
     */
    fun formatList(
        values: List<String>,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        type: String = "conjunction",
        style: String = "long",
    ): String? = PlatformIntl.formatList(values, locale, memoizer, type, style)

    /**
     * Get plural category for a number in a given locale.
     */
    fun getPluralCategory(value: Double, locale: LanguageIdentifier, memoizer: IntlLangMemoizer): String =
        PlatformIntl.getPluralCategory(value, locale, memoizer)

    /**
     * Get ordinal plural category (e.g. "one" for 1st, "two" for 2nd,
     * "few" for 3rd, "other" otherwise) for a number in a given locale.
     */
    fun getOrdinalPluralCategory(value: Double, locale: LanguageIdentifier, memoizer: IntlLangMemoizer): String =
        PlatformIntl.getOrdinalPluralCategory(value, locale, memoizer)
}

/**
 * Platform-specific Intl implementations.
 */
@Suppress("LongParameterList")
expect object PlatformIntl {
    fun formatNumber(
        value: Double,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String?,
        currency: String?,
        currencyDisplay: String?,
        minimumFractionDigits: Int?,
        maximumFractionDigits: Int?,
        useGrouping: Boolean?,
    ): String?

    fun formatDateTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        dateStyle: String?,
        timeStyle: String?,
        hour12: Boolean?,
        timeZone: String?,
    ): String?

    fun formatDate(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String,
        timeZone: String?,
    ): String?

    fun formatTime(
        value: Long,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        style: String,
        hour12: Boolean?,
        timeZone: String?,
    ): String?

    fun formatList(
        values: List<String>,
        locale: LanguageIdentifier,
        memoizer: IntlLangMemoizer,
        type: String,
        style: String,
    ): String?

    fun getPluralCategory(value: Double, locale: LanguageIdentifier, memoizer: IntlLangMemoizer): String

    fun getOrdinalPluralCategory(value: Double, locale: LanguageIdentifier, memoizer: IntlLangMemoizer): String
}

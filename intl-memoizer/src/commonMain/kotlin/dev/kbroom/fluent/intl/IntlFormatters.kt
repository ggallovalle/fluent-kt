package dev.kbroom.fluent.intl

/**
 * Common interface for Intl formatters that can be memoized.
 * Each formatter is locale-specific.
 */
interface IntlFormatter<T> {
    /**
     * Format a value using this formatter.
     */
    fun format(value: T): String
}

/**
 * Number formatting options.
 */
data class NumberFormatOptions(
    val style: NumberFormatStyle = NumberFormatStyle.Decimal,
    val currency: String? = null,
    val currencyDisplay: CurrencyDisplayMode = CurrencyDisplayMode.Symbol,
    val currencySign: CurrencySign = CurrencySign.Standard,
    val minimumIntegerDigits: Int? = null,
    val minimumFractionDigits: Int? = null,
    val maximumFractionDigits: Int? = null,
    val minimumSignificantDigits: Int? = null,
    val maximumSignificantDigits: Int? = null,
    val useGrouping: Boolean? = null,
    val signDisplay: SignDisplay = SignDisplay.Auto,
    val compactDisplay: CompactDisplay = CompactDisplay.Short,
    val notation: Notation = Notation.Standard,
    val unit: String? = null,
    val unitDisplay: UnitDisplay = UnitDisplay.Short
)

enum class NumberFormatStyle {
    Decimal,
    Percent,
    Currency,
    Unit,
    Compact
}

enum class CurrencyDisplayMode {
    Symbol,
    Code,
    Name
}

enum class CurrencySign {
    Standard,
    Accounting
}

enum class SignDisplay {
    Auto,
    Always,
    Never,
    ExceptZero
}

enum class CompactDisplay {
    Short,
    Long
}

enum class Notation {
    Standard,
    Scientific,
    Engineering,
    Compact
}

enum class UnitDisplay {
    Long,
    Short,
    Narrow
}

/**
 * DateTime formatting options.
 */
data class DateTimeFormatOptions(
    val dateStyle: DateTimeStyle? = null,
    val timeStyle: DateTimeStyle? = null,
    val weekday: TextStyle? = null,
    val era: TextStyle? = null,
    val year: TextStyle? = null,
    val month: TextStyle? = null,
    val day: TextStyle? = null,
    val hour: TextStyle? = null,
    val minute: TextStyle? = null,
    val second: TextStyle? = null,
    val timeZoneName: TextStyle? = null,
    val hour12: Boolean? = null,
    val timeZone: String? = null,
    val calendar: String? = null,
    val numberingSystem: String? = null
)

enum class DateTimeStyle {
    Full,
    Long,
    Medium,
    Short
}

enum class TextStyle {
    Long,
    Short,
    Narrow
}

/**
 * List formatting options.
 */
data class ListFormatOptions(
    val type: ListType = ListType.Conjunction,
    val style: ListStyle = ListStyle.Long
)

enum class ListType {
    Conjunction,  // "a, b, and c"
    Disjunction,  // "a, b, or c"
    Unit          // "a, b, c"
}

enum class ListStyle {
    Long,
    Short,
    Narrow
}

/**
 * Options for plural rule selection.
 */
data class PluralRulesOptions(
    val type: PluralRuleType = PluralRuleType.Cardinal,
    val minimumFractionDigits: Int? = null,
    val maximumFractionDigits: Int? = null
)

enum class PluralRuleType {
    Cardinal,
    Ordinal
}

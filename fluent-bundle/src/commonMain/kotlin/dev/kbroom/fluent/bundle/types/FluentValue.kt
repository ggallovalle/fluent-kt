package dev.kbroom.fluent.bundle.types

import kotlinx.serialization.Serializable
import kotlin.math.floor

/**
 * FluentValue represents any value that can be used in Fluent patterns.
 * Corresponds to Rust's FluentValue enum.
 */
@Serializable
sealed class FluentValue {
    /**
     * A string value.
     */
    @Serializable
    data class Str(val value: String) : FluentValue()
    
    /**
     * A number value with optional formatting.
     */
    @Serializable
    data class Number(val value: FluentNumber) : FluentValue()
    
    /**
     * A custom type value.
     */
    @Serializable
    data class Custom(val value: FluentType) : FluentValue()
    
    /**
     * A pattern value (returned for attribute references to allow proper select handling).
     */
    @Serializable
    data class Pattern(val pattern: dev.kbroom.fluent.syntax.Pattern) : FluentValue()
    
    /**
     * Represents no value (null equivalent).
     */
    @Serializable
    data object None : FluentValue()
    
    /**
     * An error value.
     */
    @Serializable
    data class Error(val message: String) : FluentValue()
    
    /**
     * Convert FluentValue to display string.
     */
    fun asString(): String = when (this) {
        is Str -> value
        is Number -> {
            val v = value.value
            // Format integer values without decimal point if whole number
            val intValue = v.toLong()
            if (v == intValue.toDouble() && intValue.toDouble() == v) {
                intValue.toString()
            } else {
                v.toString()
            }
        }
        is Custom -> value.asString()
        is Pattern -> throw IllegalStateException("Pattern should be resolved before asString()")
        is None -> ""
        is Error -> "{$message}"
    }
    
    /**
     * Get the underlying value as Any.
     */
    fun asAny(): Any? = when (this) {
        is Str -> value
        is Number -> value.value
        is Custom -> value
        is Pattern -> pattern
        is None -> null
        is Error -> message
    }
}

/**
 * Represents a number with formatting options.
 */
@Serializable
data class FluentNumber(
    val value: Double,
    val options: FluentNumberOptions = FluentNumberOptions()
)

/**
 * Options for number formatting.
 */
@Serializable
data class FluentNumberOptions(
    val style: NumberStyle? = null,
    val currency: String? = null,
    val currencyDisplay: CurrencyDisplay? = null,
    val minimumFractionDigits: Int? = null,
    val maximumFractionDigits: Int? = null
)

/**
 * Number style for formatting.
 */
@Serializable
enum class NumberStyle {
    DECIMAL,
    CURRENCY,
    PERCENT,
    UNIT
}

/**
 * Currency display format.
 */
@Serializable
enum class CurrencyDisplay {
    SYMBOL,
    CODE,
    NAME
}

/**
 * Base interface for custom Fluent types.
 */
interface FluentType {
    fun duplicate(): FluentType
    fun asString(): String
}

/**
 * Create a FluentValue from a Kotlin value.
 */
fun fluentValueOf(value: Any?): FluentValue = when (value) {
    null -> FluentValue.None
    is String -> FluentValue.Str(value)
    is Int -> FluentValue.Number(FluentNumber(value.toDouble()))
    is Long -> FluentValue.Number(FluentNumber(value.toDouble()))
    is Float -> FluentValue.Number(FluentNumber(value.toDouble()))
    is Double -> FluentValue.Number(FluentNumber(value))
    is FluentValue -> value
    is FluentType -> FluentValue.Custom(value)
    else -> FluentValue.Str(value.toString())
}

/**
 * Get plural category for a number in a given locale.
 */
fun getPluralCategory(value: Double, locale: String): PluralCategory {
    val intValue = floor(value).toInt()
    
    // CLDR plural rules for common locales
    return when {
        locale.startsWith("en") -> when (intValue) {
            1 -> PluralCategory.ONE
            else -> PluralCategory.OTHER
        }
        locale.startsWith("ru") || locale.startsWith("uk") -> when {
            intValue % 10 == 1 && intValue % 100 != 11 -> PluralCategory.ONE
            intValue % 10 in 2..4 && intValue % 100 !in 12..14 -> PluralCategory.FEW
            intValue % 10 == 0 || intValue % 10 in 5..9 || intValue % 100 in 11..14 -> PluralCategory.MANY
            else -> PluralCategory.OTHER
        }
        locale.startsWith("ar") -> when (intValue) {
            0 -> PluralCategory.ZERO
            1 -> PluralCategory.ONE
            2 -> PluralCategory.TWO
            in 3..10 -> PluralCategory.FEW
            in 11..99 -> PluralCategory.MANY
            else -> PluralCategory.OTHER
        }
        else -> PluralCategory.OTHER
    }
}

/**
 * Plural categories per CLDR.
 */
enum class PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER
}

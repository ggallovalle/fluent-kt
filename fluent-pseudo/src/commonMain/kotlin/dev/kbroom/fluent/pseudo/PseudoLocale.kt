package dev.kbroom.fluent.pseudo

/**
 * Pseudolocalization modes.
 */
enum class PseudoMode {
    /**
     * Accented: replaces ASCII characters with accented equivalents.
     * Useful for finding i18n issues.
     */
    Accented,

    /**
     * Bidi: emulates right-to-left text direction.
     */
    Bidi,

    /**
     * Widened: expands text to test UI layout.
     */
    Widened,

    /**
     * Hidden: replaces characters with [x] to find hardcoded strings.
     */
    Hidden,
}

/**
 * Options for pseudolocalization.
 */
data class PseudoOptions(
    val mode: PseudoMode = PseudoMode.Accented,
    val skipHtmlEntities: Boolean = true,
    val skipPlaceables: Boolean = true,
)

/**
 * Pseudolocalizer transforms text for testing i18n.
 */
class PseudoLocale(private val options: PseudoOptions = PseudoOptions()) {

    private val accentMap = mapOf(
        'a' to 'á', 'b' to 'ƀ', 'c' to 'ç', 'd' to 'đ', 'e' to 'é',
        'f' to 'ƒ', 'g' to 'ǵ', 'h' to 'ĥ', 'i' to 'í', 'j' to 'ĵ',
        'k' to 'ķ', 'l' to 'ĺ', 'm' to 'ɱ', 'n' to 'ñ', 'o' to 'ó',
        'p' to 'þ', 'q' to 'ǫ', 'r' to 'ŕ', 's' to 'š', 't' to 'ţ',
        'u' to 'ú', 'v' to 'ṽ', 'w' to 'ŵ', 'x' to 'χ', 'y' to 'ý',
        'z' to 'ž',
        'A' to 'Á', 'B' to 'Ɓ', 'C' to 'Ç', 'D' to 'Đ', 'E' to 'É',
        'F' to 'Ƒ', 'G' to 'Ǵ', 'H' to 'Ĥ', 'I' to 'Í', 'J' to 'Ĵ',
        'K' to 'Ķ', 'L' to 'Ĺ', 'M' to 'Ṁ', 'N' to 'Ñ', 'O' to 'Ó',
        'P' to 'Þ', 'Q' to 'Ǫ', 'R' to 'Ŕ', 'S' to 'Š', 'T' to 'Ţ',
        'U' to 'Ú', 'V' to 'Ṽ', 'W' to 'Ŵ', 'X' to 'Χ', 'Y' to 'Ý',
        'Z' to 'Ž',
    )

    private val widenedMap = mapOf(
        'a' to 'ā', 'b' to 'ƀ', 'c' to 'ċ', 'd' to 'đ', 'e' to 'ē',
        'f' to 'ƒ', 'g' to 'ġ', 'h' to 'ħ', 'i' to 'ī', 'j' to 'ĵ',
        'k' to 'ķ', 'l' to 'ĺ', 'm' to 'ɱ', 'n' to 'ñ', 'o' to 'ō',
        'p' to 'þ', 'q' to 'ǫ', 'r' to 'ŕ', 's' to 'š', 't' to 'ţ',
        'u' to 'ū', 'v' to 'ṽ', 'w' to 'ŵ', 'x' to 'χ', 'y' to 'ý',
        'z' to 'ž',
    )

    /**
     * Transform a string according to the configured mode.
     */
    fun transform(input: String): String = when (options.mode) {
        PseudoMode.Accented -> transformAccented(input)
        PseudoMode.Bidi -> transformBidi(input)
        PseudoMode.Widened -> transformWidened(input)
        PseudoMode.Hidden -> transformHidden(input)
    }

    /**
     * Apply [transformChar] to every text character in [input], preserving
     * placeables ({...}) and HTML entities (&...) unchanged based on
     * [options]. Used by [transformAccented], [transformWidened], and
     * [transformHidden] to share the skip-placeables/skip-entities logic.
     */
    private fun transformChars(input: String, transformChar: (Char) -> CharSequence): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                options.skipPlaceables && ch == '{' -> {
                    val placeholderEnd = input.indexOf('}', i)
                    val end = if (placeholderEnd < 0) input.length else placeholderEnd + 1
                    sb.append(input, i, end)
                    i = end
                }

                options.skipHtmlEntities && ch == '&' -> {
                    sb.append(ch)
                    i++
                }

                else -> {
                    sb.append(transformChar(ch))
                    i++
                }
            }
        }
        return sb.toString()
    }

    /**
     * Transform with accented characters.
     */
    private fun transformAccented(input: String): String =
        transformChars(input) { ch -> accentMap[ch]?.toString() ?: ch.toString() }

    /**
     * Transform with bidi marks for RTL testing.
     */
    private fun transformBidi(input: String): String = "\u202B$input\u202C"

    /**
     * Transform with widened characters (adds diacritics that widen text).
     */
    private fun transformWidened(input: String): String =
        transformChars(input) { ch -> widenedMap[ch]?.toString() ?: ch.toString() }

    /**
     * Transform with hidden characters (for finding hardcoded strings).
     */
    private fun transformHidden(input: String): String =
        transformChars(input) { ch -> if (ch.isLetter()) "[$ch]" else ch.toString() }

    companion object {
        /**
         * Create a default accented pseudolocalizer.
         */
        fun accented(): PseudoLocale = PseudoLocale(PseudoOptions(mode = PseudoMode.Accented))

        /**
         * Create a bidi pseudolocalizer.
         */
        fun bidi(): PseudoLocale = PseudoLocale(PseudoOptions(mode = PseudoMode.Bidi))

        /**
         * Create a widened pseudolocalizer.
         */
        fun widened(): PseudoLocale = PseudoLocale(PseudoOptions(mode = PseudoMode.Widened))

        /**
         * Create a hidden pseudolocalizer.
         */
        fun hidden(): PseudoLocale = PseudoLocale(PseudoOptions(mode = PseudoMode.Hidden))
    }
}

/**
 * Transform function for FluentBundle integration.
 */
fun createPseudoTransform(mode: PseudoMode): (String) -> String = PseudoLocale(PseudoOptions(mode = mode))::transform

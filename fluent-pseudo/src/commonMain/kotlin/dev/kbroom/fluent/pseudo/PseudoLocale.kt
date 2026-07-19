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
     * Transform with accented characters.
     */
    private fun transformAccented(input: String): String {
        val sb = StringBuilder()
        var inPlaceholder = false

        for (char in input) {
            // Handle placeables
            if (options.skipPlaceables) {
                if (char == '{') {
                    inPlaceholder = true
                    sb.append(char)
                    continue
                }
                if (char == '}') {
                    inPlaceholder = false
                    sb.append(char)
                    continue
                }
                if (inPlaceholder) {
                    sb.append(char)
                    continue
                }
            }

            // Skip HTML entities
            if (options.skipHtmlEntities && char == '&') {
                sb.append(char)
                continue
            }

            // Transform letters
            val transformed = accentMap[char]
            if (transformed != null) {
                sb.append(transformed)
            } else {
                sb.append(char)
            }
        }

        return sb.toString()
    }

    /**
     * Transform with bidi marks for RTL testing.
     */
    private fun transformBidi(input: String): String {
        // Add Unicode RTL marks
        return "\u202B$input\u202C"
    }

    /**
     * Transform with widened characters (adds diacritics that widen text).
     */
    private fun transformWidened(input: String): String {
        val sb = StringBuilder()
        var inPlaceholder = false

        for (char in input) {
            // Handle placeables
            if (options.skipPlaceables) {
                if (char == '{') {
                    inPlaceholder = true
                    sb.append(char)
                    continue
                }
                if (char == '}') {
                    inPlaceholder = false
                    sb.append(char)
                    continue
                }
                if (inPlaceholder) {
                    sb.append(char)
                    continue
                }
            }

            // Transform letters
            val transformed = widenedMap[char]
            if (transformed != null) {
                sb.append(transformed)
            } else {
                sb.append(char)
            }
        }

        return sb.toString()
    }

    /**
     * Transform with hidden characters (for finding hardcoded strings).
     */
    private fun transformHidden(input: String): String {
        val sb = StringBuilder()
        var inPlaceholder = false

        for (char in input) {
            // Handle placeables
            if (options.skipPlaceables) {
                if (char == '{') {
                    inPlaceholder = true
                    sb.append(char)
                    continue
                }
                if (char == '}') {
                    inPlaceholder = false
                    sb.append(char)
                    continue
                }
                if (inPlaceholder) {
                    sb.append(char)
                    continue
                }
            }

            // Transform letters to [x] form
            if (char.isLetter()) {
                sb.append('[').append(char).append(']')
            } else {
                sb.append(char)
            }
        }

        return sb.toString()
    }

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

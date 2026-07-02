package dev.kbroom.fluent.intl

/**
 * Represents a BCP 47 language identifier.
 * 
 * Parses language tags like "en-US", "zh-Hans-CN", "sr-Cyrl-RS".
 */
@kotlinx.serialization.Serializable
data class LanguageIdentifier(
    val language: String,
    val script: String? = null,
    val region: String? = null,
    val variants: List<String> = emptyList(),
    val extensions: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Parse a BCP 47 language tag into a LanguageIdentifier.
         */
        fun parse(tag: String): LanguageIdentifier {
            val parts = tag.split("-")
            val language = parts.getOrNull(0)?.lowercase() ?: ""
            
            var script: String? = null
            var region: String? = null
            val variants = mutableListOf<String>()
            val extensions = mutableMapOf<String, String>()
            
            var i = 1
            while (i < parts.size) {
                val part = parts[i]
                when {
                    // Script: 4 letters
                    part.length == 4 && part.first().isLetter() && part.all { it.isLetter() } -> {
                        script = part.replaceFirstChar { it.titlecase() }
                    }
                    // Region: 2 letters or 3 digits
                    part.length == 2 && part.all { it.isLetter() } -> {
                        region = part.uppercase()
                    }
                    part.length == 3 && part.all { it.isDigit() } -> {
                        region = part
                    }
                    // Variants: starts with digit or is 5+ chars starting with digit
                    part.length >= 5 || (part.isNotEmpty() && part.first().isDigit()) -> {
                        variants.add(part)
                    }
                    // Extensions: single letter followed by value(s)
                    part.length == 1 && i + 1 < parts.size -> {
                        val key = part
                        val values = mutableListOf<String>()
                        i++
                        while (i < parts.size && parts[i].length <= 2) {
                            values.add(parts[i])
                            i++
                        }
                        extensions[key] = values.joinToString("-")
                        continue
                    }
                }
                i++
            }
            
            return LanguageIdentifier(language, script, region, variants, extensions)
        }
        
        fun fromParts(language: String, script: String? = null, region: String? = null): LanguageIdentifier {
            return LanguageIdentifier(language, script, region)
        }
    }
    
    fun toTag(): String {
        val sb = StringBuilder(language)
        script?.let { sb.append("-$it") }
        region?.let { sb.append("-$it") }
        variants.forEach { sb.append("-$it") }
        extensions.forEach { (k, v) -> sb.append("-$k-$v") }
        return sb.toString()
    }
    
    override fun toString(): String = toTag()
    
    fun languageTag(): String = language
    
    fun withLanguage(lang: String): LanguageIdentifier = copy(language = lang)
    fun withScript(script: String?): LanguageIdentifier = copy(script = script)
    fun withRegion(region: String?): LanguageIdentifier = copy(region = region)
}

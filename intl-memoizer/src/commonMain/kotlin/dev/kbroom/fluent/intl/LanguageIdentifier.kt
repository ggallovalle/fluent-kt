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
    val extensions: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Parse a BCP 47 language tag into a LanguageIdentifier.
         */
        @Suppress("UnsafeCallOnNullableType", "UseOrEmpty")
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
                val classification = classifySubtag(part, parts, i)
                when (classification.kind) {
                    SubtagKind.SCRIPT -> script = part.replaceFirstChar { it.titlecase() }

                    SubtagKind.REGION -> region = regionValue(part)

                    SubtagKind.VARIANT -> variants.add(part)

                    SubtagKind.EXTENSION -> {
                        extensions[classification.extKey!!] = classification.extValue!!
                        i = classification.nextIndex
                        continue
                    }

                    SubtagKind.UNKNOWN -> { /* skip */ }
                }
                i++
            }

            return LanguageIdentifier(language, script, region, variants, extensions)
        }

        fun fromParts(language: String, script: String? = null, region: String? = null): LanguageIdentifier =
            LanguageIdentifier(language, script, region)

        private fun regionValue(part: String): String = if (part.length == 2) part.uppercase() else part

        private fun classifySubtag(part: String, parts: List<String>, index: Int): SubtagClassification = when {
            part.length == 4 && part.first().isLetter() && part.all { it.isLetter() } ->
                SubtagClassification(SubtagKind.SCRIPT)

            part.length == 2 && part.all { it.isLetter() } ->
                SubtagClassification(SubtagKind.REGION)

            part.length == 3 && part.all { it.isDigit() } ->
                SubtagClassification(SubtagKind.REGION)

            part.length >= 5 || (part.isNotEmpty() && part.first().isDigit()) ->
                SubtagClassification(SubtagKind.VARIANT)

            part.length == 1 && index + 1 < parts.size ->
                classifyExtension(part, parts, index)

            else -> SubtagClassification(SubtagKind.UNKNOWN)
        }

        private fun classifyExtension(key: String, parts: List<String>, index: Int): SubtagClassification {
            val values = mutableListOf<String>()
            var i = index + 1
            while (i < parts.size && parts[i].length <= 2) {
                values.add(parts[i])
                i++
            }
            return SubtagClassification(
                kind = SubtagKind.EXTENSION,
                extKey = key,
                extValue = values.joinToString("-"),
                nextIndex = i,
            )
        }

        private enum class SubtagKind { SCRIPT, REGION, VARIANT, EXTENSION, UNKNOWN }

        private data class SubtagClassification(
            val kind: SubtagKind,
            val extKey: String? = null,
            val extValue: String? = null,
            val nextIndex: Int = -1,
        )
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

package dev.kbroom.fluent.testing.syntax

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * JSON comparison utilities for parser fixtures.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Load all .ftl source files from a directory (classpath).
 */
fun loadSyntaxFixtures(dir: String): List<Pair<String, String>> {
    val loader = Thread.currentThread().contextClassLoader
        ?: java.lang.ClassLoader.getSystemClassLoader()
    
    val resources = loader.getResources("$dir/").toList()
    if (resources.isEmpty()) {
        return emptyList()
    }
    
    val results = mutableListOf<Pair<String, String>>()
    for (url in resources) {
        try {
            val baseDir = java.io.File(url.path)
            if (baseDir.exists() && baseDir.isDirectory) {
                val files = baseDir.listFiles()?.filter { 
                    it.extension == "ftl" && !it.name.contains("normalized") 
                } ?: continue
                for (f in files) {
                    results.add(f.name to f.readText())
                }
            }
        } catch (e: Exception) {
            // Skip inaccessible resources
        }
    }
    
    return results.sortedBy { it.first }
}

/**
 * Load expected JSON fixture as string.
 */
fun loadExpectedJson(filename: String, dir: String): String {
    val loader = Thread.currentThread().contextClassLoader
        ?: java.lang.ClassLoader.getSystemClassLoader()
    
    val jsonName = filename.removeSuffix(".ftl") + ".json"
    val resourceUrl = loader.getResource("$dir/$jsonName")
        ?: throw IllegalStateException("Expected JSON fixture not found: $dir/$jsonName")
    
    return resourceUrl.readText()
}

/**
 * Compare two JSON strings for structural equality.
 */
fun assertAstEquals(expected: String, actual: String, isCrlf: Boolean) {
    val expText = if (isCrlf) expected.replace("\r\n", "\n") else expected
    
    // Compare as JSON elements to handle different unicode escaping
    val expElement: JsonElement = json.decodeFromString(JsonElement.serializer(), expText)
    val actElement: JsonElement = json.decodeFromString(JsonElement.serializer(), actual)
    
    if (expElement.toString() != actElement.toString()) {
        throw AssertionError("AST mismatch:\nExpected: $expElement\nActual: $actElement")
    }
}

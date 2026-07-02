package dev.kbroom.fluent.testing.bundle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration

/**
 * YAML parser for resolver fixtures.
 */
private val yaml = Yaml(
    configuration = YamlConfiguration(
        strictMode = false
    )
)

/**
 * Load all YAML resolver fixtures from a directory.
 */
fun loadResolverFixtures(dir: String): List<TestFixture> {
    val loader = Thread.currentThread().contextClassLoader
        ?: java.lang.ClassLoader.getSystemClassLoader()
    
    val baseUrl = loader.getResource(dir)
        ?: return emptyList()
    
    val results = mutableListOf<TestFixture>()
    
    try {
        val baseDir = java.io.File(baseUrl.path)
        if (baseDir.exists() && baseDir.isDirectory) {
            val yamlFiles = baseDir.listFiles()?.filter { 
                it.extension == "yaml" || it.extension == "yml" 
            } ?: return emptyList()
            
            for (f in yamlFiles) {
                if (f.name == "defaults.yaml") continue // skip defaults, loaded separately
                try {
                    val text = f.readText()
                    val fixture = yaml.decodeFromString(TestFixture.serializer(), text)
                    results.add(fixture)
                } catch (e: Exception) {
                    System.err.println("Warning: Failed to load ${f.name}: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        System.err.println("Warning: Failed to load fixtures: ${e.message}")
    }
    
    return results
}

/**
 * Load defaults.yaml if present.
 */
fun loadDefaults(dir: String): TestDefaults? {
    val loader = Thread.currentThread().contextClassLoader
        ?: java.lang.ClassLoader.getSystemClassLoader()
    
    try {
        val resourceUrl = loader.getResource("$dir/defaults.yaml")
        if (resourceUrl != null) {
            val text = resourceUrl.readText()
            return yaml.decodeFromString(TestDefaults.serializer(), text)
        }
    } catch (e: Exception) {
        System.err.println("Warning: Failed to load defaults: ${e.message}")
    }
    
    return null
}

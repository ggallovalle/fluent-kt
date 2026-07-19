@file:JvmName("FluentBundleJvm")

package dev.kbroom.fluent.bundle

import java.io.File

/**
 * Parse and add an FTL file from disk (JVM only).
 *
 * Throws [IllegalArgumentException] if the file is missing or fails to parse.
 * Available only on JVM — Native targets should read the file content into
 * a String and pass it to the `String` overload of [resource].
 */
fun FluentBundleBuilder.resource(file: File): FluentBundleBuilder {
    require(file.exists()) { "FTL file not found: ${file.path}" }
    val parsed = FluentResource.tryNew(file.readText()).getOrElse {
        throw IllegalArgumentException("Failed to parse FTL file: ${file.path}", it)
    }
    return addResource(parsed).getOrElse {
        throw IllegalArgumentException("Resource conflicts in ${file.path}: ${it.message}")
    }
}

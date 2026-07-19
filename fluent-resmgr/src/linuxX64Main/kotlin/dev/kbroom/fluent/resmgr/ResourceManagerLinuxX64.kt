package dev.kbroom.fluent.resmgr

/**
 * LinuxX64 implementation - file system access not available in typical Kotlin/Native scenarios.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
actual fun readFileImpl(path: String): String? {
    // On Kotlin/Native, direct file system access requires cinterop
    // For typical use cases, resources are bundled or loaded via different mechanisms
    throw UnsupportedOperationException(
        "File system access is not supported in this configuration. " +
            "Use platform-specific resource loading mechanisms instead.",
    )
}

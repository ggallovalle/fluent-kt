package dev.kbroom.fluent.resmgr

import java.io.File

@Suppress("TooGenericExceptionCaught", "SwallowedException")
actual fun readFileImpl(path: String): String? = try {
    File(path).readText()
} catch (e: Exception) {
    null
}

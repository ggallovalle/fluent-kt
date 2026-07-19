package dev.kbroom.fluent.resmgr

import java.io.File

actual fun readFileImpl(path: String): String? = try {
    File(path).readText()
} catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
    null
}

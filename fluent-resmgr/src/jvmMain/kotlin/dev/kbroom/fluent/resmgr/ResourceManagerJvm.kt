package dev.kbroom.fluent.resmgr

import java.io.File

actual fun readFileImpl(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        null
    }
}

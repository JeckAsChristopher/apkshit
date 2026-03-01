package com.mide.ide.utils

import java.io.File
import java.security.MessageDigest

object ChecksumUtils {

    fun md5(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun md5(content: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(content.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

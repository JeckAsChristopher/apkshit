package com.mide.ide.utils

import java.io.File

object FileUtils {

    fun getMimeType(file: File): String = when (file.extension.lowercase()) {
        "kt" -> "text/x-kotlin"
        "java" -> "text/x-java"
        "xml" -> "text/xml"
        "json" -> "application/json"
        "gradle" -> "text/x-groovy"
        "md" -> "text/markdown"
        "txt" -> "text/plain"
        "png", "jpg", "jpeg", "gif", "webp" -> "image/${file.extension.lowercase()}"
        else -> "application/octet-stream"
    }

    fun isTextFile(file: File): Boolean {
        val textExtensions = setOf("kt", "java", "xml", "json", "gradle", "md", "txt",
            "properties", "pro", "html", "css", "js", "py", "sh", "yaml", "yml", "toml")
        return file.extension.lowercase() in textExtensions
    }

    fun isBinaryFile(file: File): Boolean = !isTextFile(file)

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "${"$"}bytes B"
        bytes < 1024 * 1024 -> "${"$"}{"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"$"}{"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"$"}{"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }

    fun deleteRecursively(dir: File): Boolean = dir.deleteRecursively()

    fun copyFile(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
    }

    fun readSafely(file: File): String? = try {
        if (file.exists() && file.length() < 5 * 1024 * 1024) file.readText() else null
    } catch (e: Exception) { null }

    fun writeAtomically(file: File, content: String) {
        val tmp = File(file.parentFile, "${"$"}{file.name}.tmp")
        tmp.writeText(content)
        tmp.renameTo(file)
    }
}

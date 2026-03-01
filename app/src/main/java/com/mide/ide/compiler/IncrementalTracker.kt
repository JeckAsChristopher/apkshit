package com.mide.ide.compiler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class IncrementalTracker(private val cacheFile: File) {

    private val gson = Gson()
    private val checksumMap: MutableMap<String, String> = loadCache()

    private fun loadCache(): MutableMap<String, String> {
        if (!cacheFile.exists()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            gson.fromJson(cacheFile.readText(), type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveCache() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(gson.toJson(checksumMap))
    }

    fun getChangedFiles(sourceFiles: List<File>): List<File> {
        return sourceFiles.filter { file ->
            val currentChecksum = file.checksum()
            val cachedChecksum = checksumMap[file.absolutePath]
            currentChecksum != cachedChecksum
        }
    }

    fun markFilesAsCompiled(files: List<File>) {
        files.forEach { file ->
            checksumMap[file.absolutePath] = file.checksum()
        }
        saveCache()
    }

    fun invalidateAll() {
        checksumMap.clear()
        if (cacheFile.exists()) cacheFile.delete()
    }

    fun hasAnyCache(): Boolean = checksumMap.isNotEmpty()

    private fun File.checksum(): String {
        if (!exists()) return ""
        val digest = MessageDigest.getInstance("MD5")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

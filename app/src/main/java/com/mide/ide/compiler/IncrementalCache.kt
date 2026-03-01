package com.mide.ide.compiler

import com.mide.ide.utils.ChecksumUtils
import java.io.File
import java.util.Properties

class IncrementalCache(private val cacheDir: File) {

    private val cacheFile = File(cacheDir, "incremental.properties")
    private val checksums = Properties()

    init {
        cacheDir.mkdirs()
        if (cacheFile.exists()) {
            checksums.load(cacheFile.inputStream())
        }
    }

    fun hasFileChanged(file: File): Boolean {
        if (!file.exists()) return false
        val key = file.absolutePath
        val currentChecksum = ChecksumUtils.md5(file)
        val cachedChecksum = checksums.getProperty(key)
        return currentChecksum != cachedChecksum
    }

    fun recordFile(file: File) {
        if (!file.exists()) return
        val key = file.absolutePath
        checksums.setProperty(key, ChecksumUtils.md5(file))
        saveCache()
    }

    fun recordFiles(files: List<File>) {
        files.forEach { file ->
            if (file.exists()) {
                checksums.setProperty(file.absolutePath, ChecksumUtils.md5(file))
            }
        }
        saveCache()
    }

    fun getChangedFiles(files: List<File>): List<File> {
        return files.filter { hasFileChanged(it) }
    }

    fun invalidate() {
        checksums.clear()
        saveCache()
    }

    private fun saveCache() {
        checksums.store(cacheFile.outputStream(), "MIDE Incremental Build Cache")
    }
}

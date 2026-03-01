package com.mide.ide.plugin

import android.content.Context
import com.google.gson.Gson
import com.mide.ide.MIDEApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.File
import java.security.MessageDigest

class PluginManager(private val context: Context) {

    private val pluginsDir = MIDEApplication.get().pluginsDir
    private val gson = Gson()
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    data class LoadedPlugin(
        val manifest: PluginManifest,
        val dir: File,
        val isEnabled: Boolean
    )

    data class InstallResult(
        val success: Boolean,
        val manifest: PluginManifest? = null,
        val error: String? = null
    )

    suspend fun installPlugin(zipFile: File, allowUnverified: Boolean = false): InstallResult =
        withContext(Dispatchers.IO) {
            val manifest = extractManifest(zipFile)
                ?: return@withContext InstallResult(false, error = "Invalid plugin: missing manifest.json")

            val securityCheck = performSecurityScan(zipFile)
            if (!securityCheck.passed && !allowUnverified) {
                return@withContext InstallResult(false, error = "Security check failed: ${securityCheck.reason}")
            }

            val pluginDir = File(pluginsDir, manifest.id)
            if (pluginDir.exists()) pluginDir.deleteRecursively()
            pluginDir.mkdirs()

            extractPlugin(zipFile, pluginDir)
            saveManifest(manifest, pluginDir)

            loadedPlugins[manifest.id] = LoadedPlugin(manifest, pluginDir, true)
            InstallResult(true, manifest)
        }

    fun getInstalledPlugins(): List<LoadedPlugin> {
        refreshLoadedPlugins()
        return loadedPlugins.values.toList()
    }

    fun uninstallPlugin(pluginId: String): Boolean {
        val plugin = loadedPlugins[pluginId] ?: return false
        loadedPlugins.remove(pluginId)
        return plugin.dir.deleteRecursively()
    }

    private fun refreshLoadedPlugins() {
        loadedPlugins.clear()
        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val manifest = gson.fromJson(manifestFile.readText(), PluginManifest::class.java)
                    loadedPlugins[manifest.id] = LoadedPlugin(manifest, dir, true)
                } catch (e: Exception) {
                    // Skip invalid plugins
                }
            }
        }
    }

    private fun extractManifest(zipFile: File): PluginManifest? {
        return try {
            ZipArchiveInputStream(zipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.json") {
                        val content = zip.readBytes().decodeToString()
                        return gson.fromJson(content, PluginManifest::class.java)
                    }
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: Exception) { null }
    }

    private fun extractPlugin(zipFile: File, targetDir: File) {
        ZipArchiveInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(targetDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                }
                entry = zip.nextEntry
            }
        }
    }

    private fun saveManifest(manifest: PluginManifest, dir: File) {
        File(dir, "manifest.json").writeText(gson.toJson(manifest))
    }

    private data class SecurityCheckResult(val passed: Boolean, val reason: String = "")

    private fun performSecurityScan(zipFile: File): SecurityCheckResult {
        val dangerousPatterns = listOf(
            "Runtime.exec", "ProcessBuilder", "System.exit",
            "ClassLoader", "reflect.Method", "dalvik.system",
            "android.app.admin", "su "
        )

        return try {
            ZipArchiveInputStream(zipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".js")) {
                        val content = zip.readBytes().decodeToString()
                        dangerousPatterns.forEach { pattern ->
                            if (content.contains(pattern)) {
                                return SecurityCheckResult(false, "Dangerous pattern '$pattern' found in ${entry.name}")
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            SecurityCheckResult(true)
        } catch (e: Exception) {
            SecurityCheckResult(false, "Failed to scan plugin: ${e.message}")
        }
    }
}

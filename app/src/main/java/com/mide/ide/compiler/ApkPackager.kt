package com.mide.ide.compiler

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkPackager {

    data class PackageResult(
        val success: Boolean,
        val unsignedApk: File?,
        val error: String?
    )

    fun packageApk(
        dexFile: File,
        resourcesApk: File,
        nativeLibsDir: File?,
        outputDir: File,
        onLog: (String) -> Unit
    ): PackageResult {
        outputDir.mkdirs()
        val unsignedApk = File(outputDir, "app-unsigned.apk")

        return try {
            onLog("Packaging APK...")
            FileOutputStream(unsignedApk).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.setMethod(ZipOutputStream.DEFLATED)

                    // Add resources from the resource-linked APK
                    if (resourcesApk.exists()) {
                        ZipFile(resourcesApk).use { resZip ->
                            resZip.entries().asSequence().forEach { entry ->
                                onLog("Adding resource: ${entry.name}")
                                zos.putNextEntry(ZipEntry(entry.name))
                                resZip.getInputStream(entry).copyTo(zos)
                                zos.closeEntry()
                            }
                        }
                    }

                    // Add classes.dex
                    onLog("Adding classes.dex...")
                    zos.putNextEntry(ZipEntry("classes.dex"))
                    dexFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()

                    // Add native libraries if present
                    nativeLibsDir?.walkTopDown()
                        ?.filter { it.isFile && it.extension == "so" }
                        ?.forEach { soFile ->
                            val abi = soFile.parentFile?.name ?: "arm64-v8a"
                            val entryName = "lib/$abi/${soFile.name}"
                            onLog("Adding native lib: $entryName")
                            zos.putNextEntry(ZipEntry(entryName))
                            soFile.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                }
            }
            onLog("APK packaged: ${unsignedApk.absolutePath}")
            PackageResult(true, unsignedApk, null)
        } catch (e: Exception) {
            PackageResult(false, null, "APK packaging failed: ${e.message}")
        }
    }
}

package com.mide.ide.compiler

import java.io.File

class ResourceCompiler(private val aapt2Binary: File, private val androidJar: File) {

    data class ResourceResult(
        val success: Boolean,
        val compiledResourcesDir: File?,
        val linkedApk: File?,
        val rJavaDir: File?,
        val error: String?
    )

    fun compileAndLink(
        manifestFile: File,
        resDir: File,
        outputDir: File,
        packageName: String,
        onLog: (String) -> Unit
    ): ResourceResult {
        if (!aapt2Binary.exists()) {
            return ResourceResult(false, null, null, null, "aapt2 not found at ${aapt2Binary.absolutePath}")
        }
        if (!androidJar.exists()) {
            return ResourceResult(false, null, null, null, "android.jar not found at ${androidJar.absolutePath}")
        }

        val compiledResDir = File(outputDir, "compiled_res").apply { mkdirs() }
        val rJavaDir = File(outputDir, "r_java").apply { mkdirs() }
        val linkedApk = File(outputDir, "resources.ap_")

        // Step 1: Compile each resource file
        if (resDir.exists()) {
            resDir.walkTopDown()
                .filter { it.isFile && it.extension == "xml" || it.extension == "png"
                        || it.extension == "jpg" || it.extension == "9.png" }
                .forEach { resFile ->
                    val compileResult = compileResource(resFile, compiledResDir, onLog)
                    if (!compileResult) {
                        return ResourceResult(false, null, null, null, "Failed to compile: ${resFile.name}")
                    }
                }
        }

        // Step 2: Link resources
        val linkCommand = buildList {
            add(aapt2Binary.absolutePath)
            add("link")
            add("--proto-format")
            add("-o")
            add(linkedApk.absolutePath)
            add("-I")
            add(androidJar.absolutePath)
            add("--manifest")
            add(manifestFile.absolutePath)
            add("--java")
            add(rJavaDir.absolutePath)
            add("--auto-add-overlay")
            add("--min-sdk-version")
            add("26")
            add("--target-sdk-version")
            add("34")

            if (compiledResDir.listFiles()?.isNotEmpty() == true) {
                compiledResDir.listFiles()?.filter { it.extension == "flat" }?.forEach {
                    add(it.absolutePath)
                }
            }
        }

        onLog("Linking resources...")
        val process = ProcessBuilder(linkCommand)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        output.lines().forEach { if (it.isNotBlank()) onLog(it) }

        return if (exitCode == 0) {
            onLog("Resources compiled and linked successfully")
            ResourceResult(true, compiledResDir, linkedApk, rJavaDir, null)
        } else {
            ResourceResult(false, null, null, null, "aapt2 link failed: $output")
        }
    }

    private fun compileResource(file: File, outputDir: File, onLog: (String) -> Unit): Boolean {
        val command = listOf(
            aapt2Binary.absolutePath,
            "compile",
            file.absolutePath,
            "-o",
            outputDir.absolutePath
        )
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            onLog("Resource compile error for ${file.name}: $output")
        }
        return exitCode == 0
    }
}

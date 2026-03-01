package com.mide.ide.compiler

import java.io.File

class DexCompiler(private val d8Binary: File) {

    data class DexResult(
        val success: Boolean,
        val dexFile: File?,
        val error: String?
    )

    fun compile(
        classFiles: List<File>,
        outputDir: File,
        minSdk: Int = 26,
        onLog: (String) -> Unit
    ): DexResult {
        if (!d8Binary.exists()) {
            return DexResult(false, null, "d8 tool not found at ${d8Binary.absolutePath}")
        }
        outputDir.mkdirs()
        val outputDex = File(outputDir, "classes.dex")

        val command = buildList {
            add(d8Binary.absolutePath)
            add("--min-api")
            add(minSdk.toString())
            add("--output")
            add(outputDir.absolutePath)
            addAll(classFiles.map { it.absolutePath })
        }

        onLog("Running D8: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        output.lines().forEach { line -> if (line.isNotBlank()) onLog(line) }

        return if (exitCode == 0 && outputDex.exists()) {
            onLog("DEX compilation successful: ${outputDex.absolutePath}")
            DexResult(true, outputDex, null)
        } else {
            DexResult(false, null, "D8 failed with exit code $exitCode: $output")
        }
    }
}

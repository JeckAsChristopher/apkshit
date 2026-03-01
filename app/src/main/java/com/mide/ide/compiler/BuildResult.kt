package com.mide.ide.compiler

import java.io.File

sealed class BuildResult {
    data class Success(
        val apkFile: File,
        val durationMs: Long,
        val warnings: List<String> = emptyList()
    ) : BuildResult()

    data class Failure(
        val errors: List<BuildError>,
        val durationMs: Long
    ) : BuildResult()
}

data class BuildError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity { ERROR, WARNING, INFO }

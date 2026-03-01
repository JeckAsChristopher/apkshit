package com.mide.ide.compiler

import com.mide.ide.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class BuildEngine(private val project: Project) {

    enum class BuildState { IDLE, COMPILING_JAVA, COMPILING_DEX, COMPILING_RESOURCES, PACKAGING, SIGNING, DONE }

    private val _buildState = MutableStateFlow(BuildState.IDLE)
    val buildState: StateFlow<BuildState> = _buildState

    private val logBuffer = StringBuilder()
    private val incrementalTracker = IncrementalTracker(
        File(project.intermediatesDir, "build_cache.json")
    )

    suspend fun build(
        forceClean: Boolean = false,
        onLog: (String) -> Unit
    ): BuildResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        logBuffer.clear()

        val log: (String) -> Unit = { msg ->
            logBuffer.appendLine(msg)
            onLog(msg)
        }

        try {
            if (forceClean) {
                log("Build: Cleaning previous build outputs...")
                project.buildDir.deleteRecursively()
                incrementalTracker.invalidateAll()
            }

            project.ensureBuildDirs()

            // Gather all Java source files
            val allSources = project.sourceDir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .toList()

            if (allSources.isEmpty()) {
                return@withContext BuildResult.Failure(
                    errors = listOf(BuildResult.BuildError("", 0, 0, "No Java source files found in project.", BuildResult.BuildError.Severity.ERROR)),
                    log = logBuffer.toString()
                )
            }

            // Incremental: only compile changed files
            val changedFiles = if (incrementalTracker.hasAnyCache()) {
                incrementalTracker.getChangedFiles(allSources).also {
                    if (it.isEmpty()) {
                        log("Build: No source changes detected, skipping Java compilation")
                    } else {
                        log("Build: ${it.size} file(s) changed, recompiling...")
                    }
                }
            } else {
                log("Build: First build - compiling all ${allSources.size} source file(s)")
                allSources
            }

            // Step 1: Java compilation
            if (changedFiles.isNotEmpty()) {
                _buildState.value = BuildState.COMPILING_JAVA
                log("=== Step 1/5: Java Compilation ===")
                val javaResult = JavaCompiler(project).compile(allSources, log)
                if (!javaResult.success) {
                    _buildState.value = BuildState.DONE
                    return@withContext BuildResult.Failure(javaResult.errors, logBuffer.toString())
                }
                incrementalTracker.markFilesAsCompiled(changedFiles)
            }

            // Step 2: DEX compilation
            _buildState.value = BuildState.COMPILING_DEX
            log("=== Step 2/5: DEX Compilation ===")
            val dexResult = DexCompiler(project).compile(log)
            if (!dexResult.success) {
                _buildState.value = BuildState.DONE
                return@withContext BuildResult.Failure(
                    listOf(BuildResult.BuildError("dex", 0, 0, dexResult.log, BuildResult.BuildError.Severity.ERROR)),
                    logBuffer.toString()
                )
            }

            // Step 3: Resource compilation
            _buildState.value = BuildState.COMPILING_RESOURCES
            log("=== Step 3/5: Resource Compilation ===")
            val resourceResult = ResourceCompiler(project).compile(log)
            if (!resourceResult.success) {
                _buildState.value = BuildState.DONE
                return@withContext BuildResult.Failure(
                    listOf(BuildResult.BuildError("res", 0, 0, resourceResult.log, BuildResult.BuildError.Severity.ERROR)),
                    logBuffer.toString()
                )
            }

            // Step 4: APK packaging
            _buildState.value = BuildState.PACKAGING
            log("=== Step 4/5: APK Packaging ===")
            val packageResult = ApkPackager(project).packageApk(resourceResult.linkedApk, log)
            if (!packageResult.success) {
                _buildState.value = BuildState.DONE
                return@withContext BuildResult.Failure(
                    listOf(BuildResult.BuildError("apk", 0, 0, packageResult.log, BuildResult.BuildError.Severity.ERROR)),
                    logBuffer.toString()
                )
            }

            // Step 5: APK signing
            _buildState.value = BuildState.SIGNING
            log("=== Step 5/5: APK Signing ===")
            val signResult = ApkSigner(project).sign(
                unsignedApk = packageResult.unsignedApk!!,
                outputApk = project.apkFile,
                onLog = log
            )

            _buildState.value = BuildState.DONE

            return@withContext if (signResult.success) {
                val duration = System.currentTimeMillis() - startTime
                log("=== Build Successful in ${duration}ms ===")
                log("APK: ${signResult.signedApk?.absolutePath}")
                BuildResult.Success(
                    apkFile = signResult.signedApk!!,
                    durationMs = duration,
                    log = logBuffer.toString()
                )
            } else {
                BuildResult.Failure(
                    listOf(BuildResult.BuildError("signing", 0, 0, signResult.log, BuildResult.BuildError.Severity.ERROR)),
                    logBuffer.toString()
                )
            }
        } catch (e: Exception) {
            _buildState.value = BuildState.DONE
            log("=== Build Failed: ${e.message} ===")
            BuildResult.Failure(
                listOf(BuildResult.BuildError("", 0, 0, e.message ?: "Unknown error", BuildResult.BuildError.Severity.ERROR)),
                logBuffer.toString()
            )
        }
    }
}

package com.mide.ide.compiler

import com.mide.ide.MIDEApplication
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

    private val toolsDir get() = MIDEApplication.get().toolsDir
    private val d8Binary get() = File(toolsDir, "d8")
    private val aapt2Binary get() = File(toolsDir, "aapt2")
    private val androidJar get() = File(toolsDir, "android.jar")

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

            val allSources = project.sourceDir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .toList()

            if (allSources.isEmpty()) {
                return@withContext BuildResult.Failure(
                    errors = listOf(BuildError("", 0, 0, "No Java source files found in project.", ErrorSeverity.ERROR)),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

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
                val javaResult = JavaCompiler().compile(
                    sourceFiles = allSources,
                    classesOutputDir = project.classesDir,
                    classpath = listOf(androidJar),
                    onLog = log
                )
                if (!javaResult.success) {
                    _buildState.value = BuildState.DONE
                    return@withContext BuildResult.Failure(
                        errors = javaResult.errors,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                incrementalTracker.markFilesAsCompiled(changedFiles)
            }

            // Step 2: DEX compilation
            _buildState.value = BuildState.COMPILING_DEX
            log("=== Step 2/5: DEX Compilation ===")
            val classFiles = project.classesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .toList()
            val dexResult = DexCompiler(d8Binary).compile(
                classFiles = classFiles,
                outputDir = project.dexDir,
                minSdk = project.minSdk,
                onLog = log
            )
            if (!dexResult.success) {
                _buildState.value = BuildState.DONE
                return@withContext BuildResult.Failure(
                    errors = listOf(BuildError("dex", 0, 0, dexResult.error ?: "DEX compilation failed", ErrorSeverity.ERROR)),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // Step 3: Resource compilation
            _buildState.value = BuildState.COMPILING_RESOURCES
            log("=== Step 3/5: Resource Compilation ===")
            val resourceResult = ResourceCompiler(aapt2Binary, androidJar).compileAndLink(
                manifestFile = project.manifestFile,
                resDir = project.resDir,
                outputDir = project.compiledResDir,
                packageName = project.packageName,
                onLog = log
            )
            if (!resourceResult.success) {
                _buildState.value = BuildState.DONE
                return@withContext BuildResult.Failure(
                    errors = listOf(BuildError("res", 0, 0, resourceResult.error ?: "Resource compilation failed", ErrorSeverity.ERROR)),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // Step 4: APK packaging
            _buildState.value = BuildState.PACKAGING
            log("=== Step 4/5: APK Packaging ===")
            val packageResult = ApkPackager().packageApk(
                dexFile = dexResult.dexFile!!,
                resourcesApk = resourceResult.linkedApk!!,
                nativeLibsDir = null,
                outputDir = project.outputDir,
                onLog = log
            )
            if (!packageResult.success) {
                _buildState.value = BuildState.DONE
                return@withContext BuildResult.Failure(
                    errors = listOf(BuildError("apk", 0, 0, packageResult.error ?: "APK packaging failed", ErrorSeverity.ERROR)),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // Step 5: APK signing
            _buildState.value = BuildState.SIGNING
            log("=== Step 5/5: APK Signing ===")
            val signResult = ApkSigner().sign(
                unsignedApk = packageResult.unsignedApk!!,
                outputDir = project.outputDir,
                onLog = log
            )

            _buildState.value = BuildState.DONE
            val duration = System.currentTimeMillis() - startTime

            return@withContext if (signResult.success) {
                log("=== Build Successful in ${duration}ms ===")
                log("APK: ${signResult.signedApk?.absolutePath}")
                BuildResult.Success(
                    apkFile = signResult.signedApk!!,
                    durationMs = duration
                )
            } else {
                BuildResult.Failure(
                    errors = listOf(BuildError("signing", 0, 0, signResult.error ?: "Signing failed", ErrorSeverity.ERROR)),
                    durationMs = duration
                )
            }
        } catch (e: Exception) {
            _buildState.value = BuildState.DONE
            log("=== Build Failed: ${e.message} ===")
            BuildResult.Failure(
                errors = listOf(BuildError("", 0, 0, e.message ?: "Unknown error", ErrorSeverity.ERROR)),
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

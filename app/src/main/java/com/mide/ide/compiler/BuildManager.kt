package com.mide.ide.compiler

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mide.ide.MIDEApplication
import com.mide.ide.project.BuildType
import com.mide.ide.project.MIDEProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BuildManager(private val context: Context) {

    private val app = MIDEApplication.get()
    private val toolsDir = app.toolsDir
    private val buildCacheDir = app.buildCacheDir

    private val d8Binary = File(toolsDir, "d8")
    private val aapt2Binary = File(toolsDir, "aapt2")
    private val androidJar = File(toolsDir, "android.jar")

    suspend fun build(
        project: MIDEProject,
        onProgress: (step: String, progress: Int, message: String) -> Unit
    ): BuildResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val buildDir = File(buildCacheDir, project.name)
        project.ensureBuildDirs()
        buildDir.mkdirs()

        val incrementalCache = IncrementalCache(File(buildDir, "incremental"))
        val logLines = mutableListOf<String>()
        val allErrors = mutableListOf<BuildError>()
        val allWarnings = mutableListOf<BuildError>()

        fun log(msg: String) {
            logLines.add(msg)
        }

        // Verify tools are present
        if (!androidJar.exists()) {
            return@withContext BuildResult.Failure(
                errors = listOf(BuildError("", 0, 0, "android.jar not found. Please wait for tools to download.", ErrorSeverity.ERROR)),
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 1: Collect source files
        onProgress("INIT", 5, "Scanning project files...")
        val sourceFiles = app.let {
            val pm = com.mide.ide.project.ProjectManager(context)
            pm.getAllSourceFiles(project)
        }
        log("Found ${sourceFiles.size} source files")

        // Step 2: Determine changed files for incremental build
        val changedFiles = incrementalCache.getChangedFiles(sourceFiles)
        log("${changedFiles.size} files changed since last build")

        val classesDir = File(buildDir, "classes")
        val dexDir = File(buildDir, "dex")
        val resDir = File(buildDir, "res")
        val apkDir = File(buildDir, "apk")

        // Step 3: Resource compilation
        onProgress("RESOURCES", 15, "Compiling resources...")
        val resourceCompiler = ResourceCompiler(aapt2Binary, androidJar)
        val resResult = resourceCompiler.compileAndLink(
            manifestFile = project.manifestFile,
            resDir = project.resDir,
            outputDir = resDir,
            packageName = project.packageName
        ) { msg -> log(msg) }

        if (!resResult.success) {
            return@withContext BuildResult.Failure(
                errors = listOf(BuildError("", 0, 0, resResult.error ?: "Resource compilation failed", ErrorSeverity.ERROR)),
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 4: Java compilation
        onProgress("JAVA", 35, "Compiling Java sources...")
        val javaFiles = (if (changedFiles.isEmpty() && classesDir.listFiles()?.isNotEmpty() == true) {
            log("No Java files changed, skipping Java compilation")
            emptyList()
        } else {
            sourceFiles.filter { it.extension == "java" }
        })

        if (javaFiles.isNotEmpty()) {
            val classpath = buildList {
                add(androidJar)
                project.libsDir.listFiles()?.filter { it.extension == "jar" }?.forEach { add(it) }
                resResult.rJavaDir?.let { add(it) }
            }

            val javaCompiler = JavaCompiler()
            val compileResult = javaCompiler.compile(javaFiles, classesDir, classpath) { msg -> log(msg) }

            allErrors.addAll(compileResult.errors)
            allWarnings.addAll(compileResult.warnings)

            if (!compileResult.success) {
                return@withContext BuildResult.Failure(
                    errors = allErrors,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
            incrementalCache.recordFiles(javaFiles)
        }

        // Step 5: DEX compilation
        onProgress("DEX", 60, "Converting to DEX format...")
        val classFiles = classesDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .toList()

        val dexCompiler = DexCompiler(d8Binary)
        val dexResult = dexCompiler.compile(
            classFiles = classFiles,
            outputDir = dexDir,
            minSdk = project.buildConfig.minSdk
        ) { msg -> log(msg) }

        if (!dexResult.success) {
            return@withContext BuildResult.Failure(
                errors = listOf(BuildError("", 0, 0, dexResult.error ?: "DEX compilation failed", ErrorSeverity.ERROR)),
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 6: APK packaging
        onProgress("PACKAGE", 80, "Packaging APK...")
        val packager = ApkPackager()
        val packageResult = packager.packageApk(
            dexFile = dexResult.dexFile!!,
            resourcesApk = resResult.linkedApk!!,
            nativeLibsDir = File(project.rootDir, "jniLibs").takeIf { it.exists() },
            outputDir = apkDir
        ) { msg -> log(msg) }

        if (!packageResult.success) {
            return@withContext BuildResult.Failure(
                errors = listOf(BuildError("", 0, 0, packageResult.error ?: "Packaging failed", ErrorSeverity.ERROR)),
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 7: APK signing
        onProgress("SIGN", 90, "Signing APK...")
        val signer = ApkSigner()
        val signResult = signer.sign(
            unsignedApk = packageResult.unsignedApk!!,
            outputDir = apkDir,
            useDebug = project.buildConfig.buildType == BuildType.DEBUG
        ) { msg -> log(msg) }

        if (!signResult.success) {
            return@withContext BuildResult.Failure(
                errors = listOf(BuildError("", 0, 0, signResult.error ?: "Signing failed", ErrorSeverity.ERROR)),
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        onProgress("DONE", 100, "Build successful!")
        val duration = System.currentTimeMillis() - startTime
        BuildResult.Success(
            apkFile = signResult.signedApk!!,
            durationMs = duration,
            warnings = allWarnings.map { it.message }
        )
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

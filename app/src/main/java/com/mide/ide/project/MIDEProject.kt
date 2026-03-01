package com.mide.ide.project

import java.io.File

data class MIDEProject(
    val name: String,
    val packageName: String,
    val rootDir: File,
    val buildConfig: BuildConfig
) {
    val srcDir get() = File(rootDir, "src/main/java")
    val sourceDir get() = srcDir
    val resDir get() = File(rootDir, "src/main/res")
    val manifestFile get() = File(rootDir, "src/main/AndroidManifest.xml")
    val buildDir get() = File(rootDir, "build")
    val outputDir get() = File(buildDir, "outputs")
    val intermediatesDir get() = File(buildDir, "intermediates")
    val classesDir get() = File(intermediatesDir, "classes")
    val dexDir get() = File(intermediatesDir, "dex")
    val compiledResDir get() = File(intermediatesDir, "res_compiled")
    val libsDir get() = File(rootDir, "libs")
    val configFile get() = File(rootDir, "mide_project.json")
    val apkFile get() = File(outputDir, "$name-debug.apk")
    val minSdk get() = buildConfig.minSdk

    fun ensureBuildDirs() {
        listOf(
            buildDir, outputDir, intermediatesDir,
            classesDir, dexDir, compiledResDir,
            File(buildDir, "apk")
        ).forEach { it.mkdirs() }
    }
}

data class BuildConfig(
    val minSdk: Int = 26,
    val targetSdk: Int = 34,
    val compileSdk: Int = 34,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val buildType: BuildType = BuildType.DEBUG,
    val abiFilters: List<String> = listOf("arm64-v8a", "x86_64")
)

enum class BuildType { DEBUG, RELEASE }

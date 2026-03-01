package com.mide.ide.project

import java.io.File

data class MIDEProject(
    val name: String,
    val packageName: String,
    val rootDir: File,
    val buildConfig: BuildConfig
) {
    val srcDir get() = File(rootDir, "src/main/java")
    val resDir get() = File(rootDir, "src/main/res")
    val manifestFile get() = File(rootDir, "src/main/AndroidManifest.xml")
    val buildDir get() = File(rootDir, "build")
    val libsDir get() = File(rootDir, "libs")
    val configFile get() = File(rootDir, "mide_project.json")

    fun ensureBuildDirs() {
        listOf(
            buildDir,
            File(buildDir, "classes"),
            File(buildDir, "dex"),
            File(buildDir, "res"),
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

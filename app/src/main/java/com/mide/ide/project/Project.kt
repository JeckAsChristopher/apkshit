package com.mide.ide.project

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class Project(
    val name: String,
    val packageName: String,
    val rootDir: File,
    val minSdk: Int = 21,
    val targetSdk: Int = 34,
    val buildToolsVersion: String = "34.0.0"
) : Parcelable {

    val sourceDir: File get() = File(rootDir, "src/main/java")
    val resDir: File get() = File(rootDir, "src/main/res")
    val manifestFile: File get() = File(rootDir, "src/main/AndroidManifest.xml")
    val buildDir: File get() = File(rootDir, "build")
    val outputDir: File get() = File(buildDir, "outputs")
    val intermediatesDir: File get() = File(buildDir, "intermediates")
    val classesDir: File get() = File(intermediatesDir, "classes")
    val dexDir: File get() = File(intermediatesDir, "dex")
    val compiledResDir: File get() = File(intermediatesDir, "res_compiled")
    val apkFile: File get() = File(outputDir, "${name}-debug.apk")
    val buildConfigFile: File get() = File(rootDir, "build_config.json")

    fun ensureBuildDirs() {
        listOf(buildDir, outputDir, intermediatesDir, classesDir, dexDir, compiledResDir).forEach { it.mkdirs() }
    }
}

package com.mide.ide.project

import android.content.Context
import com.google.gson.Gson
import com.mide.ide.MIDEApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProjectManager(private val context: Context) {

    private val gson = Gson()
    private val projectsDir get() = MIDEApplication.get().projectsDir

    suspend fun createProject(
        name: String,
        template: ProjectTemplate,
        packageName: String? = null
    ): MIDEProject? = withContext(Dispatchers.IO) {
        val safeName = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
        val pkg = packageName ?: "com.example.${safeName.lowercase().replace(Regex("[^a-z0-9]"), "")}"
        val rootDir = File(projectsDir, safeName)
        if (rootDir.exists()) return@withContext null

        rootDir.mkdirs()
        template.generate(rootDir, safeName, pkg)

        val buildConfig = BuildConfig()
        val project = MIDEProject(safeName, pkg, rootDir, buildConfig)
        saveProjectConfig(project)
        project
    }

    fun listProjects(): List<MIDEProject> {
        return projectsDir.listFiles()
            ?.filter { it.isDirectory && File(it, "mide_project.json").exists() }
            ?.mapNotNull { loadProject(it) }
            ?: emptyList()
    }

    fun loadProject(dir: File): MIDEProject? {
        val configFile = File(dir, "mide_project.json")
        if (!configFile.exists()) return null
        return try {
            val json = configFile.readText()
            val config = gson.fromJson(json, ProjectConfigJson::class.java)
            MIDEProject(
                name = config.name,
                packageName = config.packageName,
                rootDir = dir,
                buildConfig = BuildConfig(
                    minSdk = config.minSdk,
                    targetSdk = config.targetSdk,
                    compileSdk = config.compileSdk,
                    versionCode = config.versionCode,
                    versionName = config.versionName
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveProjectConfig(project: MIDEProject) {
        val config = ProjectConfigJson(
            name = project.name,
            packageName = project.packageName,
            minSdk = project.buildConfig.minSdk,
            targetSdk = project.buildConfig.targetSdk,
            compileSdk = project.buildConfig.compileSdk,
            versionCode = project.buildConfig.versionCode,
            versionName = project.buildConfig.versionName
        )
        project.configFile.writeText(gson.toJson(config))
    }

    fun deleteProject(project: MIDEProject): Boolean {
        return project.rootDir.deleteRecursively()
    }

    fun getAllSourceFiles(project: MIDEProject): List<File> {
        val files = mutableListOf<File>()
        listOf(project.srcDir, File(project.rootDir, "src/main/kotlin")).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
                    .forEach { files.add(it) }
            }
        }
        return files
    }

    private data class ProjectConfigJson(
        val name: String,
        val packageName: String,
        val minSdk: Int,
        val targetSdk: Int,
        val compileSdk: Int,
        val versionCode: Int,
        val versionName: String
    )
}

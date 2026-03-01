package com.mide.ide

import android.app.Application
import android.content.Context
import com.mide.ide.downloader.ToolDownloadManager
import com.mide.ide.plugin.PluginManager
import com.mide.ide.project.ProjectManager
import com.mide.ide.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MIDEApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var projectManager: ProjectManager
        private set
    lateinit var pluginManager: PluginManager
        private set
    lateinit var toolDownloadManager: ToolDownloadManager
        private set
    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeManagers()
        ensureDirectoriesExist()
        applicationScope.launch {
            toolDownloadManager.checkAndDownloadRequiredTools()
        }
    }

    private fun initializeManagers() {
        preferencesManager = PreferencesManager(this)
        projectManager = ProjectManager(this)
        pluginManager = PluginManager(this)
        toolDownloadManager = ToolDownloadManager(this)
    }

    private fun ensureDirectoriesExist() {
        listOf(projectsDir, toolsDir, pluginsDir, buildCacheDir, keystoreDir).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    val projectsDir get() = getDir("projects", Context.MODE_PRIVATE)
    val toolsDir get() = getDir("tools", Context.MODE_PRIVATE)
    val pluginsDir get() = getDir("plugins", Context.MODE_PRIVATE)
    val buildCacheDir get() = getDir("build_cache", Context.MODE_PRIVATE)
    val keystoreDir get() = getDir("keystores", Context.MODE_PRIVATE)

    companion object {
        lateinit var instance: MIDEApplication
            private set

        fun get(): MIDEApplication = instance
    }
}

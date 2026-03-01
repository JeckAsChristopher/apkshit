package com.mide.ide

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mide.ide.compiler.BuildManager
import com.mide.ide.compiler.BuildResult
import com.mide.ide.plugin.PluginStoreFragment
import com.mide.ide.project.MIDEProject
import com.mide.ide.project.ProjectManager
import com.mide.ide.project.ProjectTemplate
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _currentProject = MutableLiveData<MIDEProject?>()
    val currentProject: LiveData<MIDEProject?> = _currentProject

    private val _buildProgress = MutableLiveData(0)
    val buildProgress: LiveData<Int> = _buildProgress

    private val _buildStatus = MutableLiveData("Ready")
    val buildStatus: LiveData<String> = _buildStatus

    private val _buildOutput = MutableLiveData<List<String>>(emptyList())
    val buildOutput: LiveData<List<String>> = _buildOutput

    private val _buildResult = MutableLiveData<BuildResult?>()
    val buildResult: LiveData<BuildResult?> = _buildResult

    private val outputLines = mutableListOf<String>()

    fun setCurrentProject(project: MIDEProject) {
        _currentProject.value = project
        _buildStatus.value = "Project loaded: ${project.name}"
    }

    fun onStoragePermissionGranted() {
        _buildStatus.value = "Storage permission granted"
    }

    fun startBuild(buildManager: BuildManager, project: MIDEProject) {
        viewModelScope.launch {
            outputLines.clear()
            _buildOutput.value = emptyList()
            _buildResult.value = null
            _buildProgress.value = 0
            _buildStatus.value = "Building..."

            buildManager.build(project) { step, progress, message ->
                outputLines.add("[$step] $message")
                _buildOutput.postValue(outputLines.toList())
                _buildProgress.postValue(progress)
                _buildStatus.postValue(message)
            }
        }
    }

    fun showNewProjectDialog(context: Context) {
        val input = EditText(context).apply { hint = "Project name" }
        AlertDialog.Builder(context)
            .setTitle("New Project")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    createProject(context, name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createProject(context: Context, name: String) {
        viewModelScope.launch {
            val app = MIDEApplication.get()
            val pm = ProjectManager(context)
            val project = pm.createProject(name, ProjectTemplate.EMPTY_JAVA)
            project?.let { setCurrentProject(it) }
        }
    }

    fun showOpenProjectDialog(context: Context) {
        val app = MIDEApplication.get()
        val pm = ProjectManager(context)
        val projects = pm.listProjects()
        if (projects.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Open Project")
                .setMessage("No projects found. Create a new project first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = projects.map { it.name }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Open Project")
            .setItems(names) { _, index ->
                setCurrentProject(projects[index])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showPluginStore(fm: FragmentManager) {
        PluginStoreFragment().show(fm, "plugin_store")
    }
}

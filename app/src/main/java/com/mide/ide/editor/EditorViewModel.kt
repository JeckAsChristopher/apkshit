package com.mide.ide.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mide.ide.MIDEApplication
import com.mide.ide.compiler.BuildEngine
import com.mide.ide.compiler.BuildResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    val tabManager = TabManager()

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs

    private val _activeIndex = MutableStateFlow(-1)
    val activeIndex: StateFlow<Int> = _activeIndex

    private val _buildLog = MutableStateFlow("")
    val buildLog: StateFlow<String> = _buildLog

    private val _buildResult = MutableStateFlow<BuildResult?>(null)
    val buildResult: StateFlow<BuildResult?> = _buildResult

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding

    private val _fileOpenEvent = MutableSharedFlow<EditorTab>()
    val fileOpenEvent: SharedFlow<EditorTab> = _fileOpenEvent

    fun openFile(file: File) {
        tabManager.openFile(file)
        refreshTabs()
        viewModelScope.launch {
            tabManager.activeTab?.let { _fileOpenEvent.emit(it) }
        }
    }

    fun closeTab(index: Int) {
        tabManager.closeTab(index)
        refreshTabs()
    }

    fun setActiveTab(index: Int) {
        tabManager.setActive(index)
        refreshTabs()
        viewModelScope.launch {
            tabManager.activeTab?.let { _fileOpenEvent.emit(it) }
        }
    }

    fun onContentChanged(content: String) {
        val idx = tabManager.activeIndex
        if (idx >= 0) {
            tabManager.updateContent(idx, content)
            refreshTabs()
        }
    }

    fun saveCurrentFile(): Boolean {
        val idx = tabManager.activeIndex
        return if (idx >= 0) tabManager.saveFile(idx) else false
    }

    fun saveAllFiles() {
        tabManager.saveAll()
        refreshTabs()
    }

    fun startBuild(forceClean: Boolean = false) {
        val project = MIDEApplication.get().projectManager.currentProject ?: return
        if (_isBuilding.value) return

        tabManager.saveAll()

        viewModelScope.launch(Dispatchers.Default) {
            _isBuilding.value = true
            _buildLog.value = ""
            _buildResult.value = null

            val engine = BuildEngine(project)
            val result = engine.build(forceClean) { log ->
                _buildLog.value = _buildLog.value + log + "\n"
            }

            _buildResult.value = result
            _isBuilding.value = false
        }
    }

    private fun refreshTabs() {
        _tabs.value = tabManager.tabs
        _activeIndex.value = tabManager.activeIndex
    }
}

package com.mide.ide.editor

import java.io.File

class TabManager {

    private val _tabs = mutableListOf<EditorTab>()
    val tabs: List<EditorTab> get() = _tabs.toList()

    var activeIndex: Int = -1
        private set

    val activeTab: EditorTab? get() = _tabs.getOrNull(activeIndex)

    val maxTabs = 5

    fun openFile(file: File): Pair<Int, Boolean> {
        val existingIndex = _tabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        return if (existingIndex >= 0) {
            activeIndex = existingIndex
            Pair(existingIndex, false)
        } else {
            if (_tabs.size >= maxTabs) {
                val closeable = _tabs.indexOfFirst { !it.isModified && it != activeTab }
                if (closeable >= 0) {
                    _tabs.removeAt(closeable)
                    if (activeIndex >= closeable) activeIndex = maxOf(0, activeIndex - 1)
                }
            }
            val content = try { file.readText() } catch (e: Exception) { "" }
            val tab = EditorTab(file = file, content = content)
            _tabs.add(tab)
            activeIndex = _tabs.size - 1
            Pair(activeIndex, true)
        }
    }

    fun closeTab(index: Int) {
        if (index < 0 || index >= _tabs.size) return
        _tabs.removeAt(index)
        activeIndex = when {
            _tabs.isEmpty() -> -1
            activeIndex >= _tabs.size -> _tabs.size - 1
            else -> activeIndex
        }
    }

    fun setActive(index: Int) {
        if (index in _tabs.indices) activeIndex = index
    }

    fun saveCurrentTabState(scrollY: Int, cursorLine: Int, cursorColumn: Int) {
        activeTab?.apply {
            this.scrollY = scrollY
            this.cursorLine = cursorLine
            this.cursorColumn = cursorColumn
        }
    }

    fun updateContent(index: Int, content: String) {
        _tabs.getOrNull(index)?.apply {
            this.content = content
            this.isModified = true
        }
    }

    fun saveFile(index: Int): Boolean {
        val tab = _tabs.getOrNull(index) ?: return false
        return try {
            tab.file.writeText(tab.content ?: "")
            tab.isModified = false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun saveAll(): List<File> {
        return _tabs.indices
            .filter { _tabs[it].isModified }
            .mapNotNull { i -> if (saveFile(i)) _tabs[i].file else null }
    }

    fun hasUnsavedChanges(): Boolean = _tabs.any { it.isModified }
}

package com.mide.ide.editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.tabs.TabLayout
import com.mide.ide.utils.FileUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorManager(
    private val context: Context,
    private val container: FrameLayout,
    private val tabLayout: TabLayout
) {
    private val tabs = mutableListOf<EditorTab>()
    private val editors = mutableMapOf<String, CodeEditor>()
    private var activeTab: EditorTab? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val maxActiveTabs = 5

    companion object {
        private const val AUTOCOMPLETE_DEBOUNCE_MS = 300L
    }

    init {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val index = tab.position
                if (index < tabs.size) switchToTab(tabs[index])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    fun openFile(file: File) {
        val existing = tabs.find { it.file.absolutePath == file.absolutePath }
        if (existing != null) {
            switchToTab(existing)
            return
        }
        if (tabs.size >= maxActiveTabs) evictLeastRecentTab()
        scope.launch {
            val content = withContext(Dispatchers.IO) { file.readText() }
            val tab = EditorTab(file = file, content = content)
            tabs.add(tab)
            addTabToLayout(tab)
            switchToTab(tab)
        }
    }

    private fun addTabToLayout(tab: EditorTab) {
        val newTab = tabLayout.newTab().apply {
            text = tab.title
            setTag(tab.file.absolutePath)
        }
        tabLayout.addTab(newTab)
    }

    private fun switchToTab(tab: EditorTab) {
        activeTab?.let { current ->
            editors[current.file.absolutePath]?.let { editor ->
                current.cursorLine = editor.cursor.leftLine
                current.cursorColumn = editor.cursor.leftColumn
                current.content = editor.text.toString()
            }
        }
        activeTab = tab
        container.removeAllViews()
        val editor = getOrCreateEditor(tab)
        container.addView(editor)

        val tabIndex = tabs.indexOf(tab)
        if (tabLayout.selectedTabPosition != tabIndex) {
            tabLayout.getTabAt(tabIndex)?.select()
        }
    }

    private fun getOrCreateEditor(tab: EditorTab): CodeEditor {
        val key = tab.file.absolutePath
        return editors.getOrPut(key) {
            createEditor(tab)
        }
    }

    private fun createEditor(tab: EditorTab): CodeEditor {
        return CodeEditor(context).apply {
            colorScheme = SchemeDarcula()
            setEditorLanguage(JavaLanguage())
            isLineNumberEnabled = true
            isWordwrap = false
            tabWidth = 4

            tab.content?.let { setText(it) }

            post {
                setSelection(tab.cursorLine, tab.cursorColumn)
            }

            var debounceJob: Job? = null
            subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                val fileName = tab.file.name
                tab.isModified = true
                updateTabTitle(tab)
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(AUTOCOMPLETE_DEBOUNCE_MS)
                }
            }
        }
    }

    private fun updateTabTitle(tab: EditorTab) {
        val index = tabs.indexOf(tab)
        tabLayout.getTabAt(index)?.text = tab.title
    }

    fun saveCurrentFile() {
        val tab = activeTab ?: return
        val editor = editors[tab.file.absolutePath] ?: return
        scope.launch {
            val content = editor.text.toString()
            withContext(Dispatchers.IO) {
                tab.file.writeText(content)
            }
            tab.isModified = false
            tab.content = content
            updateTabTitle(tab)
        }
    }

    fun closeTab(tab: EditorTab) {
        val index = tabs.indexOf(tab)
        if (index == -1) return
        editors.remove(tab.file.absolutePath)
        tabs.remove(tab)
        tabLayout.removeTabAt(index)
        if (activeTab == tab) {
            activeTab = null
            if (tabs.isNotEmpty()) {
                switchToTab(tabs.minOf(tabs.size - 1, index).let { tabs[it] })
            } else {
                container.removeAllViews()
            }
        }
    }

    private fun evictLeastRecentTab() {
        val nonActive = tabs.filter { it != activeTab }
        if (nonActive.isNotEmpty()) {
            closeTab(nonActive.first())
        }
    }

    fun setDarkTheme(dark: Boolean) {
        editors.values.forEach { editor ->
            editor.colorScheme = if (dark) SchemeDarcula() else EditorColorScheme()
        }
    }

    fun setFontSize(sp: Float) {
        editors.values.forEach { it.textSizePx = sp * context.resources.displayMetrics.scaledDensity }
    }

    fun getCurrentContent(): String? {
        val tab = activeTab ?: return null
        return editors[tab.file.absolutePath]?.text?.toString()
    }

    fun getCurrentFile(): File? = activeTab?.file

    fun getOpenTabs(): List<EditorTab> = tabs.toList()
}

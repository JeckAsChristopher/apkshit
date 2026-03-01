package com.mide.ide.editor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mide.ide.MIDEApplication
import com.mide.ide.R
import com.mide.ide.compiler.BuildResult
import com.mide.ide.databinding.FragmentEditorBinding
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private val editorViewModel: EditorViewModel by activityViewModels()

    private val debounceDelay = 300L
    private var autoSaveJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupEditor()
        setupToolbar()
        observeViewModel()
    }

    private fun setupEditor() {
        binding.codeEditor.apply {
            colorScheme = SchemeDarcula()
            setEditorLanguage(JavaLanguage())
            isLineNumberEnabled = true
            typefaceText = android.graphics.Typeface.MONOSPACE
            textSizePx = 42f
            isWordwrap = false

            subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                val content = text.toString()
                editorViewModel.onContentChanged(content)

                // Debounced auto-save
                autoSaveJob?.cancel()
                autoSaveJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(debounceDelay * 5)
                    val prefs = MIDEApplication.getInstance().appPreferences
                    prefs.autoSave.collect { autoSave ->
                        if (autoSave) editorViewModel.saveCurrentFile()
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.btnBuild.setOnClickListener {
            editorViewModel.startBuild()
            binding.buildOutputPanel.isVisible = true
        }

        binding.btnCleanBuild.setOnClickListener {
            editorViewModel.startBuild(forceClean = true)
            binding.buildOutputPanel.isVisible = true
        }

        binding.btnSave.setOnClickListener {
            editorViewModel.saveCurrentFile()
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnInstallApk.setOnClickListener {
            installLatestApk()
        }

        binding.btnCloseBuildOutput.setOnClickListener {
            binding.buildOutputPanel.isVisible = false
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            editorViewModel.fileOpenEvent.collect { tab ->
                loadTabContent(tab)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            editorViewModel.tabs.collect { tabs ->
                rebuildTabBar(tabs)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            editorViewModel.buildLog.collect { log ->
                binding.textBuildOutput.text = log
                binding.scrollBuildOutput.post {
                    binding.scrollBuildOutput.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            editorViewModel.isBuilding.collect { building ->
                binding.progressBuild.isVisible = building
                binding.btnBuild.isEnabled = !building
                binding.btnCleanBuild.isEnabled = !building
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            editorViewModel.buildResult.collect { result ->
                result ?: return@collect
                when (result) {
                    is BuildResult.Success -> {
                        binding.btnInstallApk.isVisible = true
                        showBuildStatus("Build successful in ${result.durationMs}ms", success = true)
                    }
                    is BuildResult.Failure -> {
                        binding.btnInstallApk.isVisible = false
                        showBuildStatus("Build failed: ${result.errors.size} error(s)", success = false)
                        highlightErrors(result.errors)
                    }
                }
            }
        }
    }

    private fun loadTabContent(tab: EditorTab) {
        binding.codeEditor.setText(tab.content)
        binding.codeEditor.setEditorLanguage(
            if (tab.file.extension == "kt") JavaLanguage() else JavaLanguage()
        )
        // Restore scroll position
        binding.codeEditor.scrollTo(0, tab.scrollY)
    }

    private fun rebuildTabBar(tabs: List<EditorTab>) {
        binding.tabContainer.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val tabView = LayoutInflater.from(context)
                .inflate(R.layout.item_editor_tab, binding.tabContainer, false)

            val textName = tabView.findViewById<TextView>(R.id.textTabName)
            val btnClose = tabView.findViewById<View>(R.id.btnCloseTab)

            textName.text = if (tab.isModified) "• ${tab.file.name}" else tab.file.name
            val isActive = index == editorViewModel.activeIndex.value
            tabView.setBackgroundColor(if (isActive) Color.parseColor("#2B2B2B") else Color.parseColor("#1E1E1E"))

            tabView.setOnClickListener { editorViewModel.setActiveTab(index) }
            btnClose.setOnClickListener { editorViewModel.closeTab(index) }

            binding.tabContainer.addView(tabView)
        }
    }

    private fun showBuildStatus(message: String, success: Boolean) {
        binding.textBuildStatus.text = message
        binding.textBuildStatus.setTextColor(
            if (success) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        )
        binding.textBuildStatus.isVisible = true
    }

    private fun highlightErrors(errors: List<BuildResult.BuildError>) {
        // Jump to first error if it's in the current file
        val currentFile = editorViewModel.tabManager.activeTab?.file ?: return
        val firstError = errors.firstOrNull {
            it.severity == BuildResult.BuildError.Severity.ERROR &&
                    it.file == currentFile.absolutePath
        }
        if (firstError != null && firstError.line > 0) {
            binding.codeEditor.jumpToLine(firstError.line - 1)
        }
    }

    private fun installLatestApk() {
        val project = MIDEApplication.getInstance().projectManager.currentProject ?: return
        val apkFile = project.apkFile
        if (!apkFile.exists()) {
            Toast.makeText(context, "No APK found. Build first.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

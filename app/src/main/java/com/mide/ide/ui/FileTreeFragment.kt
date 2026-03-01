package com.mide.ide.ui

import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mide.ide.MainActivity
import com.mide.ide.MIDEApplication
import com.mide.ide.project.FileNode
import com.mide.ide.project.MIDEProject
import com.mide.ide.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileTreeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileTreeAdapter
    private var currentProject: MIDEProject? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        recyclerView = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        adapter = FileTreeAdapter { node ->
            if (node.isDirectory) {
                toggleDirectory(node)
            } else {
                openFile(node.file)
            }
        }

        recyclerView.adapter = adapter
        layout.addView(recyclerView)
        return layout
    }

    fun setProject(project: MIDEProject) {
        currentProject = project
        loadFileTree(project.rootDir)
    }

    private fun loadFileTree(rootDir: File) {
        lifecycleScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                buildFileTree(rootDir, 0)
            }
            adapter.submitList(nodes)
        }
    }

    private fun buildFileTree(dir: File, depth: Int): List<FileNode> {
        val nodes = mutableListOf<FileNode>()
        val entries = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return nodes

        entries.forEach { file ->
            val node = FileNode(file, depth)
            nodes.add(node)
        }
        return nodes
    }

    private fun toggleDirectory(node: FileNode) {
        node.isExpanded = !node.isExpanded
        lifecycleScope.launch {
            val currentList = adapter.currentList.toMutableList()
            val nodeIndex = currentList.indexOf(node)
            if (node.isExpanded) {
                val children = withContext(Dispatchers.IO) {
                    buildFileTree(node.file, node.depth + 1)
                }
                currentList.addAll(nodeIndex + 1, children)
            } else {
                val toRemove = currentList.drop(nodeIndex + 1).takeWhile { it.depth > node.depth }
                currentList.removeAll(toRemove.toSet())
            }
            adapter.submitList(currentList)
        }
    }

    private fun openFile(file: File) {
        if (!FileUtils.isTextFile(file)) {
            Toast.makeText(requireContext(), "Cannot open binary file", Toast.LENGTH_SHORT).show()
            return
        }
        (activity as? MainActivity)?.editorManager?.openFile(file)
    }

    fun showNewFileDialog(parentDir: File) {
        val input = EditText(requireContext()).apply { hint = "File name" }
        AlertDialog.Builder(requireContext())
            .setTitle("New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newFile = File(parentDir, name)
                    if (!newFile.exists()) {
                        newFile.createNewFile()
                        currentProject?.let { loadFileTree(it.rootDir) }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showDeleteDialog(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${file.name}?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (deleted) {
                    currentProject?.let { loadFileTree(it.rootDir) }
                } else {
                    Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

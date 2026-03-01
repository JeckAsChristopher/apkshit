package com.mide.ide.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.mide.ide.MIDEApplication
import com.mide.ide.downloader.ToolDownloadManager
import kotlinx.coroutines.launch

class ToolsStatusDialog : DialogFragment() {

    private val statusItems = mutableMapOf<String, TextView>()
    private lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        progressBar = ProgressBar(ctx)
        layout.addView(progressBar)

        val tools = listOf("d8", "aapt2", "android.jar")
        tools.forEach { toolName ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(ctx).apply {
                text = toolName
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val status = TextView(ctx).apply { text = "Checking..." }
            statusItems[toolName] = status
            row.addView(label)
            row.addView(status)
            layout.addView(row)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Build Tools Status")
            .setView(layout)
            .setPositiveButton("Download Missing") { _, _ -> downloadTools() }
            .setNegativeButton("Close", null)
            .create()

        checkTools()
        return dialog
    }

    private fun checkTools() {
        val mgr = MIDEApplication.get().toolDownloadManager
        val status = mgr.getToolStatus()
        status.forEach { (name, exists) ->
            statusItems[name]?.text = if (exists) "✓ Ready" else "✗ Missing"
            statusItems[name]?.setTextColor(if (exists) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        }
        progressBar.visibility = android.view.View.GONE
    }

    private fun downloadTools() {
        val mgr = MIDEApplication.get().toolDownloadManager
        lifecycleScope.launch {
            progressBar.visibility = android.view.View.VISIBLE
            mgr.checkAndDownloadRequiredTools { status ->
                activity?.runOnUiThread {
                    statusItems[status.toolName]?.text = when (status.status) {
                        ToolDownloadManager.Status.DOWNLOADING -> "${status.progress}%"
                        ToolDownloadManager.Status.DONE -> "✓ Ready"
                        ToolDownloadManager.Status.FAILED -> "✗ Failed: ${status.error}"
                        ToolDownloadManager.Status.ALREADY_EXISTS -> "✓ Ready"
                        else -> "Pending"
                    }
                }
            }
            activity?.runOnUiThread { progressBar.visibility = android.view.View.GONE }
        }
    }
}

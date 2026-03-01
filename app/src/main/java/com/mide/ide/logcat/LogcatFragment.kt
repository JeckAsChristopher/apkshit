package com.mide.ide.logcat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var logContainer: LinearLayout
    private var logProcess: Process? = null
    private var readJob: Job? = null
    private var filterPackage: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        scrollView = ScrollView(ctx)
        logContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scrollView.addView(logContainer)
        startLogcat()
        return scrollView
    }

    fun setPackageFilter(packageName: String) {
        filterPackage = packageName
        restartLogcat()
    }

    private fun startLogcat() {
        readJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cmd = if (filterPackage != null) {
                    arrayOf("logcat", "-v", "time", "--pid", getPidForPackage(filterPackage!!).toString())
                } else {
                    arrayOf("logcat", "-v", "time")
                }

                logProcess = Runtime.getRuntime().exec(cmd)
                logProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var line = reader.readLine()
                    while (isActive && line != null) {
                        val logLine = line
                        withContext(Dispatchers.Main) {
                            addLogLine(logLine)
                        }
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLogLine("Logcat error: ${e.message}")
                }
            }
        }
    }

    private fun addLogLine(line: String) {
        if (!isAdded) return
        val ctx = requireContext() ?: return
        val color = when {
            line.contains(" E ") || line.contains("/E:") -> 0xFFFF4444.toInt()
            line.contains(" W ") || line.contains("/W:") -> 0xFFFFAA00.toInt()
            line.contains(" I ") || line.contains("/I:") -> 0xFF44AAFF.toInt()
            line.contains(" D ") || line.contains("/D:") -> 0xFFAAAAAA.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

        val tv = TextView(ctx).apply {
            text = line
            setTextColor(color)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 1, 0, 1)
        }
        logContainer.addView(tv)

        // Keep max 2000 lines to prevent OOM
        if (logContainer.childCount > 2000) {
            logContainer.removeViewAt(0)
        }

        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun getPidForPackage(packageName: String): Int {
        return try {
            val process = Runtime.getRuntime().exec("pidof $packageName")
            val pid = process.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull() ?: -1
            process.destroy()
            pid
        } catch (e: Exception) { -1 }
    }

    fun clearLog() {
        logContainer.removeAllViews()
    }

    private fun restartLogcat() {
        readJob?.cancel()
        logProcess?.destroy()
        clearLog()
        startLogcat()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        readJob?.cancel()
        logProcess?.destroy()
    }
}

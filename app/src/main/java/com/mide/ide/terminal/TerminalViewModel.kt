package com.mide.ide.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mide.ide.MIDEApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var shellProcess: Process? = null
    private var shellWriter: PrintWriter? = null

    private val outputBuilder = StringBuilder()
    private val maxOutputLines = 2000

    init {
        startShell()
    }

    private fun startShell() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workDir = MIDEApplication.get().projectManager.currentProject?.rootDir
                    ?: MIDEApplication.get().getExternalFilesDir(null)
                    ?: File(MIDEApplication.get().filesDir, "projects")

                val shell = listOf("/bin/sh", "/system/bin/sh")
                    .firstOrNull { File(it).exists() } ?: "/system/bin/sh"

                shellProcess = ProcessBuilder(shell)
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .start()

                shellWriter = PrintWriter(shellProcess!!.outputStream.bufferedWriter(), true)
                _isRunning.value = true

                appendOutput("MIDE Terminal\nType 'exit' to close shell.\n$ ")

                viewModelScope.launch(Dispatchers.IO) {
                    val reader = shellProcess!!.inputStream.bufferedReader()
                    try {
                        val buffer = CharArray(1024)
                        while (isActive && shellProcess?.isAlive == true) {
                            if (reader.ready()) {
                                val count = reader.read(buffer)
                                if (count > 0) {
                                    appendOutput(String(buffer, 0, count))
                                }
                            } else {
                                kotlinx.coroutines.delay(50)
                            }
                        }
                    } catch (e: Exception) {
                        appendOutput("\n[Shell exited]\n")
                    }
                    _isRunning.value = false
                }
            } catch (e: Exception) {
                appendOutput("Failed to start shell: ${e.message}\n")
                _isRunning.value = false
            }
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            appendOutput("$command\n")
            shellWriter?.println(command)
        }
    }

    fun sendInput(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            shellProcess?.outputStream?.write(text.toByteArray())
            shellProcess?.outputStream?.flush()
        }
    }

    private suspend fun appendOutput(text: String) = withContext(Dispatchers.Main) {
        outputBuilder.append(text)

        val lineCount = outputBuilder.count { it == '\n' }
        if (lineCount > maxOutputLines) {
            val lines = outputBuilder.toString().lines()
            val trimmed = lines.takeLast(maxOutputLines)
            outputBuilder.clear()
            outputBuilder.append(trimmed.joinToString("\n"))
        }

        _output.value = outputBuilder.toString()
    }

    fun clearOutput() {
        outputBuilder.clear()
        _output.value = ""
    }

    fun restartShell() {
        shellProcess?.destroyForcibly()
        shellWriter?.close()
        outputBuilder.clear()
        _output.value = ""
        startShell()
    }

    override fun onCleared() {
        shellProcess?.destroyForcibly()
        shellWriter?.close()
        super.onCleared()
    }
}

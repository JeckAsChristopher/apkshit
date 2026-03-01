package com.mide.ide.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalFragment : Fragment() {

    private lateinit var terminalView: TerminalView
    private lateinit var terminalSession: TerminalSession

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        terminalView = TerminalView(ctx, null)
        terminalView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.onScreenUpdated()
            }
            override fun onTitleChanged(updatedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val clipboard = ctx.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
            }
            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                val clipboard = ctx.getSystemService(android.content.ClipboardManager::class.java)
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
                session?.write(text)
            }
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        }

        val workingDir = requireContext().filesDir.absolutePath
        val shell = getShellPath()

        terminalSession = TerminalSession(
            shell,
            workingDir,
            arrayOf(),
            getEnvironmentVariables(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            sessionClient
        )

        val viewClient = object : TerminalViewClient {
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
            override fun onScale(scale: Float): Boolean = false
            override fun onSingleTapUp(e: android.view.MotionEvent?) {}
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = true
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?): Boolean = false
            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = false
            override fun onLongPress(event: android.view.MotionEvent?): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
            override fun onEmulatorSet() {}
            override fun onBell() {}
        }

        terminalView.setTerminalViewClient(viewClient)
        terminalView.attachSession(terminalSession)

        return terminalView
    }

    private fun getShellPath(): String {
        val shells = listOf("/bin/sh", "/system/bin/sh", "/bin/bash", "/system/bin/bash")
        return shells.firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }

    private fun getEnvironmentVariables(): Array<String> {
        val ctx = requireContext()
        return arrayOf(
            "TERM=xterm-256color",
            "HOME=${ctx.filesDir.absolutePath}",
            "PATH=/system/bin:/system/xbin",
            "TMPDIR=${ctx.cacheDir.absolutePath}",
            "ANDROID_DATA=${ctx.filesDir.absolutePath}",
            "ANDROID_ROOT=/system"
        )
    }

    fun sendInput(text: String) {
        if (::terminalSession.isInitialized) {
            terminalSession.write(text)
        }
    }

    fun sendCommand(command: String) {
        sendInput("${"$"}command
")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::terminalSession.isInitialized) {
            terminalSession.finishIfRunning()
        }
    }
}

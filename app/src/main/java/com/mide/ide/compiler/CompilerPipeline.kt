package com.mide.ide.compiler

import android.content.Context
import com.mide.ide.project.MIDEProject

interface CompilerPipeline {
    suspend fun compile(
        project: MIDEProject,
        context: Context,
        onProgress: (step: String, progress: Int, message: String) -> Unit
    ): BuildResult
}

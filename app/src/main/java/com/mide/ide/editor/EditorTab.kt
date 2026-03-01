package com.mide.ide.editor

import java.io.File

data class EditorTab(
    val file: File,
    var isModified: Boolean = false,
    var cursorLine: Int = 0,
    var cursorColumn: Int = 0,
    var scrollY: Int = 0,
    var content: String? = null
) {
    val title: String get() = if (isModified) "${file.name}*" else file.name
}

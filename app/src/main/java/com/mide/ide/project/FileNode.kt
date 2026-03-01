package com.mide.ide.project

import java.io.File

data class FileNode(
    val file: File,
    val depth: Int,
    var isExpanded: Boolean = false,
    var isSelected: Boolean = false
) {
    val name: String get() = file.name
    val isDirectory: Boolean get() = file.isDirectory
    val extension: String get() = file.extension.lowercase()

    val iconRes: Int get() = when {
        isDirectory -> android.R.drawable.ic_menu_more
        extension == "kt" -> android.R.drawable.ic_menu_edit
        extension == "java" -> android.R.drawable.ic_menu_edit
        extension == "xml" -> android.R.drawable.ic_menu_info_details
        extension == "gradle" -> android.R.drawable.ic_menu_preferences
        extension == "json" -> android.R.drawable.ic_menu_info_details
        else -> android.R.drawable.ic_menu_agenda
    }
}

package com.mide.ide.plugin

enum class PluginPermission(val displayName: String, val description: String) {
    READ_FILES("Read Files", "Can read project files"),
    WRITE_FILES("Write Files", "Can create and modify project files"),
    NETWORK("Network Access", "Can make network requests"),
    EDITOR_INJECT("Editor Injection", "Can inject code into the editor"),
    ADD_MENU_ITEM("Add Menu Items", "Can add items to MIDE menus"),
    SHOW_NOTIFICATIONS("Show Notifications", "Can show notifications"),
    RUN_COMMANDS("Run Commands", "Can execute shell commands"),
    ACCESS_BUILD_OUTPUT("Build Output", "Can read build logs and results"),
    MODIFY_BUILD_PIPELINE("Build Pipeline", "Can hook into the build process")
}

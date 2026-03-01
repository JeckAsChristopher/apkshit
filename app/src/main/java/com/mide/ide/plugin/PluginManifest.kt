package com.mide.ide.plugin

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val minMideVersion: String = "1.0.0",
    val permissions: List<PluginPermission> = emptyList(),
    val entryPoint: String = "index.js",
    val iconPath: String? = null
)

package com.mide.ide.plugin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.mide.ide.MIDEApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PluginStoreFragment : DialogFragment() {

    data class StorePlugin(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val author: String,
        val downloadUrl: String,
        val size: Long = 0
    )

    private val storeApiUrl = "https://mide-plugins.example.com/api/v1/plugins"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        statusText = TextView(ctx).apply {
            text = "Loading plugins..."
            textSize = 16f
        }
        progressBar = ProgressBar(ctx)
        this.container = layout

        layout.addView(TextView(ctx).apply {
            text = "Plugin Store"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })
        layout.addView(progressBar)
        layout.addView(statusText)

        scroll.addView(layout)

        lifecycleScope.launch { loadPlugins() }
        return scroll
    }

    private suspend fun loadPlugins() {
        val installedPlugins = getInstalledPluginIds()
        val plugins = fetchStorePlugins()
        withContext(Dispatchers.Main) {
            progressBar.visibility = View.GONE
            if (plugins.isEmpty()) {
                statusText.text = "No plugins available or network unavailable."
                return@withContext
            }
            statusText.visibility = View.GONE
            plugins.forEach { plugin -> addPluginCard(plugin, installedPlugins.contains(plugin.id)) }
        }
    }

    private suspend fun fetchStorePlugins(): List<StorePlugin> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder().url(storeApiUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val json = response.body?.string() ?: return@withContext emptyList()
            gson.fromJson(json, Array<StorePlugin>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getInstalledPluginIds(): Set<String> {
        val pm = PluginManager(requireContext())
        return pm.getInstalledPlugins().map { it.manifest.id }.toSet()
    }

    private fun addPluginCard(plugin: StorePlugin, isInstalled: Boolean) {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        card.addView(TextView(ctx).apply {
            text = "${plugin.name} v${plugin.version}"
            textSize = 16f
        })
        card.addView(TextView(ctx).apply {
            text = "by ${plugin.author}"
            textSize = 12f
        })
        card.addView(TextView(ctx).apply {
            text = plugin.description
            setPadding(0, 8, 0, 8)
        })
        val btn = Button(ctx).apply {
            text = if (isInstalled) "Installed" else "Install"
            isEnabled = !isInstalled
            setOnClickListener { installPlugin(plugin) }
        }
        card.addView(btn)
        container.addView(card)

        val spacer = View(ctx)
        spacer.minimumHeight = 16
        container.addView(spacer)
    }

    private fun installPlugin(plugin: StorePlugin) {
        val ctx = requireContext()
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, "Downloading ${plugin.name}...", Toast.LENGTH_SHORT).show()
            }
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(plugin.downloadUrl).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) return@withContext false
                    val tempFile = java.io.File(ctx.cacheDir, "${plugin.id}.zip")
                    response.body?.byteStream()?.use { it.copyTo(tempFile.outputStream()) }
                    val pm = PluginManager(ctx)
                    val allowUnverified = MIDEApplication.get().preferencesManager.allowUnverifiedPlugins.first()
                    val installResult = pm.installPlugin(tempFile, allowUnverified)
                    tempFile.delete()
                    installResult.success
                } catch (e: Exception) {
                    false
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    ctx,
                    if (result) "${plugin.name} installed!" else "Failed to install ${plugin.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}

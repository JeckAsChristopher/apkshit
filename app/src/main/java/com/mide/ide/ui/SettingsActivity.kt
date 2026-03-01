package com.mide.ide.ui

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mide.ide.MIDEApplication
import com.mide.ide.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val prefs get() = MIDEApplication.get().preferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        layout.addView(sectionHeader("Editor"))
        layout.addView(seekBarSetting("Font Size", 8, 32) { prefs.fontSize } { prefs.setFontSize(it.toFloat()) })
        layout.addView(seekBarSetting("Tab Width", 2, 8) { prefs.tabSize } { prefs.setTabSize(it) })
        layout.addView(switchSetting("Word Wrap", prefs.wordWrap) { prefs.setWordWrap(it) })
        layout.addView(switchSetting("Show Line Numbers", prefs.showLineNumbers) { prefs.setShowLineNumbers(it) })
        layout.addView(switchSetting("Auto Save", prefs.autoSave) { prefs.setAutoSave(it) })
        layout.addView(switchSetting("Dark Theme", prefs.darkTheme) { prefs.setDarkTheme(it) })

        layout.addView(sectionHeader("Build"))
        layout.addView(switchSetting("Allow Unverified Plugins", prefs.allowUnverifiedPlugins) {
            prefs.setAllowUnverifiedPlugins(it)
        })

        scroll.addView(layout)
        setContentView(scroll)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun sectionHeader(title: String): View = TextView(this).apply {
        text = title
        textSize = 18f
        setPadding(0, 32, 0, 16)
    }

    private fun switchSetting(
        label: String,
        valueFlow: kotlinx.coroutines.flow.Flow<Boolean>,
        onChanged: suspend (Boolean) -> Unit
    ): View {
        val ctx = this
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val tv = TextView(ctx).apply {
            text = label
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(ctx)
        row.addView(tv)
        row.addView(sw)

        lifecycleScope.launch {
            sw.isChecked = valueFlow.first()
        }
        sw.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { onChanged(checked) }
        }
        return row
    }

    private fun seekBarSetting(
        label: String,
        min: Int,
        max: Int,
        valueFlow: () -> kotlinx.coroutines.flow.Flow<Number>,
        onChanged: suspend (Int) -> Unit
    ): View {
        val ctx = this
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val headerRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val tv = TextView(ctx).apply {
            text = label
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueLabel = TextView(ctx).apply { text = min.toString() }
        headerRow.addView(tv)
        headerRow.addView(valueLabel)

        val seekBar = SeekBar(ctx).apply {
            this.max = max - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val actual = progress + min
                    valueLabel.text = actual.toString()
                    if (fromUser) lifecycleScope.launch { onChanged(actual) }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        lifecycleScope.launch {
            val v = valueFlow().first().toInt()
            seekBar.progress = v - min
            valueLabel.text = v.toString()
        }

        col.addView(headerRow)
        col.addView(seekBar)
        return col
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

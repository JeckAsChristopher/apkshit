package com.mide.ide.ui

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mide.ide.MainViewModel

class BuildOutputFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var outputView: TextView
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1E1E.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val header = TextView(ctx).apply {
            text = "BUILD OUTPUT"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(16, 12, 16, 12)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        layout.addView(header)

        outputView = TextView(ctx).apply {
            text = ""
            textSize = 11f
            setTextColor(0xFFDDDDDD.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 0, 16, 16)
            setTextIsSelectable(true)
        }

        scrollView = ScrollView(ctx).apply {
            addView(outputView)
        }
        layout.addView(scrollView)

        observeViewModel()
        return layout
    }

    private fun observeViewModel() {
        viewModel.buildOutput.observe(viewLifecycleOwner) { lines ->
            val sb = StringBuilder()
            val spannable = SpannableString(lines.joinToString("
"))
            var offset = 0
            lines.forEach { line ->
                val color = when {
                    line.startsWith("[ERROR]") || line.contains(" ERROR ") -> 0xFFFF6B6B.toInt()
                    line.startsWith("[WARN]") || line.contains(" WARNING ") -> 0xFFFFD93D.toInt()
                    line.startsWith("[DONE]") || line.contains("successful") -> 0xFF6BCB77.toInt()
                    line.startsWith("[DEX]") -> 0xFF4ECDC4.toInt()
                    line.startsWith("[SIGN]") -> 0xFFA8DADC.toInt()
                    line.startsWith("[PACKAGE]") -> 0xFFFFA07A.toInt()
                    else -> 0xFFDDDDDD.toInt()
                }
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    offset,
                    (offset + line.length).coerceAtMost(spannable.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                offset += line.length + 1
            }
            outputView.text = spannable
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}

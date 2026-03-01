package com.mide.ide.terminal

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

object AnsiParser {

    private val ANSI_ESCAPE_REGEX = Regex("\u001B\\[(\\d+(?:;\\d+)*)m")

    fun parse(text: String): CharSequence {
        val builder = SpannableStringBuilder()
        var currentColor: Int? = null
        var lastEnd = 0

        ANSI_ESCAPE_REGEX.findAll(text).forEach { match ->
            val plainText = text.substring(lastEnd, match.range.first)
            if (plainText.isNotEmpty()) {
                val start = builder.length
                builder.append(plainText)
                currentColor?.let { color ->
                    builder.setSpan(
                        ForegroundColorSpan(color),
                        start,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            currentColor = parseAnsiCodes(codes)
            lastEnd = match.range.last + 1
        }

        // Remaining text after last escape code
        if (lastEnd < text.length) {
            val remaining = text.substring(lastEnd)
            val start = builder.length
            builder.append(remaining)
            currentColor?.let { color ->
                builder.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return builder
    }

    private fun parseAnsiCodes(codes: List<Int>): Int? {
        if (codes.isEmpty() || codes.contains(0)) return null
        return when (codes.firstOrNull()) {
            30 -> Color.parseColor("#000000")
            31 -> Color.parseColor("#FF5555")
            32 -> Color.parseColor("#50FA7B")
            33 -> Color.parseColor("#F1FA8C")
            34 -> Color.parseColor("#BD93F9")
            35 -> Color.parseColor("#FF79C6")
            36 -> Color.parseColor("#8BE9FD")
            37 -> Color.parseColor("#F8F8F2")
            90 -> Color.parseColor("#6272A4")
            91 -> Color.parseColor("#FF6E6E")
            92 -> Color.parseColor("#69FF94")
            93 -> Color.parseColor("#FFFFA5")
            94 -> Color.parseColor("#D6ACFF")
            95 -> Color.parseColor("#FF92DF")
            96 -> Color.parseColor("#A4FFFF")
            97 -> Color.parseColor("#FFFFFF")
            else -> null
        }
    }

    fun stripAnsi(text: String): String = ANSI_ESCAPE_REGEX.replace(text, "")
}

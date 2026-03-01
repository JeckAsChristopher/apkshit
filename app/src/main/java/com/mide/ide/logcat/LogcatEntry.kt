package com.mide.ide.logcat

import android.graphics.Color

data class LogcatEntry(
    val timestamp: String,
    val pid: String,
    val level: Level,
    val tag: String,
    val message: String
) {
    enum class Level(val char: Char, val color: Int) {
        VERBOSE('V', Color.parseColor("#AAAAAA")),
        DEBUG('D', Color.parseColor("#8BE9FD")),
        INFO('I', Color.parseColor("#50FA7B")),
        WARNING('W', Color.parseColor("#FFB86C")),
        ERROR('E', Color.parseColor("#FF5555")),
        ASSERT('A', Color.parseColor("#FF5555"));

        companion object {
            fun fromChar(c: Char): Level = values().firstOrNull { it.char == c } ?: DEBUG
        }
    }

    companion object {
        private val LOGCAT_REGEX = Regex(
            """(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+(\d+)\s+\d+\s+([VDIWEA])\s+([^:]+):\s+(.*)"""
        )

        fun parse(line: String): LogcatEntry? {
            val match = LOGCAT_REGEX.find(line) ?: return null
            val (ts, pid, level, tag, msg) = match.destructured
            return LogcatEntry(ts, pid, Level.fromChar(level[0]), tag.trim(), msg)
        }
    }
}

package com.example.shadowplayer.player

import java.util.regex.Pattern

object SrtParser {
    // 匹配时间轴格式: 00:00:20,000 --> 00:00:24,400
    private val TIME_PATTERN = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

    fun parse(content: String): List<LrcSentence> {
        val sentences = ArrayList<LrcSentence>()
        // 统一换行符并按空行分割成块
        val blocks = content.replace("\r\n", "\n").replace("\r", "\n").split("\n\n")

        var indexCounter = 0 // 用于生成 LrcSentence 的 index

        for (block in blocks) {
            val lines = block.trim().split("\n")
            if (lines.size >= 2) {
                // 查找包含时间轴的那一行
                val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
                if (timeLineIndex != -1 && timeLineIndex + 1 < lines.size) {
                    val timeLine = lines[timeLineIndex]
                    val times = timeLine.split("-->")
                    if (times.size == 2) {
                        val startTime = parseTime(times[0].trim())
                        val endTime = parseTime(times[1].trim())

                        // 剩下的行拼接为字幕文本
                        val text = lines.subList(timeLineIndex + 1, lines.size)
                            .joinToString(" ") { it.trim() }

                        if (startTime >= 0 && endTime > startTime && text.isNotEmpty()) {
                            // --- 修复点：这里传入了 index 参数 ---
                            sentences.add(LrcSentence(
                                index = indexCounter++,
                                startTime = startTime,
                                endTime = endTime,
                                text = text
                            ))
                        }
                    }
                }
            }
        }
        return sentences
    }

    private fun parseTime(timeStr: String): Long {
        val matcher = TIME_PATTERN.matcher(timeStr)
        if (matcher.find()) {
            val hours = matcher.group(1)?.toLong() ?: 0
            val minutes = matcher.group(2)?.toLong() ?: 0
            val seconds = matcher.group(3)?.toLong() ?: 0
            val millis = matcher.group(4)?.toLong() ?: 0
            return (hours * 3600000) + (minutes * 60000) + (seconds * 1000) + millis
        }
        return -1
    }
}
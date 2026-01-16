package com.example.shadowplayer.player

/**
 * LRC 字幕句子
 */
data class LrcSentence(
    val index: Int,
    val startTime: Long,  // 毫秒
    val endTime: Long,    // 毫秒
    val text: String
)

/**
 * LRC 字幕解析器
 */
object LrcParser {
    // [修改] 匹配时间标签: 允许分钟数超过2位 (\d{2,})
    private val timeTagRegex = Regex("""\[(\d{2,}):(\d{2})[.:](\d{2,3})]""")

    /**
     * 解析 LRC 文件内容
     * @param content LRC 文件内容
     * @param totalDuration 音频总时长(毫秒), 用于计算最后一句的结束时间
     * @param offset 时间偏移(毫秒), 正数表示字幕提前, 负数表示字幕延后
     */
    fun parse(content: String, totalDuration: Long, offset: Long = 0): List<LrcSentence> {
        val lines = content.lines()
        val rawSentences = mutableListOf<Pair<Long, String>>()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // 跳过元数据标签 [ti:], [ar:], [al:] 等
            if (trimmedLine.matches(Regex("""\[[a-zA-Z]+:.*]"""))) continue

            // 提取所有时间标签
            val matches = timeTagRegex.findAll(trimmedLine)
            val times = matches.map { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millisPart = match.groupValues[3]
                // 处理 2 位或 3 位毫秒
                val millis = if (millisPart.length == 2) {
                    millisPart.toLong() * 10
                } else {
                    millisPart.toLong()
                }
                minutes * 60 * 1000 + seconds * 1000 + millis
            }.toList()

            // 提取歌词文本（移除所有时间标签）
            val text = timeTagRegex.replace(trimmedLine, "").trim()

            // 为每个时间标签添加句子
            for (time in times) {
                if (text.isNotEmpty()) {
                    rawSentences.add(Pair(time + offset, text))
                }
            }
        }

        // 按时间排序
        val sortedSentences = rawSentences.sortedBy { it.first }

        // 构建 LrcSentence 列表，计算结束时间
        return sortedSentences.mapIndexed { index, (startTime, text) ->
            val endTime = if (index < sortedSentences.size - 1) {
                sortedSentences[index + 1].first
            } else {
                totalDuration
            }
            LrcSentence(
                index = index,
                startTime = maxOf(0, startTime),  // 确保不为负数
                endTime = maxOf(startTime, endTime),
                text = text
            )
        }
    }

    /**
     * 根据当前播放位置查找对应的句子索引
     */
    fun findSentenceIndex(sentences: List<LrcSentence>, position: Long): Int {
        if (sentences.isEmpty()) return -1

        for (i in sentences.indices.reversed()) {
            if (position >= sentences[i].startTime) {
                return i
            }
        }
        return 0
    }
}
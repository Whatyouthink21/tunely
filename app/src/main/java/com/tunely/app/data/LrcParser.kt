package com.tunely.app.data

/**
 * Parses standard LRC ("[mm:ss.xx] text") and enhanced LRC with
 * per-word tags ("<mm:ss.xx> word"). Word timing is used when present;
 * otherwise the UI interpolates highlighting across each line.
 */
object LrcParser {
    private val lineTag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
    private val wordTag = Regex("""<(\d+):(\d+)(?:[.:](\d+))?>""")

    fun parse(raw: String, source: String): Lyrics {
        val entries = mutableListOf<Pair<Long, String>>()
        for (line in raw.lines()) {
            val m = lineTag.find(line) ?: continue
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            entries += start to line.substring(m.range.last + 1).trim()
        }
        entries.sortBy { it.first }

        var anyWords = false
        val lines = entries.mapIndexed { i, (start, text) ->
            val end = entries.getOrNull(i + 1)?.first ?: (start + 5000)
            val words = parseWords(text, end)
            if (words.isNotEmpty()) anyWords = true
            LyricLine(start, end, wordTag.replace(text, "").trim(), words)
        }
        return Lyrics(lines, synced = true, wordSynced = anyWords, source = source)
    }

    private fun parseWords(text: String, lineEnd: Long): List<LyricWord> {
        val tags = wordTag.findAll(text).toList()
        if (tags.isEmpty()) return emptyList()
        return tags.mapIndexed { i, t ->
            val start = toMs(t.groupValues[1], t.groupValues[2], t.groupValues[3])
            val end = tags.getOrNull(i + 1)?.let {
                toMs(it.groupValues[1], it.groupValues[2], it.groupValues[3])
            } ?: lineEnd
            val from = t.range.last + 1
            val to = tags.getOrNull(i + 1)?.range?.first ?: text.length
            LyricWord(start, end, text.substring(from, to).trim())
        }.filter { it.text.isNotEmpty() }
    }

    private fun toMs(min: String, sec: String, frac: String): Long {
        val f = if (frac.isEmpty()) 0L else (frac.padEnd(3, '0').take(3)).toLong()
        return min.toLong() * 60_000 + sec.toLong() * 1000 + f
    }
}

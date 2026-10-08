package com.tunely.app.data

/**
 * Parses standard LRC ("[mm:ss.xx] text") and enhanced LRC with
 * per-word tags ("<mm:ss.xx> word"). Word timing is used when present;
 * otherwise the UI interpolates highlighting across each line.
 *
 * Handles the things that used to break lyrics in the wild:
 *  * several timestamps on one line, e.g. "[00:12.00][00:45.00] chorus"
 *  * the "[offset:+250]" header
 *  * "[ar:]", "[ti:]", "[by:]" metadata lines
 */
object LrcParser {
    private val lineTag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val wordTag = Regex("""<(\d+):(\d+)(?:[.:](\d+))?>""")

    fun parse(raw: String, source: String): Lyrics {
        val offset = offsetTag.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val entries = mutableListOf<Pair<Long, String>>()

        for (line in raw.lines()) {
            val tags = lineTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (tag in tags) {
                val start = toMs(tag.groupValues[1], tag.groupValues[2], tag.groupValues[3]) - offset
                entries += start.coerceAtLeast(0L) to text
            }
        }
        if (entries.isEmpty()) return Lyrics(emptyList(), synced = false, wordSynced = false, source = source)
        entries.sortBy { it.first }

        var anyWords = false
        val lines = entries.mapIndexed { i, (start, text) ->
            val end = entries.getOrNull(i + 1)?.first?.takeIf { it > start } ?: (start + 6000)
            val words = parseWords(text, end)
            if (words.isNotEmpty()) anyWords = true
            LyricLine(
                startMs = start,
                endMs = end,
                text = wordTag.replace(text, "").replace(Regex("\\s+"), " ").trim(),
                words = words,
                isBackground = text.trim().startsWith("(") && text.trim().endsWith(")")
            )
        }.filter { it.text.isNotEmpty() }

        return Lyrics(lines, synced = true, wordSynced = anyWords, source = source)
    }

    /** Build plain lyrics with an even, polite timing so the pane still scrolls. */
    fun plain(text: String, source: String, msPerLine: Long = 4200): Lyrics {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Lyrics(emptyList(), synced = false, wordSynced = false, source = source)
        return Lyrics(
            lines = lines.mapIndexed { i, t ->
                val start = i * msPerLine + 1500
                LyricLine(start, start + msPerLine, t)
            },
            synced = false,
            wordSynced = false,
            source = source
        )
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

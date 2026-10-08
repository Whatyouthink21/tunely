package com.tunely.app.data

/**
 * Provider artwork arrives in wildly inconsistent quality: YouTube hands out
 * 480 px thumbnails, Deezer caps its "xl" covers at 500 px, iTunes returns
 * 100 px stubs. Every CDN involved here serves much bigger files from the same
 * path with the size segment rewritten, so we upgrade URLs once at parse time
 * and keep a step-down ladder for the rare 404s.
 */
object ArtworkUrls {

    private val YT_THUMB = Regex("""(i\.ytimg\.com/vi[^?]*/)(default|mqdefault|hqdefault|sddefault|hq720)(\.jpg)""")
    private val MZSTATIC_NEW = Regex("""/(\d+)x(\d+)bb\.(jpg|png|webp)""")
    private val MZSTATIC_OLD = Regex("""\.(\d+)x(\d+)-(\d+)\.(jpg|png)""")
    private val DEEZER = Regex("""(cdn-images\.dzcdn\.net/images/(?:cover|artist)/[a-z0-9]+/)(\d+)x(\d+)-""")
    private val SOUNDCLOUD_LARGE = Regex("""-large\.jpg""")
    private val SOUNDCLOUD_TINY = Regex("""-(badge|small|tiny|t67x67|t120x120|t300x300)\.jpg""")
    private val AUDIUS_SIZE = Regex("""/(\d+)x(\d+)\.(jpg|png)""")

    /** Biggest rendition every one of these CDNs is known to serve. */
    fun upgrade(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var out = url.trim()
        out = YT_THUMB.replace(out) { "${it.groupValues[1]}maxresdefault${it.groupValues[3]}" }
        out = MZSTATIC_NEW.replace(out) { "/1200x1200bb.${it.groupValues[3]}" }
        out = MZSTATIC_OLD.replace(out) { ".1200x1200-${it.groupValues[3]}.${it.groupValues[4]}" }
        out = DEEZER.replace(out) { "${it.groupValues[1]}1000x1000-" }
        out = SOUNDCLOUD_TINY.replace(out) { "-t500x500.jpg" }
        out = SOUNDCLOUD_LARGE.replace(out) { "-t500x500.jpg" }
        // Audius content nodes publish 150/480/1000 renditions from one CID.
        out = AUDIUS_SIZE.replace(out) { m ->
            val w = m.groupValues[1].toIntOrNull() ?: 0
            val h = m.groupValues[2].toIntOrNull() ?: 0
            if (maxOf(w, h) in 33..600) "/1000x1000.${m.groupValues[3]}" else m.value
        }
        return out
    }

    /**
     * Next-smaller rendition after an upgraded URL 404s — e.g. a video without
     * a maxres thumbnail falls back to the ever-present hqdefault.
     */
    fun degrade(url: String?): String? {
        if (url.isNullOrBlank()) return url
        var out = url
        out = out.replace("maxresdefault.jpg", "hqdefault.jpg")
        out = out.replace("/1200x1200bb.", "/600x600bb.")
        out = out.replace(".1200x1200-", ".600x600-")
        out = out.replace("/1000x1000-", "/500x500-")
        out = out.replace("/1000x1000.jpg", "/480x480.jpg")
        out = out.replace("-t500x500.jpg", "-t300x300.jpg")
        return out
    }

    /** Ladder of fallbacks: the original URL is always the last resort. */
    fun fallbacks(url: String?): List<String> {
        val upgraded = upgrade(url) ?: return emptyList()
        val steps = mutableListOf(upgraded)
        var step = degrade(upgraded)
        while (step != null && step !in steps) {
            steps += step
            val next = degrade(step)
            if (next == step) break
            step = next
        }
        url?.let { if (it !in steps) steps += it }
        return steps
    }
}

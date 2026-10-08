package com.tunely.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Small helper so every provider speaks the same (polite) HTTP dialect. */
object UserAgents {
    const val NEWPIPE =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    const val TUNELY = "Tunely/1.0 (Android; +https://github.com/Whatyouthink21/tunely)"
}

suspend fun OkHttpClient.getBody(
    url: String,
    userAgent: String = UserAgents.TUNELY,
    headers: Map<String, String> = emptyMap()
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val builder = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
        headers.forEach { (k, v) -> builder.header(k, v) }
        newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }.getOrNull()
}

fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Round-robin merge so every provider gets visibility at the top of the results. */
fun interleave(lists: List<List<Track>>, limit: Int = 60): List<Track> {
    val out = mutableListOf<Track>()
    val seen = HashSet<String>()
    val max = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until max) {
        for (list in lists) {
            val t = list.getOrNull(i) ?: continue
            val key = "${t.title.lowercase()}|${t.artist.lowercase()}"
            if (seen.add(key) || seen.add(t.uid)) out += t
            if (out.size >= limit) return out
        }
    }
    return out
}

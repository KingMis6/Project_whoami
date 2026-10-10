package com.example.whoami.data.repository

import com.example.whoami.util.GuessMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Best-effort picture lookup for a character name using Wikipedia's free
 * public API (no API key, no billing).
 *
 * We run a search (so lowercase / casual spelling still works), then only
 * accept a result whose page title genuinely matches what the player typed.
 * That guard matters in a guessing game: no picture is better than a wrong one.
 *
 * Never throws: on any failure returns null and the UI shows a placeholder.
 */
class ImageLookupService {

    suspend fun findThumbnailUrl(characterName: String): String? = withContext(Dispatchers.IO) {
        val query = characterName.trim()
        if (query.isEmpty()) return@withContext null
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                    "&generator=search&gsrsearch=$encoded&gsrlimit=5" +
                    "&prop=pageimages&piprop=thumbnail&pithumbsize=400&redirects=1"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                // Wikimedia asks apps to send a descriptive User-Agent.
                // TODO: put your own contact email here before publishing.
                setRequestProperty("User-Agent", "WhoAmIGame/1.0 (Android party game; contact: you@example.com)")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                pickBestThumbnail(query, body)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun pickBestThumbnail(query: String, body: String): String? {
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null

        val candidates = mutableListOf<JSONObject>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            pages.optJSONObject(keys.next())?.let { candidates.add(it) }
        }
        // "index" is the search rank (1 = best match)
        candidates.sortBy { it.optInt("index", Int.MAX_VALUE) }

        for (page in candidates) {
            val title = page.optString("title").substringBefore(" (") // "Naruto (TV series)" -> "Naruto"
            val thumb = page.optJSONObject("thumbnail")?.optString("source")
            if (!thumb.isNullOrBlank() && GuessMatcher.isMatch(query, title)) return thumb
        }
        return null
    }
}
